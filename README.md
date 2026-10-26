# Tableau de bord Fiat 500 Abarth — appli gratuite sur prise OBD

Une application gratuite qui affiche sur votre téléphone les mesures de votre voiture,
en la branchant sur la prise OBD (la prise de diagnostic sous le volant).

## Ce qu'elle fait

- **Tableau de bord** : régime moteur, vitesse, eau, batterie, papillon.
- **Pression turbo** : cadran et historique des 20 dernières secondes, avec alerte au-dessus d'un seuil réglable.
- **Moteur** : avance à l'allumage, pression d'essence, température d'air d'admission.
- **Pannes** : lecture des codes défaut, effacement après confirmation.
- **Performances** : pics de vitesse, de pression et de régime, chronomètre de session.
- **Journal** : historique des événements (seuils franchis, connexions, lectures de pannes).
- **Réglages** : unités (km/h ou mph), seuils d'alerte, mode Sport.
- **Jeux** : serpent et ping-pong, disponibles uniquement à l'arrêt.

## Comment ça marche

1. Branchez un adaptateur **ELM327 Wi-Fi** dans la prise OBD de la voiture, contact mis.
2. Connectez le téléphone au Wi-Fi de l'adaptateur (`WiFi_OBDII`).
3. Ouvrez l'appli et touchez **Connecter**.

L'appli n'a pas besoin d'internet : elle parle directement à l'adaptateur sur le Wi-Fi local.
Sans voiture, le **mode démonstration** affiche des valeurs simulées pour tester l'écran.

## Matériel

- Un adaptateur ELM327 Wi-Fi (adresse 192.168.0.10, port 35000 par défaut).
- Un téléphone Android (dossier `android/`) ou un iPhone (dossier racine, projet SwiftUI).

## Télécharger l'appli

**Android** (fichier APK, sans passer par le Play Store) :
https://github.com/artresinedeco-pixel/test-applie/raw/main/android/apk/Fiat500Abarth-etape2-debug.apk

Ouvrez ce lien sur le téléphone, téléchargez le fichier puis installez-le. Le téléphone peut demander
d'autoriser l'installation depuis le navigateur (Paramètres → Sécurité → Installer des applis inconnues).

**iPhone** : pas encore disponible en téléchargement direct. L'installation sur iPhone demande un compte
développeur Apple ; le code est compilable sur GitHub (voir plus bas).

## Gratuit et ouvert

L'appli est gratuite : aucun abonnement, aucune publicité, aucune donnée envoyée à l'extérieur.
Le code est publié tel quel, pour que chacun puisse le lire, le modifier et l'adapter.

## Avertissement

Ce projet est un outil personnel. Il n'est pas un produit certifié ni un appareil de sécurité.
Ne consultez pas l'écran en conduisant, et ne vous fiez pas aux valeurs pour décider d'une
réparation : utilisez un diagnostic professionnel pour tout problème. Les fonctions de
lecture et d'effacement des pannes modifient la mémoire de la voiture : utilisez-les en connaissance de cause.

## Compilation

- **Android** : ouvrir le dossier `android/` dans Android Studio, puis Build > Build APK(s).
- **iPhone** : la compilation se fait automatiquement sur GitHub (workflow « Compilation iOS »).
  Voir `LISEZMOI-iOS.txt` pour l'installation sur un Mac avec Xcode.

## État du projet

Version de test. Les écrans et la connexion sont en cours de vérification sur une vraie voiture.
