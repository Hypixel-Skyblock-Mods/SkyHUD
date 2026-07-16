package org.hypixelskyblockmods.skyhud.feature.enderchest

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.fabricmc.loader.api.FabricLoader
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.slf4j.LoggerFactory

data class StoragePageCatalogSnapshot(
    val overviewDiscovered: Boolean,
    val availablePages: Set<StoragePageKey>,
)

object StoragePageCatalog {
    private data class SavedProfile(
        var overviewDiscovered: Boolean = false,
        var available: MutableList<String> = mutableListOf(),
    )

    private data class SavedAccount(
        var profiles: MutableMap<String, SavedProfile> = mutableMapOf(),
    )

    private data class SavedCatalog(
        var version: Int = 1,
        var accounts: MutableMap<String, SavedAccount> = mutableMapOf(),
    )

    private val logger = LoggerFactory.getLogger("SkyHUD/StorageCatalog")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("skyhud-storage-catalog.json")
    private var catalog = load()
    private var lastSavedJson = gson.toJson(catalog)

    fun snapshot(profile: SkyBlockProfileIdentity): StoragePageCatalogSnapshot {
        val saved = profile(profile) ?: return StoragePageCatalogSnapshot(false, emptySet())
        return StoragePageCatalogSnapshot(
            overviewDiscovered = saved.overviewDiscovered,
            availablePages = saved.available.mapNotNull(::decode).toSet(),
        )
    }

    fun remember(profile: SkyBlockProfileIdentity, pages: Collection<StoragePageKey>) {
        val saved = profile(profile, create = true) ?: return
        val available = saved.available.mapNotNull(::decode).toMutableSet()
        if (!available.addAll(pages)) return
        saved.available = available.sorted().map(::encode).toMutableList()
        save()
    }

    fun replaceOverview(profile: SkyBlockProfileIdentity, pages: Collection<StoragePageKey>) {
        val saved = profile(profile, create = true) ?: return
        val encoded = pages.toSortedSet().map(::encode)
        if (saved.overviewDiscovered && saved.available == encoded) return
        saved.overviewDiscovered = true
        saved.available = encoded.toMutableList()
        save()
    }

    private fun profile(
        identity: SkyBlockProfileIdentity,
        create: Boolean = false,
    ): SavedProfile? {
        val accountKey = identity.accountUuid.toString()
        val account = if (create) {
            catalog.accounts.getOrPut(accountKey, ::SavedAccount)
        } else {
            catalog.accounts[accountKey]
        } ?: return null
        return if (create) {
            account.profiles.getOrPut(identity.profileName, ::SavedProfile)
        } else {
            account.profiles[identity.profileName]
        }
    }

    private fun load(): SavedCatalog {
        if (!Files.isRegularFile(file)) return SavedCatalog()
        return runCatching {
            gson.fromJson(Files.readString(file), SavedCatalog::class.java) ?: SavedCatalog()
        }.onFailure {
            logger.warn("Could not load storage page catalog", it)
        }.getOrDefault(SavedCatalog())
    }

    private fun save() {
        val json = gson.toJson(catalog)
        if (json == lastSavedJson) return
        runCatching {
            Files.createDirectories(file.parent)
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temporary, json)
            runCatching {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
            lastSavedJson = json
        }.onFailure {
            logger.warn("Could not save storage page catalog", it)
        }
    }

    private fun encode(key: StoragePageKey): String = "${key.type.name}:${key.number}"

    private fun decode(value: String): StoragePageKey? {
        val parts = value.split(':', limit = 2)
        val type = parts.getOrNull(0)?.let { runCatching { StoragePageType.valueOf(it) }.getOrNull() } ?: return null
        val number = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val validRange = when (type) {
            StoragePageType.ENDER_CHEST -> 1..9
            StoragePageType.BACKPACK -> 1..18
        }
        return StoragePageKey(type, number).takeIf { number in validRange }
    }
}
