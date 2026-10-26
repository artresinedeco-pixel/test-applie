import SwiftUI

let ROUGE = Color(red: 230 / 255, green: 58 / 255, blue: 46 / 255)
let BLEU = Color(red: 59 / 255, green: 155 / 255, blue: 255 / 255)
let VERT = Color(red: 61 / 255, green: 220 / 255, blue: 132 / 255)
let JAUNE = Color(red: 255 / 255, green: 197 / 255, blue: 61 / 255)
let ORANGE = Color(red: 1, green: 140 / 255, blue: 40 / 255)
let GRIS = Color(red: 90 / 255, green: 99 / 255, blue: 112 / 255)
let GRIS_CLAIR = Color(red: 142 / 255, green: 142 / 255, blue: 150 / 255)
let GRIS_FOND = Color(red: 31 / 255, green: 36 / 255, blue: 44 / 255)
let CARTE = Color(red: 20 / 255, green: 24 / 255, blue: 30 / 255)
let BLANC = Color(red: 242 / 255, green: 244 / 255, blue: 247 / 255)

// Arc de jauge : 270 degrés à partir du bas à gauche
struct Arc: Shape {
    var fraction: Double

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let f = min(max(fraction, 0), 1)
        p.addArc(
            center: CGPoint(x: rect.midX, y: rect.midY),
            radius: min(rect.width, rect.height) / 2,
            startAngle: .degrees(135),
            endAngle: .degrees(135 + 270 * f),
            clockwise: false
        )
        return p
    }
}

func formater(_ v: Double?, _ decimales: Int = 0, _ suffixe: String = "") -> String {
    guard let v = v else { return "--" }
    return String(format: "%.\(decimales)f", v) + suffixe
}

// Carte sombre à bordure (tirets ou pleine)
struct Carte<Contenu: View>: View {
    let bord: Color
    let tirets: Bool
    let contenu: Contenu

    init(bord: Color = GRIS, tirets: Bool = false, @ViewBuilder contenu: () -> Contenu) {
        self.bord = bord
        self.tirets = tirets
        self.contenu = contenu()
    }

    var body: some View {
        contenu
            .padding(12)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: 14).fill(CARTE))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(bord, style: StrokeStyle(lineWidth: 2, dash: tirets ? [8, 6] : []))
            )
    }
}

// Bouton de la maquette : bordure rouge ou pleine couleur
struct BoutonStyle: ButtonStyle {
    var couleur: Color = ROUGE
    var plein = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 22, weight: .heavy))
            .foregroundColor(plein ? .black : couleur)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: 14).fill(plein ? couleur : CARTE))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(couleur, lineWidth: 2))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

// En-tête de page avec bouton « ‹ Menu »
struct Entete: View {
    @EnvironmentObject var m: AppModel
    let titre: String
    var retour: Bool = true

    var body: some View {
        HStack {
            Text(titre)
                .font(.system(size: 26, weight: .heavy))
                .foregroundColor(ROUGE)
            Spacer()
            if retour {
                Button("‹ Menu") { m.page = .menu }
                    .buttonStyle(BoutonStyle())
                    .frame(width: 160, height: 40)
            }
        }
        .frame(height: 48)
    }
}

// Racine : affiche la page courante dans un cadre 800 x 480, mis à l'échelle de l'écran
struct RacineView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width / 800, geo.size.height / 480)
            ZStack {
                Color.black
                contenuPage
            }
            .frame(width: 800, height: 480)
            .scaleEffect(s)
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .background(Color.black)
        .ignoresSafeArea()
    }

    @ViewBuilder
    private var contenuPage: some View {
        switch m.page {
        case .veille: VeillePage()
        case .menu: MenuPage()
        case .dash: DashPage()
        case .turbo: TurboPage()
        case .moteur: MoteurPage()
        case .log: LogPage()
        case .reglages: ReglagesPage()
        case .obd: ObdPage()
        case .pannes: PannesPage()
        case .perf: PerfPage()
        case .jeux: JeuxPage()
        case .serpent: SerpentPage()
        case .pong: PongPage()
        }
    }
}

struct VeillePage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { contexte in
            VStack(spacing: 10) {
                Text(contexte.date, format: .dateTime.hour().minute())
                    .font(.system(size: 120, weight: .heavy))
                    .monospacedDigit()
                    .foregroundColor(BLANC)
                Text("FIAT 500 ABARTH")
                    .font(.system(size: 40, weight: .heavy))
                    .foregroundColor(ROUGE)
                Text("Bonjour Wylliam")
                    .font(.system(size: 28))
                    .foregroundColor(BLANC)
                Text("Touchez l'écran pour revenir au menu")
                    .font(.system(size: 20))
                    .foregroundColor(GRIS)
            }
        }
        .frame(width: 800, height: 480)
        .background(Color.black)
        .contentShape(Rectangle())
        .onTapGesture { m.page = .menu }
    }
}

struct MenuPage: View {
    @EnvironmentObject var m: AppModel

    let tuiles: [(String, String, Page)] = [
        ("Tableau de bord", "Régime, vitesse, relevés", .dash),
        ("Pression turbo", "Cadran de -1 à +2 bar", .turbo),
        ("Pannes", "Recherche de pannes", .pannes),
        ("Jeux", "Serpent et ping-pong, à l'arrêt", .jeux),
        ("Moteur", "Avance, essence, admission", .moteur),
        ("Performances", "Pics de vitesse, pression", .perf),
        ("Log", "Historique des sorties", .log),
        ("Réglages", "Unités, seuils, affichage", .reglages)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Entete(titre: "MENU", retour: false)
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(176), spacing: 20), count: 4), spacing: 20) {
                ForEach(0..<tuiles.count, id: \.self) { i in
                    Button {
                        m.page = tuiles[i].2
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(tuiles[i].0)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(BLANC)
                            Text(tuiles[i].1)
                                .font(.system(size: 16))
                                .foregroundColor(GRIS_CLAIR)
                        }
                        .padding(14)
                        .frame(width: 176, height: 180, alignment: .topLeading)
                        .background(RoundedRectangle(cornerRadius: 16).fill(CARTE))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(ROUGE, style: StrokeStyle(lineWidth: 2, dash: [8, 6])))
                    }
                    .buttonStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct PetiteCarte: View {
    let titre: String
    let valeur: String
    var couleur: Color = BLANC

    var body: some View {
        Carte {
            VStack(spacing: 6) {
                Text(titre)
                    .font(.system(size: 16))
                    .foregroundColor(GRIS)
                Text(valeur)
                    .font(.system(size: 30, weight: .heavy))
                    .monospacedDigit()
                    .foregroundColor(couleur)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

struct DashPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        let rpm = m.mesures.rpm ?? 0
        let vitesse = m.mesures.vitesse.map { m.unitesMph ? $0 * 0.621371 : $0 }
        VStack(alignment: .leading, spacing: 8) {
            Entete(titre: m.modeSport ? "TABLEAU DE BORD · SPORT" : "TABLEAU DE BORD")
            HStack(spacing: 20) {
                ZStack {
                    Arc(fraction: 1).stroke(GRIS_FOND, style: StrokeStyle(lineWidth: 18, lineCap: .round))
                    Arc(fraction: rpm / 8000).stroke(BLEU, style: StrokeStyle(lineWidth: 18, lineCap: .round))
                    VStack(spacing: 2) {
                        Text("RPM x1000").font(.system(size: 14)).foregroundColor(GRIS)
                        Text(formater(m.mesures.rpm.map { $0 / 1000 }, 1))
                            .font(.system(size: 40, weight: .heavy)).monospacedDigit()
                            .foregroundColor(BLANC)
                    }
                }
                .frame(width: 230, height: 230)

                VStack {
                    Text(vitesse.map { String(Int($0)) } ?? "--")
                        .font(.system(size: 120, weight: .heavy))
                        .monospacedDigit()
                        .foregroundColor(BLANC)
                    Text(m.unitesMph ? "mph" : "km/h")
                        .font(.system(size: 26))
                        .foregroundColor(GRIS)
                }
                .frame(maxWidth: .infinity)
            }
            .frame(height: 260)

            HStack(spacing: 14) {
                PetiteCarte(titre: "EAU", valeur: formater(m.mesures.eau, 0, " °C"))
                PetiteCarte(titre: "PAPILLON", valeur: formater(m.mesures.papillon, 0, " %"), couleur: BLEU)
                PetiteCarte(titre: "TURBO", valeur: formater(m.mesures.boost, 2, " bar"),
                            couleur: (m.mesures.boost ?? 0) > m.seuilTurbo ? ROUGE : BLANC)
                PetiteCarte(titre: "BATTERIE", valeur: formater(m.mesures.batt, 1, " V"), couleur: BLEU)
            }
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct TurboPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        let b = m.mesures.boost ?? 0
        VStack(alignment: .leading, spacing: 8) {
            Entete(titre: "PRESSION TURBO")
            HStack(spacing: 30) {
                ZStack {
                    Arc(fraction: 1).stroke(GRIS_FOND, style: StrokeStyle(lineWidth: 18, lineCap: .round))
                    Arc(fraction: (b + 1) / 3).stroke(b > m.seuilTurbo ? ROUGE : BLEU, style: StrokeStyle(lineWidth: 18, lineCap: .round))
                    VStack {
                        Text(formater(m.mesures.boost, 2))
                            .font(.system(size: 64, weight: .heavy)).monospacedDigit()
                            .foregroundColor(BLANC)
                        Text("bar").font(.system(size: 22)).foregroundColor(GRIS)
                    }
                }
                .frame(width: 280, height: 280)

                VStack(alignment: .leading, spacing: 12) {
                    Text("Papillon").font(.system(size: 22)).foregroundColor(GRIS)
                    Text(formater(m.mesures.papillon, 0, " %"))
                        .font(.system(size: 56, weight: .heavy)).foregroundColor(BLANC)
                    Text("Seuil d'alerte : \(formater(m.seuilTurbo, 1)) bar")
                        .font(.system(size: 18)).foregroundColor(GRIS_CLAIR)
                }
            }
            .frame(height: 290)

            Text("PRESSION · 20 DERNIÈRES SECONDES")
                .font(.system(size: 16)).foregroundColor(GRIS)
            Canvas { contexte, taille in
                let pas = taille.width / 20
                for (i, v) in m.histo.enumerated() {
                    guard let v = v else { continue }
                    let hauteur = min(max(v / 2.5 * 90, -45), 90)
                    let rect = CGRect(x: CGFloat(i) * pas + 2, y: taille.height - CGFloat(hauteur) - 4,
                                      width: pas - 4, height: CGFloat(hauteur))
                    contexte.fill(Path(rect), with: .color(v > m.seuilTurbo ? ROUGE : BLEU))
                }
            }
            .frame(height: 100)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct MoteurPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        let v = m.mesures
        VStack(alignment: .leading, spacing: 8) {
            Entete(titre: "MOTEUR")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 14), count: 3), spacing: 14) {
                PetiteCarte(titre: "AVANCE", valeur: formater(v.avance, 1, " °"))
                PetiteCarte(titre: "PRESSION ESSENCE", valeur: formater(v.essence, 0, " kPa"))
                PetiteCarte(titre: "AIR ADMISSION", valeur: formater(v.air, 0, " °C"))
                PetiteCarte(titre: "EAU", valeur: formater(v.eau, 0, " °C"))
                PetiteCarte(titre: "BATTERIE", valeur: formater(v.batt, 1, " V"), couleur: BLEU)
                PetiteCarte(titre: "PAPILLON", valeur: formater(v.papillon, 0, " %"), couleur: BLEU)
            }
            .frame(maxHeight: .infinity)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct LogPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Entete(titre: "JOURNAL DES SORTIES")
            if m.log.isEmpty {
                Spacer()
                Text("Aucun événement pour l'instant")
                    .font(.system(size: 26)).foregroundColor(GRIS)
                    .frame(maxWidth: .infinity)
                Spacer()
            } else {
                ForEach(Array(m.log.prefix(14).enumerated()), id: \.offset) { _, ligne in
                    Text(ligne).font(.system(size: 19)).foregroundColor(BLANC)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct ReglagesPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Entete(titre: "RÉGLAGES")
            ReglageLigne(nom: "Unité de vitesse", valeur: m.unitesMph ? "mph" : "km/h") { m.unitesMph.toggle() }
            ReglageLigne(nom: "Alerte turbo au-dessus de", valeur: "\(formater(m.seuilTurbo, 1)) bar") {
                m.seuilTurbo = [0.8, 1.0, 1.1, 1.3].cycle(after: m.seuilTurbo)
            }
            ReglageLigne(nom: "Alerte régime au-dessus de", valeur: "\(Int(m.seuilRpm)) tr/min") {
                m.seuilRpm = [5000.0, 5500, 6000, 6500].cycle(after: m.seuilRpm)
            }
            ReglageLigne(nom: "Mode Sport", valeur: m.modeSport ? "ACTIVÉ" : "désactivé") { m.modeSport.toggle() }
            ReglageLigne(nom: "Alertes à l'écran", valeur: m.alertesEcran ? "ACTIVÉES" : "désactivées") { m.alertesEcran.toggle() }
            ReglageLigne(nom: "Connexion OBD", valeur: m.connecte ? "connecté" : "ouvrir") { m.page = .obd }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

extension Array where Element: Equatable {
    // Valeur suivante de la liste, puis retour au début
    func cycle(after v: Element) -> Element {
        guard let i = firstIndex(of: v) else { return self[0] }
        return self[(i + 1) % count]
    }
}

struct ReglageLigne: View {
    let nom: String
    let valeur: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(nom).font(.system(size: 22)).foregroundColor(BLANC)
                Spacer()
                Text(valeur).font(.system(size: 22, weight: .heavy)).foregroundColor(ROUGE)
            }
            .padding(.horizontal, 16)
            .frame(height: 58)
            .background(RoundedRectangle(cornerRadius: 14).fill(CARTE))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(GRIS, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }
}

struct ObdPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Entete(titre: "CONNEXION OBD")
            Text(m.demo ? "Démonstration : valeurs simulées" : m.statutObd)
                .font(.system(size: 30, weight: .heavy))
                .foregroundColor(m.demo ? JAUNE : (m.connecte ? VERT : ROUGE))
            Text("Prise OBD2 · broches 6 et 14 · CAN 500 kbit/s")
                .font(.system(size: 20)).foregroundColor(GRIS)
            Text("Connectez l'iPhone au Wi-Fi WiFi_OBDII avant de cliquer")
                .font(.system(size: 20)).foregroundColor(GRIS)
            Spacer()
            HStack(spacing: 20) {
                Button("CONNECTER") { m.connecterObd() }
                    .buttonStyle(BoutonStyle(couleur: VERT))
                Button(m.demo ? "ARRÊTER DÉMO" : "DÉMONSTRATION") { m.demo.toggle() }
                    .buttonStyle(BoutonStyle(couleur: JAUNE))
            }
            .frame(height: 90)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct PannesPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Entete(titre: "RECHERCHE DE PANNES")
            Text(m.msgDtc).font(.system(size: 26)).foregroundColor(BLANC)
            HStack(alignment: .top, spacing: 40) {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(m.dtc, id: \.self) { code in
                        Text(code).font(.system(size: 34, weight: .heavy)).foregroundColor(ROUGE)
                    }
                }
                Spacer()
            }
            .frame(maxHeight: .infinity, alignment: .top)
            HStack(spacing: 20) {
                Button("LIRE") { m.lireDtc() }
                    .buttonStyle(BoutonStyle(couleur: VERT))
                Button(m.effacerArme ? "CONFIRMER ?" : "EFFACER") {
                    if m.effacerArme {
                        m.effacerArme = false
                        m.effacerDtc()
                    } else {
                        m.effacerArme = true
                        m.msgDtc = "Touchez EFFACER encore pour confirmer"
                    }
                }
                .buttonStyle(BoutonStyle(couleur: m.effacerArme ? ROUGE : ORANGE))
            }
            .frame(height: 70)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct PerfPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        let vitesse = m.vitMax == 0 ? "--" : String(Int(m.unitesMph ? m.vitMax * 0.621371 : m.vitMax))
        VStack(alignment: .leading, spacing: 8) {
            Entete(titre: "PERFORMANCES")
            HStack(alignment: .top, spacing: 14) {
                VStack(spacing: 14) {
                    HStack(spacing: 14) {
                        PetiteCarte(titre: "VITESSE MAX · \(m.unitesMph ? "mph" : "km/h")", valeur: vitesse)
                        PetiteCarte(titre: "PRESSION MAX · bar", valeur: m.boostMax == 0 ? "--" : formater(m.boostMax, 2))
                    }
                    HStack(spacing: 14) {
                        PetiteCarte(titre: "RÉGIME MAX · tr/min", valeur: m.rpmMax == 0 ? "--" : String(Int(m.rpmMax)))
                        PetiteCarte(titre: "TEMPS DE SESSION", valeur: m.chronoTexte())
                    }
                }
                VStack(spacing: 14) {
                    Button(m.chronoDebut == nil ? "DÉPART" : "ARRÊT") {
                        if let d = m.chronoDebut {
                            m.chronoCumul += Date().timeIntervalSince(d)
                            m.chronoDebut = nil
                        } else {
                            m.chronoDebut = Date()
                        }
                    }
                    .buttonStyle(BoutonStyle(couleur: m.chronoDebut == nil ? VERT : ROUGE))
                    Button("Réinitialiser") {
                        m.vitMax = 0; m.boostMax = 0; m.rpmMax = 0
                        m.chronoCumul = 0; m.chronoDebut = nil
                    }
                    .buttonStyle(BoutonStyle())
                }
                .frame(width: 180)
            }
            .frame(maxHeight: .infinity)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct JeuxPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Entete(titre: "JEUX")
            Text(m.arret ? "Touchez un jeu" : "Jeux disponibles à l'arrêt uniquement")
                .font(.system(size: 22)).foregroundColor(m.arret ? GRIS : JAUNE)
                .frame(maxWidth: .infinity)
            HStack(spacing: 20) {
                Button("SERPENT") {
                    m.nouveauSerpent()
                    m.page = .serpent
                }
                .buttonStyle(BoutonStyle(couleur: VERT))
                Button("PING-PONG") {
                    m.pongRaz()
                    m.page = .pong
                }
                .buttonStyle(BoutonStyle(couleur: BLEU))
            }
            .frame(height: 160)
            .disabled(!m.arret)
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct SerpentPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Entete(titre: "SERPENT · score \(m.scoreSerpent)")
                Button("Rejouer") { m.nouveauSerpent() }
                    .buttonStyle(BoutonStyle(couleur: VERT))
                    .frame(width: 170, height: 40)
            }
            HStack(alignment: .top, spacing: 20) {
                ZStack {
                    Canvas { contexte, _ in
                        contexte.fill(Path(CGRect(x: 0, y: 0, width: 580, height: 400)), with: .color(CARTE))
                        let nour = CGRect(x: CGFloat(m.nourriture.x) * 20 + 4, y: CGFloat(m.nourriture.y) * 20 + 4, width: 12, height: 12)
                        contexte.fill(Path(ellipseIn: nour), with: .color(ROUGE))
                        for c in m.serpent {
                            let r = CGRect(x: CGFloat(c.x) * 20 + 2, y: CGFloat(c.y) * 20 + 2, width: 16, height: 16)
                            contexte.fill(Path(r), with: .color(VERT))
                        }
                    }
                    .frame(width: 580, height: 400)
                    if m.serpentPerdu {
                        Text("PERDU · touchez Rejouer")
                            .font(.system(size: 26, weight: .heavy)).foregroundColor(ROUGE)
                    }
                }
                .frame(width: 580, height: 400)

                VStack(spacing: 10) {
                    Button("▲") { m.tournerSerpent(0) }.buttonStyle(BoutonStyle(couleur: BLANC))
                    HStack(spacing: 10) {
                        Button("◀") { m.tournerSerpent(3) }.buttonStyle(BoutonStyle(couleur: BLANC))
                        Button("▶") { m.tournerSerpent(1) }.buttonStyle(BoutonStyle(couleur: BLANC))
                    }
                    Button("▼") { m.tournerSerpent(2) }.buttonStyle(BoutonStyle(couleur: BLANC))
                }
                .frame(width: 180, height: 400)
                .disabled(!m.arret)
            }
            .frame(maxHeight: .infinity)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}

struct PongPage: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Entete(titre: "PING-PONG · \(m.scorePong) / \(m.ratesPong)")
                Button("Rejouer") { m.pongRaz() }
                    .buttonStyle(BoutonStyle(couleur: VERT))
                    .frame(width: 170, height: 40)
            }
            ZStack {
                Canvas { contexte, _ in
                    contexte.fill(Path(CGRect(x: 0, y: 0, width: 580, height: 400)), with: .color(CARTE))
                    let balle = CGRect(x: CGFloat(m.balleX) * 580 - 9, y: CGFloat(m.balleY) * 400 - 9, width: 18, height: 18)
                    contexte.fill(Path(ellipseIn: balle), with: .color(JAUNE))
                    let rx = (m.raquette - 0.12) * 580
                    contexte.fill(Path(roundedRect: CGRect(x: rx, y: 380, width: 0.24 * 580, height: 12), cornerRadius: 6), with: .color(BLEU))
                }
                .frame(width: 580, height: 400)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { valeur in
                            if m.arret {
                                m.raquette = min(max(Double(valeur.location.x) / 580, 0.1), 0.9)
                            }
                        }
                )
                if !m.pongActif {
                    Text("Touchez Rejouer").font(.system(size: 22)).foregroundColor(GRIS)
                }
            }
            .frame(width: 580, height: 400)
            .frame(maxWidth: .infinity)
            Text("Touchez la zone pour placer la raquette")
                .font(.system(size: 16)).foregroundColor(GRIS)
                .frame(maxWidth: .infinity)
        }
        .padding(16)
        .frame(width: 800, height: 480, alignment: .topLeading)
        .background(Color.black)
    }
}
