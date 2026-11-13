# PontPlus — plan de l'étape 1

Squelette des modules, thème, couche de liaison reprise de KeePlus, appairage par QR code,
connexion permanente avec reconnexion.

À la fin de l'étape : on installe l'APK et l'appli PC, on appaire le téléphone en scannant le QR
code, et l'icône du PC indique « Relié à Pixel ». La liaison se rétablit seule après une coupure du
Wi-Fi, une mise en veille du PC, un redémarrage du téléphone ou une mise à jour. Hors de la maison,
le service s'arrête.

---

## 1. Ce que KeePlus fait et ce qui doit changer

KeePlus échange **une demande et une réponse par connexion** (`LinkServer.serve` lit une trame, en
écrit une, ferme). PontPlus garde une **connexion ouverte en permanence** dans les deux sens. Trois
conséquences :

1. **Une session après la poignée de main.** L'ouverture reprend le message scellé de KeePlus (clé
   de la paire, date, numéro à usage unique, `ReplayGuard`). Ensuite, chaque côté dérive une **clé
   de session par sens** (HKDF-SHA256 de la clé de paire, sel = numéro du client ‖ numéro du
   serveur). Chaque trame porte un **numéro d'ordre** qui augmente de 1 : il sert de nonce GCM (unique
   par clé de session) et de donnée authentifiée. Une trame rejouée, réordonnée, supprimée ou
   venant d'une autre session est refusée et la connexion est coupée. Les trames suivantes n'ont plus
   besoin de l'horloge ni de la table des nonces, et la limite d'environ 2³² messages par clé
   d'AES-GCM avec des nonces tirés au hasard ne se pose plus.
2. **Une trame binaire.** KeePlus met le contenu chiffré en base64 dans du JSON (+33 %).
   L'énoncé demande des fichiers en flux binaire découpé en morceaux. Nouvelle enveloppe :
   `[longueur u32][nature u8][corps]`. `hello`, `open` et `error` gardent un corps JSON (`LinkFrame`
   de KeePlus). `data` contient `[n° d'ordre u64][chiffré GCM]`, et le clair est
   `[type u8][contenu]` : type 0 pour un message JSON, type 1 pour un morceau de fichier (étape 4).
   La taille maximale de 64 Mo de KeePlus est conservée.
3. **Un battement de cœur asymétrique.** Un téléphone en veille profonde ne fait plus tourner ses
   minuteurs, mais un paquet Wi-Fi entrant le réveille. C'est donc **le PC qui envoie un `ping` toutes
   les 30 s** et le téléphone qui répond `pong`. Le PC déclare la session morte après 75 s sans rien
   recevoir. Le téléphone vérifie, à partir de `elapsedRealtime`, qu'il a reçu quelque chose dans les
   90 dernières secondes. On active aussi le TCP keepalive. La reconnexion se fait côté téléphone,
   avec un délai de 1, 2, 4, 8, 16, 32 puis 60 s au plus, une variation aléatoire de ±20 %, et une
   remise à zéro à chaque changement de réseau.

## 2. Fichiers repris de KeePlus

Paquet `fr.maubin.keeplus.*` → `fr.maubin.pontplus.*`. Noms de fils `KeePlus-…` → `PontPlus-…`.

### `:core` — `fr.maubin.pontplus.link`

| KeePlus | PontPlus | Changements |
|---|---|---|
| `link/LinkCrypto.kt` | `link/LinkCrypto.kt` | Repris tel quel (`LinkCipher`, base64, aléa). Ajout de `deriveSessionKeys()` (HKDF-SHA256 avec `HmacSHA256` du JDK) et d'un `SessionCipher` à nonce compteur. |
| `link/LinkProtocol.kt` | `link/LinkProtocol.kt` | Ports `kLinkPort = 47841`, `kDiscoveryPort = 47840`. `kMaxFrameBytes` et `kMaxClockSkewMs` inchangés. Nature `kKindOpen`, `kKindData` ajoutées. `writeFrame`/`readFrame` passent à l'enveloppe binaire. `DeviceInfo` : `format` est remplacé par `features` (liste des fonctions connues, pour les versions voisines). `ReplayGuard` est repris tel quel pour la poignée de main, et `SequenceGuard` est ajouté. |
| `link/Pairing.kt` | `link/Pairing.kt` | Schéma `pontplus://appairer`. Mêmes paramètres et même validité de 5 min. Un QR code de KeePlus est refusé (et testé). |
| `link/Discovery.kt` | `link/Discovery.kt` | Préfixe `PONTPLUS?`. Le répondeur écoute toutes les interfaces, car la réception des diffusions sur une adresse précise n'est pas fiable sous Windows, mais **ne répond qu'aux demandes venant d'un sous-réseau de la maison** (filtre passé en paramètre). |
| `link/HomeNetwork.kt` | `link/HomeNetwork.kt` | Repris tel quel (`NetworkId`, `isHomeNetwork`). |
| `link/LinkServer.kt` | `link/LinkHub.kt` | Réécrit autour du même tri (`hello` en clair, `sealed` vérifié, le reste refusé). Un `ServerSocket` **par adresse de la maison** au lieu d'une écoute sur toutes les interfaces (`bind` renouvelé si les adresses changent). Chaque connexion acceptée devient une `LinkSession`. |
| `link/LinkClient.kt` | `link/LinkClient.kt` | `hello()` est repris. `call()` est remplacé par `open()`, qui rend une `LinkSession`. |
| *(nouveau)* | `link/LinkSession.kt` | Canal scellé permanent : fil de lecture, file d'écriture, ping et pong, délai de mort, fermeture propre, état observable. |
| *(nouveau)* | `link/Backoff.kt` | Délai progressif plafonné. |
| *(nouveau)* | `link/Messages.kt` | Messages JSON (`@Serializable sealed`) : `Ping`, `Pong`, `Pair`, `Paired`, `Forgotten`. Les messages suivants arrivent aux étapes 2 à 6. |
| `link/SyncCall.kt` | — | **Non repris** : le téléphone garde la connexion, le PC n'a plus besoin de l'appeler. |

### `:core` — appareils, réglages, stockage

| KeePlus | PontPlus | Changements |
|---|---|---|
| `sync/PcSync.kt` | `peers/PcLink.kt` | Données `PairedPhone`/`PcPeers`, appairage par offre, réseaux de la maison réunis, écoute seulement à la maison ou pendant un appairage : **repris**. La partie coffre (`answerSync`, `fVault`, `fOnStored`, `callPhones`) est **retirée**. Ajouts : plusieurs sessions simultanées, état « connecté / absent » par téléphone, renommage (`alias` local), `forget()` qui détruit la clé et coupe la session, ping toutes les 30 s. |
| `sync/PhoneSync.kt` | `peers/PhoneLink.kt` | `PairedPc`/`PhonePeers`, `pair()` et la recherche (dernières adresses puis découverte) : **repris**. Coffre en attente et `syncAll` : **retirés**. Remplacés par une boucle de connexion par PC avec `Backoff`. |
| `sync/PcSync.kt` → `writeAtomic` | `store/AtomicFile.kt` | Repris tel quel. |
| *(nouveau)* | `store/SecretBox.kt` | Interface « sceller / ouvrir » pour les fichiers sensibles. Dès l'étape 1, `appareils.json` (qui contient les clés de paire) est chiffré : Keystore sur Android, DPAPI sur Windows. KeePlus les garde en clair dans le stockage privé. L'étape 3 réutilisera la même interface pour l'historique. |
| `settings/Settings.kt` | `settings/Settings.kt` | Même `SettingsStore`. `PontSettings` en étape 1 : `updates`, `ignoredUpdate`, `launchAtStartup`. Les autres réglages arrivent avec leur étape. |

### `:app` — Android

| KeePlus | PontPlus | Changements |
|---|---|---|
| `QrScanner.kt` | `QrScanner.kt` | Repris tel quel (CameraX + ZXing). |
| `DevicesScreen.kt` | `DevicesScreen.kt` | Liste des PC (nom, pastille d'état avec icône et texte, dernière connexion), renommer, « Oublier cet appareil » avec confirmation, bouton « Appairer un PC ». La partie synchronisation du coffre est retirée. |
| `AndroidSync.kt` | `AndroidNetwork.kt` | `currentPhoneNetwork`, `hasWifiNamePermission`, `phoneName` sont repris. Le reste (coffre, travail en arrière-plan) est retiré. |
| `PhoneSettings.kt` | `PhoneSettings.kt` | Adapté aux réglages de PontPlus. |
| `SyncWorker.kt` | — | Remplacé par le service de premier plan. |
| *(nouveau)* | `LinkService.kt` | Service de premier plan `connectedDevice` qui fait tourner `PhoneLink`. |
| *(nouveau)* | `NetworkWatch.kt` | `registerNetworkCallback(…, PendingIntent)` sur le Wi-Fi, avec un récepteur qui relance le service au retour à la maison. |
| *(nouveau)* | `BootReceiver.kt` | `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED`. |
| *(nouveau)* | `MainActivity.kt`, `HomeScreen.kt`, `SettingsScreen.kt` | Accueil (état de la liaison en grand et état vide « Rien d'échangé pour l'instant »), Appareils, Réglages (réseaux de la maison, autorisations). |

### `:desktop` — Windows

| KeePlus | PontPlus | Changements |
|---|---|---|
| `NetworkProbe.kt` | `NetworkProbe.kt` | Les fonctions d'analyse (`parseSsid`, `parseGateway`, `parseMac`) sont reprises. Ajout de `homeAddresses()`, qui relie le réseau reconnu à **son** interface : 4e colonne de `route print` pour la box, nom d'interface de `netsh wlan` pour le Wi-Fi. On obtient ainsi les adresses d'écoute et les sous-réseaux du filtre de découverte. |
| `QrCode.kt` | `QrCode.kt` | Repris tel quel. |
| `DevicesPanel.kt` | `DevicesPanel.kt` | Adapté comme l'écran Android : téléphones, état, renommer, oublier, QR code. |
| `SingleInstance.kt`, `WindowsStartup.kt`, `TitleBar.kt`, `WindowFront.kt` | idem | Noms changés. `WindowsStartup` est activé par défaut. |
| `DesktopSettings.kt` | `DesktopSettings.kt` | Adapté. |
| `Main.kt` | `Main.kt` | Nouveau. Icône de la zone de notification dessinée selon l'état : forme et pastille différentes, pas seulement la couleur, avec une info-bulle « PontPlus — relié à Pixel ». Menu de l'étape 1 : Ouvrir, Appairer un téléphone, Réglages, Quitter (les autres entrées arrivent avec leur étape). Fenêtre principale avec Appareils et Réglages, plus une carte expliquant le pare-feu au premier lancement. |
| *(nouveau)* | `Dpapi.kt` | `SecretBox` par `Crypt32Util` (jna-platform, déjà présent). |

### `:ui` et construction

| KeePlus | PontPlus | Changements |
|---|---|---|
| `ui/theme/Theme.kt` | `ui/theme/Theme.kt` | Même structure (couleurs nommées, typographie Satoshi). **Thème clair et sombre** selon le système, avec la couleur d'accent de PontPlus. |
| `ui/theme/Logo.kt` | `ui/theme/Logo.kt` | Nouveau tracé : arche de pont évidée d'un « + », dans le carré arrondi de la gamme. |
| `ui/components/Buttons.kt`, `ListParts.kt`, `icons/LineIcons.kt` | idem | Repris et allégés. Ajout de `LinkStatusBadge` (icône, texte et couleur). |
| `ui/platform/Platform*.kt` | idem | Repris (`expect`/`actual`), avec en plus la lecture du réglage « réduire les animations ». |
| `ci/polices.sh` | `ci/polices.sh` | Repris tel quel (Satoshi). |
| `gradle/libs.versions.toml` | idem | Mêmes versions. Retirés : kotpass, okio, biometric, fragment, autofill, work. |
| `settings.gradle.kts`, `build.gradle.kts` | idem | `rootProject.name = "PontPlus"`, modules `core`, `ui`, `app`, `desktop`. Le modèle `com.example.bridgeplus` est supprimé. |
| `.github/workflows/ci.yml` | idem | Tests, APK release, zip Windows, publication. La version ne s'incrémente plus d'elle-même : elle vient du nombre de commits. |

Paramètres Android : `minSdk = 36`, `compileSdk = 37`, `targetSdk = 36` (comme KeePlus).
Version : `versionName = "1.<nombre de commits>"`, `versionCode = <nombre de commits>`, calculés une
seule fois dans le build racine (sur le modèle de SportPlus). Pour le paquet Windows : `1.<n>.0`.
Zéro avertissement : `allWarningsAsErrors = true` dans les quatre modules.

## 3. Service de premier plan sur Android 16

- **`dataSync` ne convient pas.** Depuis Android 15, il est limité à **6 h par 24 h**
  (`onTimeout`, puis arrêt forcé) et **ne peut pas être démarré depuis `BOOT_COMPLETED`**. Une
  liaison qui doit tenir toute la journée ne rentre pas dans ce cadre.
- **`connectedDevice` est le bon type.** Il est fait pour les échanges avec un appareil externe
  « par Bluetooth, NFC, USB ou réseau » et n'a pas de limite de durée. Ses prérequis sont
  `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus une des permissions de la liste, ici
  `CHANGE_WIFI_STATE` (permission normale, accordée à l'installation). Il peut être démarré depuis
  `BOOT_COMPLETED`. KDE Connect fait le même choix.
- **Démarrage en arrière-plan.** Depuis Android 12, une appli en arrière-plan ne peut démarrer un
  service de premier plan que dans des cas d'exception : `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
  **appli exclue de l'optimisation de la batterie**… Le retour au Wi-Fi de la maison passe par un
  `PendingIntent`, qui n'est pas une exception. **Sans l'exclusion de la batterie, la reconnexion
  automatique ne marche donc qu'après un redémarrage ou une ouverture de l'appli.** L'accueil le
  dira (« Reconnexion automatique désactivée : autorisez PontPlus à fonctionner en arrière-plan »),
  et l'étape correspondante de l'assistant (étape 6) l'expliquera en une phrase. Cette exclusion
  évite aussi la coupure du réseau en mode Doze.
- **Notification du service** : canal de faible importance, silencieux, « Relié à <PC> ». Elle
  disparaît dès que le service s'arrête hors de la maison.

## 4. Reconnaître le Wi-Fi de la maison en arrière-plan (décidé : localisation « Toujours »)

KeePlus lit le nom du Wi-Fi avec `ACCESS_FINE_LOCATION`, à l'écran. PontPlus doit le lire **depuis
un service en arrière-plan** (au réveil réseau, au démarrage). Selon la documentation, un service
`connectedDevice` ne donne pas l'accès à la localisation « pendant l'utilisation » : il faut
**`ACCESS_BACKGROUND_LOCATION`** (« Toujours autoriser », réglé dans les paramètres de l'appli). Je
le vérifierai sur le téléphone dès le début de l'étape.

## 5. Tests de l'étape 1 (`:core`, JVM)

- `FrameTest` : codage et décodage de l'enveloppe binaire, trame tronquée, trop grande, nature
  inconnue.
- `ReplayTest` : `ReplayGuard` (date hors fenêtre, nonce déjà vu) ; `SequenceGuard` (trame rejouée,
  réordonnée ou sautée → session coupée).
- `CryptoTest` : un octet modifié fait échouer l'ouverture ; les clés de session diffèrent par sens
  et par session.
- `PairingTest` : aller-retour du QR code, QR code de KeePlus refusé, **offre expirée refusée** par
  le PC, mauvaise clé.
- `SessionTest` (serveur et client sur `localhost`) : poignée de main, ping et pong, pair silencieux
  déclaré mort (délais raccourcis), appareil non appairé refusé, **appareil oublié refusé**, deux
  téléphones sur un PC, un téléphone avec deux PC.
- `BindTest` : un serveur lié à `127.0.0.1` n'accepte rien sur `127.0.0.2`.
- `BackoffTest` : 1 → 2 → 4 … → 60 s, plafond, remise à zéro.
- `PeersStoreTest` : renommage, oubli qui détruit la clé, fichier scellé illisible sans la clé.

Le test d'intégration avec le fichier de 50 Mo arrive à l'étape 4, avec le transfert de fichiers.

## 6. Documents livrés à l'étape 1

`DESIGN.md` (accent, clair et sombre, composants, accessibilité), `PRODUCT.md`
(`impeccable:product-schema 1`), `README.md` (squelette : logo, badges, « En bref » ; captures à
l'étape 6), `doc/CRYPTOGRAPHIE.md` (poignée de main et session, sur le modèle de KeePlus).

## 7. Mesure de la batterie

Dès la fin de l'étape 1, puisque la liaison permanente est la principale dépense : sur le téléphone
réel, `adb shell dumpsys batterystats --reset`, une journée normale, puis
`dumpsys batterystats --charged fr.maubin.pontplus`. On relève la consommation estimée, les réveils
et le temps radio Wi-Fi. Si le ping toutes les 30 s coûte trop, on le passe à 60 ou 120 s. Le
résultat sera indiqué dans le README.
