#!/usr/bin/env bash
# Polices Satoshi (Fontshare, licence ITF Free Font) : leur licence interdit de les publier
# dans un dépôt ; elles sont téléchargées depuis Fontshare avant chaque construction (CI, ou
# poste neuf) et rangées là où Compose les attend. Sans effet si elles sont déjà là.
set -euo pipefail
vDir="ui/src/commonMain/composeResources/font"
vWeights="Light Regular Medium Bold"
vMissing=0
for vWeight in $vWeights; do
	vName="satoshi_$(echo "$vWeight" | tr "[:upper:]" "[:lower:]").ttf"
	[ -f "$vDir/$vName" ] || vMissing=1
done
if [ "$vMissing" = 0 ]; then echo "Polices Satoshi déjà présentes."; exit 0; fi
vTemp=$(mktemp -d)
trap 'rm -rf "$vTemp"' EXIT
curl -fsSL --retry 3 -o "$vTemp/satoshi.zip" "https://api.fontshare.com/v2/fonts/download/satoshi"
unzip -q "$vTemp/satoshi.zip" -d "$vTemp/satoshi"
mkdir -p "$vDir"
for vWeight in $vWeights; do
	vFile=$(find "$vTemp/satoshi" -type f -name "Satoshi-$vWeight.ttf" | head -1)
	[ -n "$vFile" ] || { echo "Satoshi-$vWeight.ttf introuvable dans le zip de Fontshare" >&2; exit 1; }
	cp "$vFile" "$vDir/satoshi_$(echo "$vWeight" | tr "[:upper:]" "[:lower:]").ttf"
done
echo "Polices Satoshi installées dans $vDir."
