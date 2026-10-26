import Foundation
import SwiftUI

enum Page: Hashable {
    case veille, menu, dash, turbo, moteur, log, reglages, obd, pannes, perf, jeux, serpent, pong
}

struct Cellule: Equatable {
    var x: Int
    var y: Int
}

// État de l'appli et boucle de mise à jour (environ 5 fois par seconde).
final class AppModel: ObservableObject {
    @Published var page: Page = .veille
    @Published var demo = false
    @Published var mesures = Mesures()
    @Published var histo: [Double?] = Array(repeating: nil, count: 20)
    @Published var dtc: [String] = []
    @Published var msgDtc = "Touchez LIRE"
    @Published var effacerArme = false
    @Published var vitMax = 0.0
    @Published var boostMax = 0.0
    @Published var rpmMax = 0.0
    @Published var chronoDebut: Date? = nil
    @Published var chronoCumul: TimeInterval = 0

    // Réglages
    @Published var unitesMph = false
    @Published var seuilTurbo = 1.1
    @Published var seuilRpm = 6000.0
    @Published var modeSport = false
    @Published var alertesEcran = true

    // Connexion
    @Published var connecte = false
    @Published var statutObd = "Non connecté"

    // Journal (le plus récent en premier)
    @Published var log: [String] = []

    // Jeux
    @Published var serpent: [Cellule] = []
    @Published var direction = 1          // 0 haut, 1 droite, 2 bas, 3 gauche
    @Published var nourriture = Cellule(x: 0, y: 0)
    @Published var scoreSerpent = 0
    @Published var serpentPerdu = true

    @Published var raquette = 0.5
    @Published var balleX = 0.5
    @Published var balleY = 0.2
    @Published var vx = 0.03
    @Published var vy = 0.05
    @Published var scorePong = 0
    @Published var ratesPong = 0
    @Published var pongActif = false

    let obd = ObdClient()
    private var lectureEnCours = false
    private var etatTurbo = false
    private var etatRpm = false
    private var derniereHisto = Date.distantPast
    private var dernierSerpent = Date.distantPast
    private var minuterie: Timer?

    init() {
        minuterie = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
            self?.tick()
        }
    }

    var arret: Bool { !((mesures.vitesse ?? 0) > 2) }

    func ajouterLog(_ s: String) {
        let heure = Date().formatted(.dateTime.hour().minute().second())
        log.insert("\(heure)  \(s)", at: 0)
        if log.count > 50 { log.removeLast(log.count - 50) }
    }

    func tick() {
        let maintenant = Date()
        if demo {
            mesures = simuler(maintenant.timeIntervalSince1970)
        } else if connecte && !lectureEnCours {
            lectureEnCours = true
            let actuel = mesures
            DispatchQueue.global(qos: .userInitiated).async {
                let nouveau = self.obd.cycle(precedent: actuel)
                DispatchQueue.main.async {
                    if let n = nouveau { self.mesures = n }
                    self.lectureEnCours = false
                }
            }
        }

        let m = mesures
        if let v = m.vitesse { vitMax = max(vitMax, v) }
        if let b = m.boost { boostMax = max(boostMax, b) }
        if let r = m.rpm { rpmMax = max(rpmMax, r) }

        // Journal : une ligne à chaque franchissement de seuil
        if let b = m.boost {
            let haut = b > seuilTurbo
            if haut && !etatTurbo { ajouterLog(String(format: "Pression turbo %.2f bar", b)) }
            etatTurbo = haut
        }
        if let r = m.rpm {
            let haut = r > seuilRpm
            if haut && !etatRpm { ajouterLog("Régime \(Int(r)) tr/min") }
            etatRpm = haut
        }

        if maintenant.timeIntervalSince(derniereHisto) >= 1 {
            derniereHisto = maintenant
            histo.removeFirst()
            histo.append(m.boost)
        }

        // Jeux : seulement à l'arrêt
        if arret {
            if page == .serpent && maintenant.timeIntervalSince(dernierSerpent) >= 0.35 {
                dernierSerpent = maintenant
                avancerSerpent()
            }
            if page == .pong { pongPas() }
        }
    }

    // Simulation (même logique que la version Android)
    func simuler(_ t: Double) -> Mesures {
        var m = Mesures()
        m.rpm = 2000 + 4500 * (0.5 + 0.5 * sin(t * 0.7))
        m.vitesse = 20 + 110 * (0.5 + 0.5 * sin(t * 0.3))
        m.eau = 85 + 12 * sin(t * 0.2)
        m.air = 30 + 8 * sin(t * 0.5)
        m.batt = 13.8 + 0.6 * sin(t * 0.4)
        m.papillon = max(0, 50 + 50 * sin(t * 0.8))
        m.avance = 12 + 14 * sin(t * 0.6)
        m.essence = 380 + 60 * sin(t * 0.5)
        m.boost = 1.2 * sin(t * 0.9) + 0.4
        return m
    }

    // Chrono
    func chronoTexte() -> String {
        var s = chronoCumul
        if let d = chronoDebut { s += Date().timeIntervalSince(d) }
        let total = Int(s)
        return String(format: "%02d:%02d", total / 60, total % 60)
    }

    // Serpent
    private let colonnes = 29
    private let lignes = 20

    func nouveauSerpent() {
        serpent = [Cellule(x: 5, y: 10), Cellule(x: 4, y: 10), Cellule(x: 3, y: 10)]
        direction = 1
        scoreSerpent = 0
        serpentPerdu = false
        placerNourriture()
    }

    private func placerNourriture() {
        while true {
            let c = Cellule(x: Int.random(in: 0..<colonnes), y: Int.random(in: 0..<lignes))
            if !serpent.contains(c) {
                nourriture = c
                return
            }
        }
    }

    func tournerSerpent(_ d: Int) {
        if (d + 2) % 4 != direction { direction = d }   // pas de demi-tour direct
    }

    func avancerSerpent() {
        guard !serpentPerdu, let h = serpent.first else { return }
        let dx = direction == 1 ? 1 : (direction == 3 ? -1 : 0)
        let dy = direction == 2 ? 1 : (direction == 0 ? -1 : 0)
        let n = Cellule(x: h.x + dx, y: h.y + dy)
        if n.x < 0 || n.y < 0 || n.x >= colonnes || n.y >= lignes || serpent.contains(n) {
            serpentPerdu = true
            ajouterLog("Serpent terminé : score \(scoreSerpent)")
            return
        }
        serpent.insert(n, at: 0)
        if n == nourriture {
            scoreSerpent += 1
            placerNourriture()
        } else {
            serpent.removeLast()
        }
    }

    // Ping-pong
    func pongRaz() {
        raquette = 0.5; balleX = 0.5; balleY = 0.2
        vx = 0.03; vy = 0.05; scorePong = 0; ratesPong = 0; pongActif = true
    }

    func pongPas() {
        guard pongActif else { return }
        balleX += vx
        balleY += vy
        if balleX < 0 { balleX = 0; vx = -vx }
        if balleX > 1 { balleX = 1; vx = -vx }
        if balleY < 0 { balleY = 0; vy = -vy }
        if balleY >= 0.94 && vy > 0 {
            if abs(balleX - raquette) <= 0.12 {
                vy = -abs(vy)
                balleY = 0.94
                scorePong += 1
            } else if balleY >= 1 {
                ratesPong += 1
                balleX = 0.5
                balleY = 0.2
                vy = abs(vy)
            }
        }
    }

    // Appels réseau : hors du thread principal
    func connecterObd() {
        ajouterLog("Connexion OBD demandée")
        DispatchQueue.global(qos: .userInitiated).async {
            let ok = self.obd.connecter()
            DispatchQueue.main.async {
                self.connecte = ok
                self.statutObd = self.obd.statut
            }
        }
    }

    func lireDtc() {
        DispatchQueue.global(qos: .userInitiated).async {
            guard self.connecte else {
                DispatchQueue.main.async { self.msgDtc = "OBD non connecté" }
                return
            }
            let liste = self.obd.lireDtc()
            DispatchQueue.main.async {
                self.dtc = liste
                self.msgDtc = liste.isEmpty ? "Aucune panne enregistrée" : "\(liste.count) panne(s) trouvée(s)"
                self.ajouterLog("Lecture des pannes : \(liste.count)")
            }
        }
    }

    func effacerDtc() {
        DispatchQueue.global(qos: .userInitiated).async {
            guard self.connecte else {
                DispatchQueue.main.async { self.msgDtc = "OBD non connecté" }
                return
            }
            let ok = self.obd.effacerDtc()
            DispatchQueue.main.async {
                if ok {
                    self.dtc = []
                    self.msgDtc = "Pannes effacées (à relire pour vérifier)"
                    self.ajouterLog("Pannes effacées")
                } else {
                    self.msgDtc = "Effacement non confirmé par la voiture"
                }
            }
        }
    }
}
