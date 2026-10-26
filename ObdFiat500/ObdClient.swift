import Foundation
import Network

// Liaison avec l'adaptateur ELM327 en Wi-Fi (même logique que la version Android).
// L'iPhone doit être connecté au Wi-Fi "WiFi_OBDII" dans les réglages Wi-Fi.
// Toutes les fonctions "bloquantes" doivent être appelées hors du thread principal.
final class ObdClient {
    private let verrou = NSLock()     // une seule séquence de commandes à la fois
    private let tampon = NSLock()     // données reçues du Wi-Fi
    private var connexion: NWConnection?
    private var recu = ""
    private var baro = 101.3
    private var groupe = 0

    private(set) var connecte = false
    private(set) var statut = "Non connecté"

    // Ouvre la liaison TCP 192.168.0.10:35000 sur le Wi-Fi, puis initialise l'ELM327
    func connecter() -> Bool {
        verrou.lock()
        defer { verrou.unlock() }
        deconnecterSansVerrou()
        statut = "Connexion à l'ELM327..."

        let params = NWParameters.tcp
        params.requiredInterfaceType = .wifi
        let c = NWConnection(
            host: NWEndpoint.Host("192.168.0.10"),
            port: NWEndpoint.Port(rawValue: 35000)!,
            using: params
        )

        let pret = DispatchSemaphore(value: 0)
        var ok = false
        c.stateUpdateHandler = { etat in
            switch etat {
            case .ready:
                ok = true
                pret.signal()
            case .failed, .cancelled:
                pret.signal()
            default:
                break
            }
        }
        c.start(queue: .global())
        _ = pret.wait(timeout: .now() + 8)
        guard ok else {
            c.cancel()
            statut = "ELM327 injoignable"
            return false
        }
        connexion = c
        lireEnContinu(c)

        let reponse = envoyer("ATZ", delai: 2.5)
        if !reponse.contains("ELM") {
            deconnecterSansVerrou()
            statut = "ELM327 muet"
            return false
        }
        _ = envoyer("ATE0")
        _ = envoyer("ATL0")
        _ = envoyer("ATS0")
        _ = envoyer("ATSP0")
        _ = envoyer("0100", delai: 8)   // déclenche la recherche du protocole
        connecte = true
        statut = "OBD connecté"
        return true
    }

    func deconnecter() {
        verrou.lock()
        deconnecterSansVerrou()
        verrou.unlock()
    }

    private func deconnecterSansVerrou() {
        connecte = false
        connexion?.cancel()
        connexion = nil
        statut = "Non connecté"
    }

    // Lecture permanente des octets reçus dans le tampon
    private func lireEnContinu(_ c: NWConnection) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 512) { [weak self] donnees, _, fini, erreur in
            if let d = donnees, let s = String(data: d, encoding: .ascii) {
                self?.tampon.lock()
                self?.recu += s
                self?.tampon.unlock()
            }
            if erreur == nil && !fini {
                self?.lireEnContinu(c)
            }
        }
    }

    // Envoie une commande et attend la réponse jusqu'au caractère ">"
    private func envoyer(_ cmd: String, delai: TimeInterval = 0.8) -> String {
        guard let c = connexion else { return "" }
        tampon.lock()
        recu = ""
        tampon.unlock()
        c.send(content: (cmd + "\r").data(using: .ascii), completion: .contentProcessed { _ in })

        let fin = Date().addingTimeInterval(delai)
        while Date() < fin {
            tampon.lock()
            let fini = recu.contains(">")
            tampon.unlock()
            if fini { break }
            Thread.sleep(forTimeInterval: 0.005)
        }
        tampon.lock()
        let r = recu
        tampon.unlock()
        return r.replacingOccurrences(of: ">", with: "")
    }

    private func nettoyer(_ s: String) -> String {
        let hex = "0123456789ABCDEF"
        return String(s.uppercased().filter { hex.contains($0) })
    }

    // Octets de données d'un PID : "410C1AF8" -> [0x1A, 0xF8]
    private func octets(_ pid: String, _ n: Int) -> [Int]? {
        let propre = nettoyer(envoyer("01" + pid))
        guard let r = propre.range(of: "41" + pid) else { return nil }
        let debut = propre.distance(from: propre.startIndex, to: r.upperBound)
        let chars = Array(propre)
        guard chars.count >= debut + 2 * n else { return nil }
        return (0..<n).map { i in
            Int(String(chars[(debut + 2 * i)..<(debut + 2 * i + 2)]), radix: 16) ?? 0
        }
    }

    // Une passe de lecture : régime à chaque passe, les autres capteurs à tour de rôle
    func cycle(precedent: Mesures) -> Mesures? {
        verrou.lock()
        defer { verrou.unlock() }
        guard connecte else { return nil }
        var m = precedent

        if let o = octets("0C", 2) {
            m.rpm = Double(o[0] * 256 + o[1]) / 4
        } else {
            m.rpm = nil
        }

        groupe = (groupe + 1) % 4
        switch groupe {
        case 0:
            if let b = octets("33", 1) { baro = Double(b[0]) }
            let pression = octets("0B", 1)?[0]
            m.boost = pression.map { (Double($0) - baro) / 100 }
            m.vitesse = octets("0D", 1).map { Double($0[0]) }
        case 1:
            m.eau = octets("05", 1).map { Double($0[0] - 40) }
            m.air = octets("0F", 1).map { Double($0[0] - 40) }
        case 2:
            m.batt = octets("42", 2).map { Double($0[0] * 256 + $0[1]) / 1000 }
            m.papillon = octets("11", 1).map { Double($0[0]) * 100 / 255 }
        default:
            m.avance = octets("0E", 1).map { Double($0[0]) / 2 - 64 }
            m.essence = octets("0A", 1).map { Double($0[0]) * 3 }
        }
        return m
    }

    // Lecture des codes défaut (mode 03)
    func lireDtc() -> [String] {
        verrou.lock()
        defer { verrou.unlock() }
        let rep = nettoyer(envoyer("03", delai: 3))
        guard let r = rep.range(of: "43") else { return [] }
        let chars = Array(rep)
        var p = rep.distance(from: rep.startIndex, to: r.upperBound)
        let lettres = Array("PCBU")
        var codes: [String] = []
        while p + 4 <= chars.count && codes.count < 12 {
            let b1 = Int(String(chars[p..<(p + 2)]), radix: 16) ?? 0
            let b2 = Int(String(chars[(p + 2)..<(p + 4)]), radix: 16) ?? 0
            if !(b1 == 0 && b2 == 0) {
                let premier = String(lettres[(b1 >> 6) & 3]) + String((b1 >> 4) & 3)
                codes.append(premier + String(format: "%X%X%X", b1 & 0xF, b2 >> 4, b2 & 0xF))
            }
            p += 4
        }
        return codes
    }

    // Effacement des codes défaut (mode 04) : n'appeler qu'après confirmation
    func effacerDtc() -> Bool {
        verrou.lock()
        defer { verrou.unlock() }
        let rep = nettoyer(envoyer("04", delai: 3))
        return rep.contains("44")
    }
}

// Mesures reçues de la voiture. nil = pas de réponse.
struct Mesures {
    var rpm: Double? = nil
    var vitesse: Double? = nil
    var eau: Double? = nil
    var air: Double? = nil
    var batt: Double? = nil
    var papillon: Double? = nil
    var avance: Double? = nil
    var essence: Double? = nil
    var boost: Double? = nil
}
