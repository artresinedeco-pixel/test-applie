import SwiftUI

// Point d'entrée de l'appli Fiat 500 Abarth (iPhone).
// Paysage uniquement : voir LISEZMOI-iOS.txt pour le réglage dans Xcode.
@main
struct ObdFiat500App: App {
    @StateObject private var modele = AppModel()

    var body: some Scene {
        WindowGroup {
            RacineView()
                .environmentObject(modele)
                .preferredColorScheme(.dark)
        }
    }
}
