# Hôte des notifications de BridgePlus, gardé ouvert par l'appli PC (WinToast.kt).
# Il lit des lignes sur l'entrée standard :
#   S|<étiquette>|<XML en base64>   affiche une notification (étiquette vide : sans suivi)
#   R|<étiquette>                   retire une notification (aussi du centre de notifications)
#   P                               battement : relire l'historique (envoyé chaque seconde par
#                                   l'appli tant qu'une notification est suivie)
# et écrit sur la sortie standard :
#   D|<étiquette>                   notification fermée sur le PC (croix, clic, effacée du centre)
# Windows PowerShell 5.1 ne peut pas s'abonner aux événements de Windows Runtime : une
# notification suivie qui disparaît de l'historique sans avoir été retirée par l'appli a
# donc été fermée par l'utilisateur. La lecture de l'entrée standard bloque (même
# « asynchrone ») : c'est le battement de l'appli qui fait relire l'historique.
$ErrorActionPreference = 'SilentlyContinue'
[void][Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]
[void][Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime]
$id       = '{APPID}'
$group    = 'telephone'
$notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($id)
$history  = [Windows.UI.Notifications.ToastNotificationManager]::History
$shown    = @{}
while (($line = [Console]::In.ReadLine()) -ne $null) {
	$part = $line.Split('|')
	if ($part[0] -eq 'S') {
		try {
			$xml = New-Object Windows.Data.Xml.Dom.XmlDocument
			$xml.LoadXml([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($part[2])))
			$toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
			if ($part[1]) {
				$toast.Tag   = $part[1]
				$toast.Group = $group
				$shown[$part[1]] = [DateTime]::Now
			}
			$notifier.Show($toast)
		} catch {}
	} elseif ($part[0] -eq 'R') {
		$shown.Remove($part[1])
		try { $history.Remove($part[1], $group, $id) } catch {}
	}
	if ($shown.Count -gt 0) {
		$present = @{}
		try { foreach ($toast in $history.GetHistory($id)) { if ($toast.Tag) { $present[$toast.Tag] = 1 } } } catch { continue }
		foreach ($tag in @($shown.Keys)) {
			if (-not $present.ContainsKey($tag) -and ([DateTime]::Now - $shown[$tag]).TotalSeconds -gt 3) {
				[Console]::Out.WriteLine('D|' + $tag)
				[Console]::Out.Flush()
				$shown.Remove($tag)
			}
		}
	}
}
