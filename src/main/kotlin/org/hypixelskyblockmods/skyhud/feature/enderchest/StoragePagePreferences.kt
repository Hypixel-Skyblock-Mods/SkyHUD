package org.hypixelskyblockmods.skyhud.feature.enderchest

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object StoragePagePreferences {
    private data class SavedPreferences(
        var favorites: MutableList<String> = mutableListOf(),
    )

    private val logger = LoggerFactory.getLogger("SkyHUD/Storage")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("skyhud-storage.json").toFile()
    private val favorites = loadFavorites()

    fun isFavorite(key: StoragePageKey): Boolean = key in favorites

    fun toggleFavorite(key: StoragePageKey): Boolean {
        val favorite = if (key in favorites) {
            favorites -= key
            false
        } else {
            favorites += key
            true
        }
        save()
        return favorite
    }

    fun order(pages: Collection<StoragePageKey>): List<StoragePageKey> = pages.sortedWith(
        compareBy<StoragePageKey> { !isFavorite(it) }.thenBy { it },
    )

    private fun loadFavorites(): MutableSet<StoragePageKey> {
        if (!file.isFile) return mutableSetOf()
        return runCatching {
            val saved = gson.fromJson(file.readText(), SavedPreferences::class.java)
            saved.favorites.mapNotNull(::decode).toMutableSet()
        }.onFailure {
            logger.warn("Could not load storage page favorites", it)
        }.getOrDefault(mutableSetOf())
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val saved = SavedPreferences(favorites.sorted().map(::encode).toMutableList())
            file.writeText(gson.toJson(saved))
        }.onFailure {
            logger.warn("Could not save storage page favorites", it)
        }
    }

    private fun encode(key: StoragePageKey): String = "${key.type.name}:${key.number}"

    private fun decode(value: String): StoragePageKey? {
        val parts = value.split(':', limit = 2)
        val type = parts.getOrNull(0)?.let { runCatching { StoragePageType.valueOf(it) }.getOrNull() } ?: return null
        val number = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (type == StoragePageType.ENDER_CHEST && number !in 1..9) return null
        if (type == StoragePageType.BACKPACK && number !in 1..18) return null
        return StoragePageKey(type, number)
    }
}
