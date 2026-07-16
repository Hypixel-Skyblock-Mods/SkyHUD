package org.hypixelskyblockmods.skyhud.feature.itemsearch

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import net.fabricmc.loader.api.FabricLoader
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.slf4j.LoggerFactory

object SkyBlockProfileStore {
    private val logger = LoggerFactory.getLogger("SkyHUD Profile Store")
    private val root = FabricLoader.getInstance().configDir.resolve("skyhud-item-search")

    fun read(name: String, profile: SkyBlockProfileIdentity): String? = runCatching {
        val file = profileDirectory(profile).resolve("$name.json")
        if (Files.isRegularFile(file)) Files.readString(file) else null
    }.getOrElse {
        logger.warn("Could not read $name for ${profile.profileName}", it)
        null
    }

    fun write(name: String, profile: SkyBlockProfileIdentity, contents: String): Boolean = runCatching {
        val directory = profileDirectory(profile)
        Files.createDirectories(directory)
        val file = directory.resolve("$name.json")
        val temporary = directory.resolve("$name.json.tmp")
        Files.writeString(temporary, contents)
        runCatching {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
        true
    }.getOrElse {
        logger.warn("Could not write $name for ${profile.profileName}", it)
        false
    }

    fun clear(name: String, profile: SkyBlockProfileIdentity): Boolean = runCatching {
        Files.deleteIfExists(profileDirectory(profile).resolve("$name.json"))
        true
    }.getOrElse {
        logger.warn("Could not clear $name for ${profile.profileName}", it)
        false
    }

    fun clearSearchObservations(profile: SkyBlockProfileIdentity) {
        listOf("inventory", "sack-of-sacks", "island-chests", "loadouts", "wardrobe", "equipment").forEach { clear(it, profile) }
    }

    private fun profileDirectory(profile: SkyBlockProfileIdentity) = root
        .resolve(profile.accountUuid.toString())
        .resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(profile.profileName.toByteArray(StandardCharsets.UTF_8)))
}
