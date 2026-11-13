---
name: PontPlus
description: Le téléphone et le PC reliés par le Wi-Fi de la maison ; fonds neutres et chauds, un seul accent orange international, l'état de la liaison écrit en grand.
colors:
  light-ground: "#FAF8F6"
  light-surface: "#FFFFFF"
  light-surface-high: "#F2EEEA"
  light-rule: "#E6E0DA"
  light-text: "#1C1816"
  light-text-muted: "#5F5751"
  light-text-faint: "#7D746D"
  light-accent: "#B8401F"
  light-on-accent: "#FFFFFF"
  light-accent-soft: "#F8E4DB"
  light-success: "#2D7A57"
  light-danger: "#B3261E"
  dark-ground: "#121010"
  dark-surface: "#1C1917"
  dark-surface-high: "#262220"
  dark-rule: "#36312E"
  dark-text: "#F3EFEC"
  dark-text-muted: "#ADA49E"
  dark-text-faint: "#8C847E"
  dark-accent: "#FF8A5C"
  dark-on-accent: "#2A0E04"
  dark-accent-soft: "#3B2219"
  dark-success: "#6FC59A"
  dark-danger: "#F2827A"
typography:
  hero:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "40sp"
    fontWeight: 300
    lineHeight: "46sp"
    letterSpacing: "-0.025em"
  display:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "30sp"
    fontWeight: 300
    lineHeight: "36sp"
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "20sp"
    fontWeight: 400
    lineHeight: "26sp"
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "22sp"
    letterSpacing: "0"
  body:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "22sp"
    letterSpacing: "0"
  body-small:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
    letterSpacing: "0.005em"
  label:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.01em"
radii:
  pill: "999dp"
  card: "20dp"
  info: "16dp"
  device-icon: "12dp"
  logo: "24%"
---

# PontPlus — direction artistique

## En une phrase
Un outil discret qu'on regarde rarement : des fonds neutres légèrement chauds, un seul accent, l'**orange international** du Golden Gate, et l'état de la liaison écrit en très grand, en fin, comme les grands titres de la gamme.

## Parenté avec la gamme
- **Satoshi** et ses grands titres fins (graisse 300), comme KeePlus.
- Icônes au trait de 1,6 sur une grille de 24, sans aplat, comme KeePlus.
- Cartes `surface` à grand rayon, pilules pour les boutons et les pastilles.
- Sobriété de PatrimoinePlus : une seule chose élève la voix par écran (ici, l'état de la liaison).

## Thème
**Clair et sombre, selon le système**, sur Android comme sur PC. La barre de titre de Windows 11 prend la couleur du fond.

## Couleurs
- **Un seul accent** : `accent` (orange international). Il marque le bouton principal, la liaison établie (icône, pastille, disque) et le logo. Rien d'autre n'est coloré.
- **Deux couleurs d'état** : `success` (appairage réussi) et `danger` (refus, erreur, action destructrice). Toujours accompagnées d'un texte.
- **Contrastes vérifiés** : `text-muted` atteint au moins 6:1 sur `ground`, `surface` et `surface-high` dans les deux thèmes ; `accent` au moins 4,8:1 ; le texte sur l'accent 5,5:1 (clair) et 7,8:1 (sombre). `text-faint` est réservé aux indications non essentielles.

## L'état de la liaison
C'est l'information principale. Il a toujours **trois porteurs** : une icône, un texte et une couleur. La couleur seule ne porte jamais rien.

| État | Icône | Couleur | Texte (exemple) |
|---|---|---|---|
| Relié | deux maillons attachés | accent | « Relié à Bureau » |
| Recherche | flèches qui tournent | text-muted | « Recherche du PC… » |
| Absent | maillons séparés | text-muted | « Bureau est absent » |
| Refusé | cercle « ! » | danger | « Bureau refuse ce téléphone » |
| Hors de la maison | maison barrée | text-muted | « Hors du réseau de la maison » |
| Arrêté | cercle barré | text-muted | « Aucun PC appairé » |

En haut de l'accueil (`LinkHero`), l'icône est dans un disque de 56 dp (`accent-soft` si relié, `surface-high` sinon), suivie du titre en `hero` et d'une précision en `text-muted`.

**Icône de la zone de notification (PC)** : le logo, orange si relié, gris sinon. Une marque change sa forme : barre oblique hors de la maison, anneau pour un téléphone absent, pastille rouge pour une erreur. L'info-bulle reprend le texte.

## Logo
Un **pont en arche** blanc (tablier fin posé sur une arche) avec le « + » de la gamme au-dessus, dans un carré arrondi orange (rayon de 24 % du côté). Un seul tracé (`kLogoPath`, grille de 108) sert à l'appli, à l'icône Android (adaptative et monochrome) et à l'icône Windows, générées par `LogoAssetsTest`.

## Composants
- **Bouton principal** : pilule pleine `accent`, texte `on-accent` en `label`. Un seul par écran.
- **Bouton secondaire** : pilule à contour `rule`, texte `text`.
- **Bouton discret** : texte seul ; en `danger` pour « Oublier cet appareil ».
- **Carte** (`PontCard`) : fond `surface`, rayon `card`, marge de 18 dp.
- **Encadré d'explication** (`InfoCard`) : fond `surface-high`, rayon `info`, icône `text-muted`, une phrase et une action. Sert aux autorisations et au pare-feu.
- **Ligne d'appareil** : icône dans un carré `surface-high` (rayon `device-icon`), nom en `title`, pastille d'état et « vu il y a… », puis les actions renommer et oublier (icônes avec description pour les lecteurs d'écran).
- **Pastille d'état** : pilule `accent-soft` si relié, `surface-high` sinon, icône de 16 dp et texte.

## Mise en page
- **Android** : trois onglets (Accueil, Appareils, Réglages) dans une barre de navigation `surface`. L'onglet actif a l'icône `accent` sur un indicateur `accent-soft`. Contenu en colonne avec une marge de 20 dp.
- **PC** : une colonne de navigation de 220 dp (logo, Appareils, Réglages), puis le contenu limité à 640 dp de large. La fenêtre se ferme dans la zone de notification.

## Mouvement
Sobre : le changement d'état de la liaison se fait en **fondu**. Si le système demande de réduire les animations (Android : « Supprimer les animations » ; Windows : « Effets d'animation » désactivés), le changement est instantané (`LocalAnimationsEnabled`).

## Ton des textes
Français, vouvoiement, phrases courtes. Exemples : « Relié à Bureau », « Hors du réseau de la maison », « Oublier cet appareil », « Valable encore 4 min 12 s ».

## À éviter
- Une deuxième couleur d'accent, des dégradés colorés.
- Une information portée par la seule couleur.
- Une notification permanente bavarde : celle du service dit seulement « Relié à <PC> ».
- Plus d'un bouton principal par écran.
