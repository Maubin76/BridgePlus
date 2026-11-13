package fr.maubin.pontplus.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
	Écrit un fichier sans risque de le perdre : fichier provisoire du même
	dossier, puis remplacement.
	inFile  : fichier
	inBytes : contenu
*/
fun writeAtomic(
	inFile  : File,
	inBytes : ByteArray
)
{
	inFile.absoluteFile.parentFile?.mkdirs()
	val vTemp = File(inFile.absoluteFile.parentFile, inFile.name + ".tmp")				// écriture provisoire
	vTemp.writeBytes(inBytes)
	Files.move(vTemp.toPath(), inFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
