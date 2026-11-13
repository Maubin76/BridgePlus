---
name: PontPlus
description: Le téléphone et le PC reliés, dans la direction artistique de KeePlus : noir et argent, une brume grise qui tombe sur une grille fine, un pont flou en décor, l'état de la liaison en très grand.
colors:
  void: "#050505"
  ground: "#0B0B0C"
  mist-top: "#9A9A9C"
  mist-mid: "#3A3A3C"
  surface: "#141415"
  surface-high: "#1C1C1E"
  surface-highest: "#262628"
  rule: "#2A2A2C"
  grid-line: "#FFFFFF0F"
  grid-line-lit: "#FFFFFF2E"
  emblem: "#2E2E30"
  text: "#F5F5F5"
  text-muted: "#9C9C9E"
  text-faint: "#6A6A6C"
  silver-light: "#F2F2F2"
  silver-dark: "#B8B8BA"
  silver-ink: "#111111"
  danger: "#D9645F"
  success: "#6FB592"
typography:
  hero:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "44sp"
    fontWeight: 300
    lineHeight: "50sp"
    letterSpacing: "-0.025em"
  display:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "34sp"
    fontWeight: 300
    lineHeight: "40sp"
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "24sp"
    fontWeight: 400
    lineHeight: "30sp"
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "17sp"
    fontWeight: 500
    lineHeight: "24sp"
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
  field: "16dp"
  device-icon: "12dp"
  logo: "10dp"
---

# PontPlus — direction artistique

PontPlus **reprend la direction artistique de KeePlus**, à l'identique : mêmes couleurs, mêmes polices, même décor, mêmes composants. Seuls changent l'emblème (un pont à la place du bouclier) et les écrans. Ce choix remplace la proposition initiale d'un thème clair et sombre avec un accent orange.

## En une phrase
Du noir profond et de l'argent : une brume grise descend du haut de l'écran sur une grille très fine, un grand pont flou flotte en arrière-plan, et l'état de la liaison s'écrit en grand, fin, en deux tons.

## Thème
**Sombre uniquement**, sur Android comme sur PC, comme KeePlus. La barre de titre de Windows 11 prend le noir `void`.

## Couleurs
- **Monochrome** : tout en noir, gris, blanc et argent. Aucune couleur d'accent.
- **Deux couleurs d'état seulement**, sourdes : `danger` (refus, erreur) et `success` (appairage réussi). Elles s'accompagnent toujours d'une icône et d'un texte.
- Le texte secondaire (`text-muted`) atteint au moins 4.5:1 sur `ground`. `text-faint` est réservé aux indications non essentielles.

## Typographie
- **Satoshi** pour toute l'interface. Graisse Light (300) dans les grands corps, Regular et Medium ailleurs.
- **Mots en deux tons** : dans les grands titres, le contexte est en `text-muted` et le mot fort en `text`. Exemples : « Relié à **Bureau.** », « Hors du réseau **de la maison.** », « Votre téléphone et votre PC, **reliés.** »

## L'état de la liaison
C'est l'information principale. Il a toujours une **icône et un texte** ; la couleur n'en porte presque aucune.

| État | Icône | Titre (exemple) |
|---|---|---|
| Relié | deux maillons attachés (blanc) | « Relié à **Bureau.** » |
| Recherche | flèches qui tournent | « Recherche **du PC…** » |
| Absent | maillons séparés | « Bureau est **absent.** » |
| Refusé | cercle « ! » (`danger`) | « Bureau **refuse ce téléphone.** » |
| Hors de la maison | maison barrée | « Hors du réseau **de la maison.** » |
| Arrêté, aucun appareil | cercle barré | « Aucun téléphone **appairé.** » |

- **En haut de l'accueil** : l'icône dans un disque `surface-high` au contour `rule`, puis le titre en `hero`.
- **Pastille d'état** dans les listes : « Relié » est une pastille active (fond `text`, texte `void`), comme le filtre actif de KeePlus ; les autres états ont un contour `rule`.
- **Icône de la zone de notification (PC)** : le logo argent, net si relié, voilé de noir sinon, avec une marque qui change sa forme : barre oblique hors de la maison, anneau pour un téléphone absent, pastille `danger` pour une erreur. L'info-bulle reprend le titre.

## Le décor (écrans clés seulement)
Il s'applique à l'**accueil du téléphone**. Il se compose de trois couches, celles de KeePlus :
1. **Brume** : dégradé vertical de `mist-top` (en haut) vers `mist-mid` (vers 40 %), puis `void` (à partir de 70 %).
2. **Grille** : traits de 1 px en `grid-line`, avec un pas de 48 dp, estompés vers le bas.
3. **Emblème** : le pont en `emblem`, très agrandi (environ 1,6 fois la largeur de l'écran), décentré à droite, flou de 32 dp, coupé par les bords. Un grain léger couvre l'ensemble.

Les **écrans de travail** (Appareils, Réglages, fenêtre du PC) ont un fond `ground`, la grille et le grain, et un léger voile de brume sur les 120 premiers dp.

## Logo
Un **pont noir** (tablier fin posé sur une arche) surmonté du « + » de la gamme, sa moitié droite en gris (facette), **gravé dans une plaque d'aluminium brossé** au chanfrein usiné, avec une lumière unique en haut à gauche. C'est la matière du logo de KeePlus. En dessous de 40 dp, la version est à plat. L'icône adaptative d'Android (plaque brossée en fond, pont gravé au premier plan, version monochrome) et l'icône Windows sont générées par `LogoAssetsTest`.

## Composants (ceux de KeePlus)
- **Bouton principal** (`SilverButton`) : pilule pleine largeur, dégradé `silver-light` → `silver-dark`, liseré clair en haut, reflet toutes les 6 s, texte `silver-ink`. Un seul par écran.
- **Bouton secondaire** (`GhostButton`) : pilule avec un contour `rule`.
- **Lien** (`TextLink`) : texte `text-muted`, en `danger` pour une action destructrice.
- **Sections** (`SettingsSection`) : titre discret en `body-small`, puis une carte `surface` au contour `rule`, rayon `card`.
- **Pastilles de choix** (`FilterPill`) : oui ou non (« Activé » / « Désactivé »), réseaux de la maison. Pas d'interrupteur.
- **Ligne d'appareil** : icône dans un carré `surface-high` (rayon `device-icon`), nom en `title`, pastille d'état et « vu il y a… », actions renommer et oublier en icônes rondes. Pas de séparateur.
- **Dialogues** : carte `surface-high` de rayon 24, titre `headline`, texte `body-small`, deux boutons fantômes.
- **Encadré d'explication** : carte `surface`, icône `text-muted` et une phrase, avec un lien d'action (autorisations, pare-feu).
- **Icônes** : le jeu au trait de 1,6 de KeePlus, complété des icônes de la liaison (maillons, maison barrée, ordinateur, téléphone, QR code, Wi-Fi).

## Mise en page
- **Android** : comme KeePlus, un seul écran principal (l'accueil, avec le décor). Appareils et Réglages sont accessibles par des icônes en haut à droite. Ce sont des écrans de travail avec une flèche de retour, et leurs boutons sont réduits de 20 %.
- **PC** : une colonne de navigation `void` (marque, Appareils, Réglages), puis la page de travail limitée à 640 dp. La fenêtre se ferme dans la zone de notification.

## Mouvement
- Le changement d'état de la liaison se fait en fondu (220 ms).
- Reflet argent sur le bouton principal, parallaxe de l'emblème (inclinaison du téléphone, souris sur PC), comme dans KeePlus.
- **Tout est coupé** si le système demande de réduire les animations (Android : « Supprimer les animations » ; Windows : « Effets d'animation »).

## Ton des textes
Français, vouvoiement, sobre. Exemples : « Relié à Bureau », « Oublier cet appareil », « Valable encore 4 min 12 s ».

## À éviter
- Toute couleur décorative : pas d'accent, pas de dégradé coloré.
- Le décor complet derrière les listes.
- Plus d'un bouton argenté par écran.
- Le gras dans les grands titres : la force vient de la taille et du contraste gris/blanc.
- Une notification permanente bavarde : celle du service dit seulement « Relié à <PC> ».
