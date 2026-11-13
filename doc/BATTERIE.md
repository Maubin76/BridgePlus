# BridgePlus — mesurer la consommation de la batterie

BridgePlus tient une connexion permanente avec le PC tant que le téléphone est sur le Wi-Fi de la
maison : le PC envoie un battement toutes les 30 s, le téléphone répond. Le principe produit est
que la consommation sur une journée reste **négligeable**. Cette mesure le vérifie.

## Mesure (une journée normale)

Téléphone branché au PC en USB (débogage USB activé), BridgePlus appairé et relié :

```bash
# 1. Remettre les statistiques à zéro, téléphone chargé, puis le débrancher.
adb shell dumpsys batterystats --reset

# 2. Vivre une journée normale à la maison (au moins 8 h), sans recharger.

# 3. Rebrancher et relever la part de BridgePlus.
adb shell dumpsys batterystats --charged fr.maubin.bridgeplus > bridgeplus-batterie.txt
adb shell dumpsys batterystats --charged | findstr /C:"Estimated power use" /C:"Capacity" /C:"Uid u0a"
```

Dans le fichier, relever :

- la consommation estimée de l'UID de BridgePlus (`Estimated power use`, en mAh) ;
- les **réveils** (`Wakeup alarm`, `wake lock`) et le temps de **radio Wi-Fi** actif ;
- le temps total de la mesure.

Pour une vue graphique : [Battery Historian](https://developer.android.com/topic/performance/power/setup-battery-historian)
avec `adb bugreport bugreport.zip`.

## Résultat

| Mesure | Valeur |
|---|---|
| Téléphone, Android | à remplir |
| Durée | à remplir |
| Consommation de BridgePlus | à remplir (mAh, et % de la batterie) |
| Réveils | à remplir |
| Radio Wi-Fi active | à remplir |

## Si la consommation n'est pas négligeable

Le premier réglage est l'intervalle du battement (`kPingEveryMs`, 30 s) et le délai de mort des
sessions (`kPcDeadAfterMs`, `kPhoneDeadAfterMs`) dans `core/.../link/LinkSession.kt` : passer à
60 s ou 120 s divise d'autant les réveils, au prix d'une coupure détectée plus tard.
