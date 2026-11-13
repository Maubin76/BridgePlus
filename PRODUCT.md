# Product

<!-- impeccable:product-schema 1 -->

## Platform

android, desktop (Windows, Compose Desktop)

## Users
Un seul utilisateur, Mathieu, propriétaire et développeur de l'application. Un téléphone Android et un ou plusieurs PC Windows à la maison. Deux situations d'usage :
- **au PC** : récupérer une photo du téléphone, coller sur le PC un texte copié sur le téléphone (et inversement), voir les messages qui arrivent sans sortir le téléphone de la poche ;
- **au téléphone** : envoyer un lien ou un fichier au PC d'un geste, depuis le menu de partage ou la tuile de réglages rapides.

## Product Purpose
Relier le téléphone et le PC directement par le Wi-Fi de la maison pour partager le presse-papiers, envoyer des fichiers et afficher les notifications du téléphone sur le PC. C'est l'équivalent de KDE Connect ou LocalSend, dans l'esprit de la gamme. Le succès : une information passe d'un appareil à l'autre en un geste et en moins de deux secondes ; une fois l'appairage fait, on n'y pense plus.

## Positioning
Outil personnel de la gamme Plus (PatrimoinePlus, KeePlus, SportPlus, MemoPlus). Il reprend la couche réseau de KeePlus : découverte UDP, appairage par QR code, reconnaissance du réseau de la maison, messages scellés en AES-GCM. Le PC fait serveur et le téléphone tient une connexion permanente tant qu'il est à la maison.

## Operating Context
- Téléphone Android 16 (minSdk 36) : service de premier plan « connectedDevice », seulement sur le Wi-Fi de la maison, relancé au retour, après un redémarrage et après une mise à jour.
- PC Windows 10+ : l'appli reste dans la zone de notification et démarre avec Windows.
- Aucune requête internet, hormis l'éventuelle vérification des mises à jour sur GitHub, désactivable.

## Capabilities and Constraints
- Version 1 : appairage, presse-papiers dans les deux sens (texte, 1 Mo au plus, contenus sensibles exclus), historique chiffré, envoi de fichiers, notifications du téléphone sur le PC (liste blanche), sonnerie du téléphone, batterie, liens.
- Hors version 1 : réponse aux messages depuis le PC (prévue en v1.1), SMS et appels, images du presse-papiers, contrôle à distance, recopie d'écran, macOS et Linux, liaison hors de la maison.
- Android interdit la lecture du presse-papiers en arrière-plan : l'envoi du téléphone vers le PC passe par un geste (tuile, menu de partage, appli ouverte).
- Textes d'interface en français, vouvoiement.

## Evidence on Hand
Aucune donnée : l'application démarre sans appareil appairé. Les écrans ne montrent jamais de faux appareils ni de faux échanges présentés comme réels.

## Product Principles
1. **Un geste, deux secondes** : chaque échange part en une action, sans écran intermédiaire.
2. **Rien ne sort de la maison** : hors du réseau de la maison, PontPlus ne fait rien.
3. **Jamais un secret qui fuit** : ce qui est marqué sensible ne circule pas et ne s'enregistre pas.
4. **Invisible quand tout va bien** : la seule notification permanente dit « Relié à <PC> ».
5. **Sobre en batterie** : la consommation sur une journée reste négligeable, et elle est mesurée.

## Accessibility & Inclusion
Texte secondaire d'au moins 4.5:1 sur tous les fonds, dans les deux thèmes. Aucune information portée par la seule couleur : l'état de la liaison a toujours une icône et un texte, et l'icône de la zone de notification change de forme. Animations coupées si le système demande de les réduire. Actions en icône décrites pour les lecteurs d'écran.
