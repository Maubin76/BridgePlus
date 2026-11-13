package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.transfer.DiskSource
import fr.maubin.bridgeplus.transfer.FileSink
import fr.maubin.bridgeplus.transfer.FileSource
import fr.maubin.bridgeplus.transfer.FileTransfers
import fr.maubin.bridgeplus.transfer.FolderSink
import fr.maubin.bridgeplus.transfer.IncomingFile
import fr.maubin.bridgeplus.transfer.Transfer
import fr.maubin.bridgeplus.transfer.TransferState
import fr.maubin.bridgeplus.transfer.safeFileName
import fr.maubin.bridgeplus.transfer.sizeText
import fr.maubin.bridgeplus.transfer.uniqueName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/*
	Transferts de fichiers sans réseau : deux moteurs branchés l'un sur
	l'autre (le téléphone et le PC), avec des morceaux de 64 Ko. Découpage
	et réassemblage, empreinte vérifiée, noms sûrs et sans écrasement,
	espace insuffisant, annulation, coupure : jamais de fichier à moitié
	écrit.
*/
class TransferTest
{
	@get:Rule val fTemp = TemporaryFolder()											// dossiers des deux côtés

	private val fInbox    by lazy { fTemp.newFolder("reception") }					// dossier de réception
	private val fFinished = LinkedBlockingQueue<Transfer>()							// transferts terminés (côté destinataire et expéditeur)
	private var fCorrupt  = false													// abîmer un octet en route
	private var fDrop     = false													// couper la connexion (morceaux perdus)
	private var fSender   : FileTransfers? = null									// moteur de l'expéditeur
	private var fReceiver : FileTransfers? = null									// moteur du destinataire

	/*
		Branche deux moteurs l'un sur l'autre.
		inSink : dossier de réception du destinataire
	*/
	private fun wire(inSink: FileSink = FolderSink { fInbox })
	{
		fSender   = FileTransfers(
			fSend       = { _, vMessage -> fReceiver!!.onMessage("expediteur", "Pixel", vMessage); true },
			fSendChunk  = { _, vChunk ->
				if (fDrop) false
				else
				{
					if (fCorrupt) vChunk[vChunk.size - 1] = (vChunk[vChunk.size - 1] + 1).toByte()
					fReceiver!!.onChunk("expediteur", vChunk); true
				}
			},
			fSink       = FolderSink { fTemp.newFolder() },
			fOnFinished = { fFinished.add(it) },
			fChunkBytes = 64 * 1024
		)
		fReceiver = FileTransfers(
			fSend       = { _, vMessage -> fSender!!.onMessage("destinataire", "Bureau", vMessage); true },
			fSendChunk  = { _, _ -> true },
			fSink       = inSink,
			fOnFinished = { fFinished.add(it) },
			fChunkBytes = 64 * 1024
		)
	}

	/*
		Fichier de test au contenu tiré au hasard.
		inName  : nom
		inBytes : taille
	*/
	private fun sample(
		inName  : String,
		inBytes : Int
	): File = fTemp.newFolder().resolve(inName).apply { writeBytes(Random(inBytes).nextBytes(inBytes)) }

	/*
		Attend les deux fins d'un transfert (destinataire et expéditeur).
	*/
	private fun bothEnds(): List<Transfer> = List(2) { fFinished.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("transfert jamais terminé") }

	/*
		Fichier découpé en morceaux (dernier incomplet), réassemblé à
		l'identique sous son nom ; aucun fichier provisoire ne reste.
	*/
	@Test
	fun roundTrip()
	{
		wire()
		val vFile = sample("photo.jpg", 5 * 64 * 1024 + 123)										// fichier envoyé
		fSender!!.send("destinataire", "Bureau", listOf(DiskSource(vFile)))
		val vEnds = bothEnds()																		// fins
		assertTrue(vEnds.all { it.state == TransferState.Done })
		assertArrayEquals(vFile.readBytes(), File(fInbox, "photo.jpg").readBytes())
		assertEquals(listOf("photo.jpg"), fInbox.list()!!.toList())
		assertEquals(File(fInbox, "photo.jpg").absolutePath, vEnds.first { !it.outgoing }.location)
	}

	/*
		Fichier vide : accepté, rangé.
	*/
	@Test
	fun emptyFile()
	{
		wire()
		fSender!!.send("destinataire", "Bureau", listOf(DiskSource(sample("vide.txt", 0))))
		assertTrue(bothEnds().all { it.state == TransferState.Done })
		assertEquals(0L, File(fInbox, "vide.txt").length())
	}

	/*
		Octet abîmé en route : empreinte différente, fichier refusé et effacé
		des deux côtés.
	*/
	@Test
	fun corruptedIsRejected()
	{
		wire()
		fCorrupt = true
		fSender!!.send("destinataire", "Bureau", listOf(DiskSource(sample("abime.bin", 200_000))))
		val vEnds = bothEnds()																		// fins
		assertTrue(vEnds.all { it.state == TransferState.Failed })
		assertTrue(vEnds.first().reason!!.contains("Empreinte"))
		assertEquals(0, fInbox.list()!!.size)
	}

	/*
		Nom déjà pris : « (2) », puis « (3) » ; jamais d'écrasement.
	*/
	@Test
	fun conflictsNeverOverwrite()
	{
		wire()
		File(fInbox.apply { mkdirs() }, "photo.jpg").writeText("ancienne")
		repeat(2)
		{
			fSender!!.send("destinataire", "Bureau", listOf(DiskSource(sample("photo.jpg", 1000 + it))))
			bothEnds()
		}
		assertEquals("ancienne", File(fInbox, "photo.jpg").readText())
		assertEquals(setOf("photo.jpg", "photo (2).jpg", "photo (3).jpg"), fInbox.list()!!.toSet())
	}

	/*
		Espace insuffisant : refusé avant le premier morceau, avec la raison.
	*/
	@Test
	fun notEnoughSpace()
	{
		val vSmall = object : FileSink
		{
			override fun freeBytes(): Long = 10L * 1024 * 1024
			override fun create(inName: String, inSize: Long, inMime: String): IncomingFile = throw AssertionError("ne doit pas être créé")
		}																							// disque presque plein
		wire(vSmall)
		fSender!!.send("destinataire", "Bureau", listOf(DiskSource(sample("gros.bin", 1000))))
		val vEnds = bothEnds()																		// fins
		assertTrue(vEnds.all { it.state == TransferState.Failed })
		assertTrue(vEnds.last().reason!!.startsWith("Espace insuffisant"))
	}

	/*
		Annulation par l'expéditeur en cours de route, puis coupure de la
		connexion : rien ne reste chez le destinataire.
	*/
	@Test
	fun cancelAndDisconnectLeaveNothing()
	{
		wire()
		val vGate  = LinkedBlockingQueue<Unit>()													// lecture au pas à pas
		val vSlow  = object : FileSource
		{
			override val name = "lent.bin"
			override val size = 10L * 64 * 1024
			override val mime = ""
			override fun open(): InputStream = object : InputStream()
			{
				override fun read(): Int = 0
				override fun read(inBuffer: ByteArray, inOffset: Int, inLength: Int): Int { vGate.poll(5, TimeUnit.SECONDS); return inLength.coerceAtMost(64 * 1024) }
			}
		}																							// fichier qui avance à la demande
		val vOne   = fSender!!.send("destinataire", "Bureau", listOf(vSlow)).single()					// transfert
		repeat(3) { vGate.add(Unit) }
		Thread.sleep(300)
		fSender!!.cancel(vOne.id)
		repeat(3) { vGate.add(Unit) }
		val vEnds  = bothEnds()																		// fins
		assertTrue(vEnds.all { it.state == TransferState.Cancelled })
		assertEquals(0, fInbox.listFiles()!!.size)

		val vTwo   = fSender!!.send("destinataire", "Bureau", listOf(vSlow)).single()				// second transfert
		repeat(2) { vGate.add(Unit) }
		Thread.sleep(300)
		fDrop = true
		fReceiver!!.onDisconnected("expediteur")
		fSender!!.onDisconnected("destinataire")
		repeat(10) { vGate.add(Unit) }
		val vCut   = bothEnds()																		// fins
		assertTrue(vCut.all { it.state == TransferState.Failed && it.reason == "Connexion coupée" })
		assertEquals(vTwo.id, vCut.first().id)
		assertEquals(0, fInbox.listFiles()!!.size)
	}

	/*
		Noms envoyés par l'autre appareil : aucun chemin, aucun caractère
		interdit, aucun nom réservé de Windows.
	*/
	@Test
	fun safeNames()
	{
		assertEquals("x.txt", safeFileName("../../Windows/x.txt"))
		assertEquals("evil.exe", safeFileName("..\\..\\evil.exe"))
		assertEquals("a_b_c.txt", safeFileName("a<b>c.txt"))
		assertEquals("_CON.txt", safeFileName("CON.txt"))
		assertEquals("fichier", safeFileName(".."))
		assertEquals("fichier", safeFileName(""))
		assertEquals("rapport", safeFileName("rapport. . "))
		val vLong = safeFileName("a".repeat(300) + ".pdf")											// nom trop long
		assertTrue(vLong.length <= 180 && vLong.endsWith(".pdf"))
	}

	/*
		Noms libres et tailles lisibles.
	*/
	@Test
	fun namesAndSizes()
	{
		val vTaken = setOf("photo.jpg", "photo (2).jpg", "notes", "archive.tar.gz")				// noms pris
		assertEquals("photo (3).jpg", uniqueName("photo.jpg") { it in vTaken })
		assertEquals("notes (2)", uniqueName("notes") { it in vTaken })
		assertEquals("archive.tar (2).gz", uniqueName("archive.tar.gz") { it in vTaken })
		assertEquals("libre.txt", uniqueName("libre.txt") { it in vTaken })
		assertEquals("812 o", sizeText(812))
		assertEquals("4,2 Ko", sizeText(4300))
		assertEquals("50,0 Mo", sizeText(50L * 1024 * 1024))
		assertFalse(sizeText(3L * 1024 * 1024 * 1024).isEmpty())
	}

	/*
		Messages ignorés par le moteur : ceux qui ne concernent pas les fichiers.
	*/
	@Test
	fun ignoresOtherMessages()
	{
		wire()
		assertFalse(fReceiver!!.onMessage("expediteur", "Pixel", LinkMessage.Ping(1)))
	}
}
