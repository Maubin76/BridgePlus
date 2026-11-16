"""Visuels du README (doc/images) : écrans posés sur un fond gris dégradé, dans
des cadres de téléphone ou de fenêtre, à la manière des présentations produit
(repris de KeePlus).

Entrée : desktop/build/captures/readme, rendus hors écran de l'appli PC et des
écrans du téléphone (composants partagés), avec des données d'exemple :
  ./gradlew :desktop:test --tests "*ReadmeScreenshotsTest*"
La CI les produit (workflow « Visuels du README ») : rien à lancer sur le PC.
Lancer depuis la racine du dépôt : python ci/vitrines.py"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

kRaw   = 'desktop/build/captures/readme/'
kOut   = 'doc/images/'
kLogo  = 'doc/logo/bridgeplus-logo.png'
kFonts = ['C:/Windows/Fonts/seguisb.ttf', 'C:/Windows/Fonts/segoeui.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']


def font(inSize):
	"""Police des textes dessinés (Segoe UI sous Windows), à défaut celle de Pillow."""
	for vPath in kFonts:
		if os.path.exists(vPath):
			return ImageFont.truetype(vPath, inSize)
	return ImageFont.load_default(inSize)


def background(inW, inH):
	"""Fond gris dégradé, plus clair en haut à gauche."""
	vImg = Image.new('RGB', (inW, inH))
	vPx  = vImg.load()
	for vY in range(inH):
		for vX in range(inW):
			vT = (vX / inW) * 0.35 + (vY / inH) * 0.65
			vC = int(158 - 46 * vT)
			vPx[vX, vY] = (vC, vC, vC + 2)
	return vImg


def rounded_mask(inSize, inRadius):
	"""Masque d'un rectangle arrondi (anti-crénelé par sur-échantillonnage)."""
	vScale = 4
	vBig   = Image.new('L', (inSize[0] * vScale, inSize[1] * vScale), 0)
	ImageDraw.Draw(vBig).rounded_rectangle((0, 0, vBig.width - 1, vBig.height - 1), inRadius * vScale, fill=255)
	return vBig.resize(inSize, Image.LANCZOS)


def shadow(inCanvas, inBox, inRadius, inBlur=60, inOpacity=110, inOffset=(0, 40)):
	"""Ombre douce sous un élément."""
	vLayer = Image.new('L', inCanvas.size, 0)
	vBox   = (inBox[0] + inOffset[0], inBox[1] + inOffset[1], inBox[2] + inOffset[0], inBox[3] + inOffset[1])
	ImageDraw.Draw(vLayer).rounded_rectangle(vBox, inRadius, fill=inOpacity)
	vLayer = vLayer.filter(ImageFilter.GaussianBlur(inBlur))
	inCanvas.paste(Image.new('RGB', inCanvas.size, (20, 20, 22)), (0, 0), vLayer)


def add_status(inScreen):
	"""Barre d'état dessinée (9:41, Wi-Fi, batterie) au-dessus d'un écran rendu sans
	barre : une bande prolonge le haut de l'écran, le contenu descend d'autant."""
	vW    = inScreen.width
	vBand = int(vW * 0.115)
	vImg  = Image.new('RGB', (vW, inScreen.height + vBand))
	vImg.paste(inScreen.crop((0, 0, vW, 1)).resize((vW, vBand)), (0, 0))
	vImg.paste(inScreen, (0, vBand))
	vDraw = ImageDraw.Draw(vImg)
	vInk  = (245, 245, 245)
	vDraw.text((int(vW * 0.075), int(vW * 0.042)), '9:41', font=font(int(vW * 0.034)), fill=vInk)
	vBx = int(vW * 0.855); vBy = int(vW * 0.048); vBw = int(vW * 0.062); vBh = int(vW * 0.029)
	vDraw.rounded_rectangle((vBx, vBy, vBx + vBw, vBy + vBh), vBh // 3, fill=vInk)
	vDraw.rectangle((vBx + vBw + 3, vBy + vBh // 3, vBx + vBw + 7, vBy + vBh - vBh // 3), fill=vInk)
	vCx = int(vW * 0.80); vCy = vBy + vBh
	for vR in (int(vW * 0.030), int(vW * 0.020)):
		vDraw.arc((vCx - vR, vCy - vR, vCx + vR, vCy + vR), 225, 315, fill=vInk, width=4)
	vDraw.ellipse((vCx - 5, vCy - 9, vCx + 5, vCy + 1), fill=vInk)
	return vImg


def phone(inScreen, inWidth):
	"""Téléphone : écran arrondi dans un cadre sombre à liseré métallique, îlot noir en haut."""
	vScreen = inScreen.convert('RGB')
	vSw     = inWidth
	vSh     = int(vScreen.height * inWidth / vScreen.width)
	vScreen = vScreen.resize((vSw, vSh), Image.LANCZOS)
	vBezel  = int(inWidth * 0.032)
	vEdge   = max(3, int(inWidth * 0.008))
	vW      = vSw + 2 * (vBezel + vEdge)
	vH      = vSh + 2 * (vBezel + vEdge)
	vR      = int(inWidth * 0.135)
	vBody   = Image.new('RGBA', (vW, vH), (0, 0, 0, 0))
	vMetal  = Image.new('RGB', (vW, vH))
	vMd     = ImageDraw.Draw(vMetal)
	for vY in range(vH):
		vC = int(118 - 70 * vY / vH)
		vMd.line((0, vY, vW, vY), fill=(vC + 14, vC + 14, vC + 18))
	vBody.paste(vMetal, (0, 0), rounded_mask((vW, vH), vR))
	vInner = rounded_mask((vW - 2 * vEdge, vH - 2 * vEdge), vR - vEdge)
	vBody.paste(Image.new('RGB', (vW - 2 * vEdge, vH - 2 * vEdge), (12, 12, 13)), (vEdge, vEdge), vInner)
	vBody.paste(vScreen, (vEdge + vBezel, vEdge + vBezel), rounded_mask((vSw, vSh), vR - vEdge - vBezel))
	vD      = ImageDraw.Draw(vBody)
	vIw     = int(vSw * 0.29); vIh = int(vSw * 0.083)
	vIx     = (vW - vIw) // 2; vIy = vEdge + vBezel + int(vSw * 0.026)
	vD.rounded_rectangle((vIx, vIy, vIx + vIw, vIy + vIh), vIh // 2, fill=(0, 0, 0))
	vD.ellipse((vIx + vIw - vIh * 0.85, vIy + vIh * 0.25, vIx + vIw - vIh * 0.35, vIy + vIh * 0.75), fill=(28, 30, 40))
	return vBody


def title_bar(inWidth, inHeight, inTitle, inDark=True):
	"""Barre de titre de Windows 11 (sombre comme celle de BridgePlus, ou claire) :
	icône, titre, réduire, agrandir, fermer."""
	vBg   = (5, 5, 5) if inDark else (238, 238, 241)
	vInk  = (235, 235, 235) if inDark else (30, 30, 30)
	vBar  = Image.new('RGB', (inWidth, inHeight), vBg)
	vDraw = ImageDraw.Draw(vBar)
	vIcon = inHeight // 2
	if inDark and os.path.exists(kLogo):
		vLogo = Image.open(kLogo).convert('RGBA').resize((vIcon, vIcon), Image.LANCZOS)
		vBar.paste(vLogo, (inHeight // 2, (inHeight - vIcon) // 2), vLogo)
		vText = inHeight // 2 + vIcon + inHeight // 3
	else:
		vText = inHeight // 2
	vFont = font(int(inHeight * 0.36))
	vDraw.text((vText, inHeight // 2), inTitle, font=vFont, fill=vInk, anchor='lm')
	vBtn  = int(inHeight * 1.45)
	vG    = int(inHeight * 0.16)
	vCy   = inHeight // 2
	vX    = inWidth - vBtn * 3
	vDraw.line((vX + vBtn // 2 - vG, vCy, vX + vBtn // 2 + vG, vCy), fill=vInk, width=2)
	vX   += vBtn
	vDraw.rectangle((vX + vBtn // 2 - vG, vCy - vG, vX + vBtn // 2 + vG, vCy + vG), outline=vInk, width=2)
	vX   += vBtn
	vDraw.line((vX + vBtn // 2 - vG, vCy - vG, vX + vBtn // 2 + vG, vCy + vG), fill=vInk, width=2)
	vDraw.line((vX + vBtn // 2 - vG, vCy + vG, vX + vBtn // 2 + vG, vCy - vG), fill=vInk, width=2)
	return vBar


def chrome(inShot, inTitle='BridgePlus'):
	"""Fenêtre de BridgePlus : barre de titre sombre au-dessus du rendu (rendus à 200 %)."""
	vShot = inShot.convert('RGB')
	vBar  = 64
	vImg  = Image.new('RGB', (vShot.width, vShot.height + vBar))
	vImg.paste(title_bar(vShot.width, vBar, inTitle), (0, 0))
	vImg.paste(vShot, (0, vBar))
	return vImg


def document_window(inW, inH):
	"""Fenêtre claire d'une autre appli (un compte rendu en cours d'écriture), sous la
	carte de l'historique."""
	vBar  = 64
	vImg  = Image.new('RGB', (inW, inH), (250, 250, 251))
	vImg.paste(title_bar(inW, vBar, 'Compte rendu - réunion de chantier.txt', False), (0, 0))
	vDraw = ImageDraw.Draw(vImg)
	vDraw.line((0, vBar, inW, vBar), fill=(222, 222, 226), width=2)
	vLeft = int(inW * 0.16)
	vY    = vBar + 380
	vDraw.text((vLeft, vY), 'Réunion de chantier - mardi', font=font(46), fill=(30, 30, 32))
	vY   += 110
	for vLength in (0.62, 0.55, 0.66, 0.30, 0, 0.58, 0.64, 0.41, 0, 0.60, 0.52):
		if vLength:
			vDraw.rounded_rectangle((vLeft, vY, vLeft + int(inW * vLength), vY + 18), 9, fill=(214, 214, 219))
		vY += 54
	return vImg


def window(inShot, inWidth, inRadius=18):
	"""Fenêtre de PC : capture aux coins arrondis, liseré fin."""
	vShot = inShot.convert('RGB')
	vH    = int(vShot.height * inWidth / vShot.width)
	vShot = vShot.resize((inWidth, vH), Image.LANCZOS)
	vOut  = Image.new('RGBA', (inWidth + 2, vH + 2), (0, 0, 0, 0))
	vOut.paste(Image.new('RGB', vOut.size, (70, 70, 74)), (0, 0), rounded_mask(vOut.size, inRadius + 1))
	vOut.paste(vShot, (1, 1), rounded_mask((inWidth, vH), inRadius))
	return vOut


def place(inCanvas, inItem, inX, inY, inRadius, inBlur=55):
	"""Pose un élément avec son ombre."""
	shadow(inCanvas, (inX, inY, inX + inItem.width, inY + inItem.height), inRadius, inBlur)
	inCanvas.paste(inItem, (inX, inY), inItem)


def save(inCanvas, inName, inWidth=1600):
	"""Enregistre un visuel à la largeur du README."""
	vH = int(inCanvas.height * inWidth / inCanvas.width)
	inCanvas.resize((inWidth, vH), Image.LANCZOS).save(kOut + inName, optimize=True)
	print(inName, (inWidth, vH))


def raw(inName):
	"""Rendu brut de l'appli."""
	return Image.open(kRaw + inName + '.png')


def tel(inName, inWidth):
	"""Écran du téléphone dans son cadre, barre d'état dessinée."""
	return phone(add_status(raw(inName).convert('RGB')), inWidth)


def three_phones(inNames, inFile):
	"""Trois téléphones côte à côte, celui du milieu un peu plus haut."""
	vCanvas = background(3200, 2050)
	for vIndex, vName in enumerate(inNames):
		vItem = tel(vName, 820)
		place(vCanvas, vItem, 190 + vIndex * 1000, 140 + (0 if vIndex == 1 else 60), 110)
	save(vCanvas, inFile)


os.makedirs(kOut, exist_ok=True)

# 1. Couverture : la fenêtre du PC (téléphone relié), le téléphone posé devant à droite.
vCanvas = background(3200, 2000)
vWin    = window(chrome(raw('pc-devices')), 2100)
place(vCanvas, vWin, 150, 110, 30, 70)
vTel    = tel('telephone-accueil', 720)
place(vCanvas, vTel, 2180, 230, 100, 70)
save(vCanvas, 'vitrine.png')

# 2. La carte de l'historique, descendue en haut d'une autre appli.
vCanvas = background(3200, 1900)
vDoc    = document_window(2700, 1580)
vCard   = raw('pc-carte-historique').convert('RGBA')
vCard   = vCard.crop(vCard.getchannel('A').getbbox())
vDoc.paste(vCard, ((vDoc.width - vCard.width) // 2, 64), vCard)
place(vCanvas, window(vDoc, 2700), 250, 160, 30, 70)
save(vCanvas, 'pc-carte-historique.png')

# 3. Les transferts du PC.
vCanvas = background(3200, 2650)
place(vCanvas, window(chrome(raw('pc-transfers')), 2700), 250, 160, 30, 70)
save(vCanvas, 'pc-transferts.png')

# 4. Les réglages du PC.
vCanvas = background(3200, 2650)
place(vCanvas, window(chrome(raw('pc-settings')), 2700), 250, 160, 30, 70)
save(vCanvas, 'pc-reglages.png')

# 5. Écrans du téléphone : accueil, historique, notifications envoyées au PC.
three_phones(['telephone-accueil', 'telephone-historique', 'telephone-notifications'], 'telephone-ecrans.png')

# 6. Assistant de premier lancement.
three_phones(['telephone-assistant-1', 'telephone-assistant-2', 'telephone-assistant-3'], 'telephone-assistant.png')
