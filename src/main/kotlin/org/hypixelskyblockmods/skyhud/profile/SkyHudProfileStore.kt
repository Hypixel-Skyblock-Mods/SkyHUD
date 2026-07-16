package org.hypixelskyblockmods.skyhud.profile

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import net.fabricmc.loader.api.FabricLoader
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.slf4j.LoggerFactory

object SkyHudProfileStore {
    private val logger = LoggerFactory.getLogger("SkyHUD Profile Store")

    // Keep the established directory so existing profile-scoped HUD previews continue to load.
    private val root: Path
        get() = FabricLoader.getInstance().configDir.resolve("skyhud-item-search")

    fun read(name: String, profile: SkyBlockProfileIdentity): String? = runCatching {
        val file = profileDirectory(root, profile).resolve("$name.json")
        if (Files.isRegularFile(file)) Files.readString(file) else null
    }.getOrElse {
        logger.warn("Could not read $name for ${profile.profileName}", it)
        null
    }

    fun write(name: String, profile: SkyBlockProfileIdentity, contents: String): Boolean = runCatching {
        val directory = profileDirectory(root, profile)
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
        Files.deleteIfExists(profileDirectory(root, profile).resolve("$name.json"))
        true
    }.getOrElse {
        logger.warn("Could not clear $name for ${profile.profileName}", it)
        false
    }

    internal fun profileDirectory(base: Path, profile: SkyBlockProfileIdentity): Path = base
        .resolve(profile.accountUuid.toString())
        .resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(profile.profileName.toByteArray(StandardCharsets.UTF_8)))
}
