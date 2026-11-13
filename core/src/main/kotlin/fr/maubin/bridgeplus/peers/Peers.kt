package fr.maubin.bridgeplus.peers

import kotlinx.serialization.json.Json

internal val kPeersJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }	// fichiers des appareils (versions voisines tolérées)
