<p align="center">
  <img src="doc/logo/bridgeplus-logo.png" alt="Logo BridgePlus" width="112">
</p>

<h1 align="center">BridgePlus</h1>

<p align="center">
  Le téléphone et le PC, <b>reliés</b>.<br>
  Presse-papiers, fichiers et notifications passent par le Wi-Fi de la maison.<br>
  Pas de cloud, pas de compte, pas de câble.
</p>

<p align="center">
  <a href="https://github.com/Maubin76/BridgePlus/actions/workflows/ci.yml"><img src="https://github.com/Maubin76/BridgePlus/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/Maubin76/BridgePlus/releases/latest"><img src="https://img.shields.io/github/v/release/Maubin76/BridgePlus?label=version" alt="Dernière version"></a>
  <img src="https://img.shields.io/badge/Android-16%2B-3DDC84" alt="Android 16 et plus">
  <img src="https://img.shields.io/badge/Windows-10%2B-0078D4" alt="Windows 10 et plus">
  <img src="https://img.shields.io/badge/chiffrement-AES--256--GCM-555555" alt="Chiffrement AES-256-GCM">
</p>

<!-- Captures du téléphone et du PC : étape 6. -->

## En bref

BridgePlus fait passer une information du téléphone au PC, ou l'inverse, **en un geste et en moins de
deux secondes**.

- **Chez vous, et seulement chez vous** : le téléphone et le PC se parlent directement par le
  Wi-Fi de la maison. Hors de chez vous, BridgePlus se met en veille.
- **Appairé une fois, relié pour toujours** : on scanne un QR code, puis la liaison se rétablit
  toute seule dès que les deux appareils sont à la maison.
- **Chiffré de bout en bout** : chaque paire a sa propre clé, qui passe par l'appareil photo et
  jamais par le réseau. Voir [la cryptographie, en clair](doc/CRYPTOGRAPHIE.md).
- **Discret** : une icône près de l'horloge du PC, une notification minimale sur le téléphone,
  « Relié à Bureau ».

## Où en est-on

| Étape | Contenu | État |
|---|---|---|
| 1 | Modules, thème, liaison chiffrée, appairage par QR code, connexion permanente avec reconnexion | ✅ |
| 2 | Presse-papiers dans les deux sens, contenus sensibles exclus | ✅ (branche `develop`) |
| 3 | Historique chiffré, carte du PC (Ctrl+Alt+V) | ✅ (branche `develop`) |
| 4 | Envoi de fichiers dans les deux sens | ✅ (branche `develop`) |
| 5 | Notifications du téléphone sur le PC | à venir |
| 6 | Sonnerie, batterie, liens, assistant de premier lancement | à venir |

## Le presse-papiers

- **Du PC vers le téléphone** : chaque texte copié sur le PC arrive dans le presse-papiers du
  téléphone. Si Android refuse, une notification « Texte reçu » propose **Copier**.
- **Du téléphone vers le PC** : Android interdit à une appli en arrière-plan de lire le
  presse-papiers. Trois gestes : la **tuile de réglages rapides** « Envoyer au PC », l'entrée
  **« Envoyer au PC » du menu de partage**, ou simplement **ouvrir BridgePlus** (la copie part
  d'elle-même).
- **Jamais un secret** : ce que KeePlus, KeePass ou un autre gestionnaire marque comme sensible
  n'est ni lu ni envoyé.
- Texte seul, 1 Mo au plus. Chaque sens se désactive dans les réglages.

## L'historique

Les 50 derniers textes échangés, et ceux que vous épinglez, sont gardés **chiffrés** sur chaque
appareil (Keystore sur le téléphone, DPAPI sur le PC).

- **Sur le PC** : `Ctrl+Alt+V` (modifiable) fait descendre une carte en haut de l'écran, depuis
  n'importe quelle appli. Tapez pour chercher, flèches et Entrée pour copier : la carte remonte et
  vous pouvez coller aussitôt. Une épingle garde un élément, une croix le supprime.
- **Sur le téléphone** : l'icône Historique de l'accueil, avec la même recherche ; toucher un
  élément le copie.
- Un élément recopié depuis l'historique ne repart pas vers l'autre appareil. « Tout effacer »
  vide l'historique de l'appareil, épingles comprises.

## Les fichiers

- **Du téléphone vers le PC** : « Envoyer au PC » dans le menu de partage (une ou plusieurs
  photos, vidéos, documents), ou « Envoyer un fichier » sur l'accueil. Le transfert continue écran
  éteint ; la notification montre l'avancement, avec **Annuler**.
- **Du PC vers le téléphone** : glissez des fichiers sur la fenêtre ou sur la carte de
  l'historique, ou « Envoyer un fichier… » dans le menu de l'icône. En option, « Envoyer au
  téléphone » dans le menu contextuel de l'Explorateur (réglage de la page Transferts).
- **Réception** : `Téléchargements\BridgePlus\` sur le PC (modifiable), `Download/BridgePlus/` sur le
  téléphone (les photos arrivent dans la galerie). Une notification de Windows ou d'Android
  propose **Ouvrir** et **Ouvrir le dossier**.
- **Sûr** : empreinte SHA-256 vérifiée à l'arrivée ; écriture sous un nom provisoire, jamais de
  fichier à moitié écrit visible ; nom déjà pris : « photo (2).jpg », jamais d'écrasement ;
  espace libre vérifié avant de commencer. Aucune limite de taille.

## Installer

- **Téléphone** : APK de la [dernière version](https://github.com/Maubin76/BridgePlus/releases/latest).
- **PC** : zip portable de la même page ; dézippez-le où vous voulez et lancez `BridgePlus.exe`.
  Au premier appairage, Windows demande d'autoriser BridgePlus sur le réseau : cochez seulement
  **Réseaux privés**.

Puis, sur le PC : **Appairer un téléphone**. Sur le téléphone : **Appareils › Appairer un PC**, et
visez le QR code.

### Les autorisations du téléphone, et pourquoi

| Autorisation | Pourquoi |
|---|---|
| Localisation « Toujours » | Android range la lecture du **nom du Wi-Fi** sous la localisation. C'est le seul moyen de reconnaître la maison, appli fermée. Aucune position n'est lue. |
| Fonctionner en arrière-plan (batterie) | Sans elle, Android interdit de relancer la liaison au retour à la maison. |
| Notifications | Pour la notification discrète « Relié à <PC> ». |
| Appareil photo | Pour lire le QR code d'appairage, rien d'autre. |

## Construire

```bash
bash ci/polices.sh            # polices Satoshi (licence : non versionnées)
./gradlew build               # tout construire, tous les tests, lint ; zéro avertissement
./gradlew :desktop:run        # lancer l'appli PC
./gradlew :app:installDebug   # installer l'appli sur le téléphone branché
```

Modules : `:core` (Kotlin pur : liaison, appareils, réglages), `:ui` (Compose partagé : thème,
composants), `:app` (Android), `:desktop` (Windows). La version vaut `1.<nombre de commits>`.
