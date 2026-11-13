package fr.maubin.bridgeplus

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import fr.maubin.bridgeplus.transfer.FileSink
import fr.maubin.bridgeplus.transfer.FileSource
import fr.maubin.bridgeplus.transfer.IncomingFile
import fr.maubin.bridgeplus.transfer.uniqueName
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

const val kReceiveFolder = "BridgePlus"												// sous-dossier de Download où arrivent les fichiers
private val kRelativePath = "${Environment.DIRECTORY_DOWNLOADS}/$kReceiveFolder/"	// chemin relatif de MediaStore

/*
	Dossier de réception du téléphone : Download/BridgePlus/, par MediaStore
	(les photos apparaissent dans la galerie, sans autorisation de stockage).
	Le fichier est créé « en attente » (IS_PENDING) : invisible des autres
	applis jusqu'à sa vérification, puis rendu visible d'un coup ; abandonné,
	il est effacé. Nom déjà pris : « (2) », « (3) »… jamais d'écrasement.
	fContext : contexte
*/
class MediaStoreSink(private val fContext: Context) : FileSink
{
	override fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes

	override fun create(
		inName : String,
		inSize : Long,
		inMime : String
	): IncomingFile
	{
		val vResolver = fContext.contentResolver													// contenus
		val vName     = uniqueName(inName, existingNames()::contains)								// nom libre
		val vValues   = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, vName)
			put(MediaStore.MediaColumns.MIME_TYPE, inMime.ifEmpty { mimeOf(vName) })
			put(MediaStore.MediaColumns.RELATIVE_PATH, kRelativePath)
			put(MediaStore.MediaColumns.IS_PENDING, 1)
		}																						// fiche du fichier
		val vUri      = vResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, vValues) ?: throw IOException("MediaStore refuse le fichier")	// fichier créé
		val vOut      = BufferedOutputStream(vResolver.openOutputStream(vUri) ?: run { vResolver.delete(vUri, null, null); throw IOException("écriture impossible") }, 256 * 1024)	// écriture
		return object : IncomingFile
		{
			override val output: OutputStream = vOut

			override fun commit(): String
			{
				vOut.close()
				vResolver.update(vUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
				return vUri.toString()
			}

			override fun abort()
			{
				runCatching { vOut.close() }
				runCatching { vResolver.delete(vUri, null, null) }
			}
		}
	}

	/*
		Noms déjà présents dans Download/BridgePlus/ (fichiers de BridgePlus).
	*/
	private fun existingNames(): Set<String>
	{
		val vNames = mutableSetOf<String>()														// noms trouvés
		fContext.contentResolver.query(
			MediaStore.Downloads.EXTERNAL_CONTENT_URI,
			arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
			"${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
			arrayOf(kRelativePath),
			null
		)?.use { vCursor -> while (vCursor.moveToNext()) vNames += vCursor.getString(0) }
		return vNames
	}
}

/*
	Fichier partagé par une autre appli (menu de partage, sélecteur de
	fichiers), dont le contenu est ouvert tout de suite : l'autorisation de
	lecture accordée à l'activité peut cesser quand elle se ferme, le flux
	ouvert, lui, reste valable.
	name   : nom
	size   : taille (octets)
	mime   : type du contenu
	stream : contenu, déjà ouvert
	uri    : adresse du fichier (historique)
*/
class OpenedSource(
	override val name  : String,
	override val size  : Long,
	override val mime  : String,
	private val stream : InputStream,
	val uri            : String
) : FileSource
{
	override fun open(): InputStream = stream
}

/*
	Ouvre les fichiers partagés (adresses content://) : nom, taille et
	contenu. Un fichier dont la taille est inconnue ou illisible est écarté.
	inContext : contexte
	inUris    : adresses
*/
fun openSources(
	inContext : Context,
	inUris    : List<Uri>
): List<OpenedSource> =
	inUris.mapNotNull { vUri ->
		runCatching {
			val vResolver = inContext.contentResolver												// contenus
			var vName     = vUri.lastPathSegment ?: "fichier"										// nom
			var vSize     = -1L																		// taille
			vResolver.query(vUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { vCursor ->
				if (vCursor.moveToFirst())
				{
					vCursor.getString(0)?.let { vName = it }
					if (!vCursor.isNull(1)) vSize = vCursor.getLong(1)
				}
			}
			if (vSize < 0) vSize = vResolver.openAssetFileDescriptor(vUri, "r")?.use { it.length } ?: -1
			if (vSize < 0) return@runCatching null
			val vStream   = vResolver.openInputStream(vUri) ?: return@runCatching null			// contenu
			OpenedSource(vName, vSize, vResolver.getType(vUri).orEmpty(), vStream, vUri.toString())
		}.getOrNull()
	}

/*
	Type d'un fichier d'après son extension ; « application/octet-stream » à
	défaut.
	inName : nom
*/
fun mimeOf(inName: String): String =
	MimeTypeMap.getSingleton().getMimeTypeFromExtension(inName.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

/*
	Ouvre un fichier (adresse content://) avec l'appli associée ; faux s'il
	n'y en a pas, ou s'il n'existe plus.
	inContext  : contexte
	inLocation : adresse
	inName     : nom (type du contenu)
*/
fun openReceived(
	inContext  : Context,
	inLocation : String,
	inName     : String
): Boolean =
	runCatching {
		inContext.startActivity(
			Intent(Intent.ACTION_VIEW)
				.setDataAndType(inLocation.toUri(), mimeOf(inName))
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
		)
		true
	}.getOrDefault(false)

/*
	Ouvre le dossier des téléchargements (où se trouve Download/BridgePlus).
	inContext : contexte
*/
fun openDownloads(inContext: Context): Boolean =
	runCatching { inContext.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
