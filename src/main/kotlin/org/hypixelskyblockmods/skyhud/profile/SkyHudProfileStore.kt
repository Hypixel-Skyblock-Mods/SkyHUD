package org.hypixelskyblockmods.skyhud.profile

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.Executors
import net.fabricmc.loader.api.FabricLoader
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.util.CoalescingTaskQueue
import org.slf4j.LoggerFactory

object SkyHudProfileStore {
    private val logger = LoggerFactory.getLogger("SkyHUD Profile Store")
    private val writer = CoalescingTaskQueue<Path>(
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "SkyHUD cache writer").apply { isDaemon = true }
        },
    ) { file, exception -> logger.warn("Could not persist SkyHUD cache $file", exception) }

    // Keep the established directory so existing profile-scoped HUD previews continue to load.
    private val root: Path
        get() = FabricLoader.getInstance().configDir.resolve("skyhud-item-search")

    fun read(name: String, profile: SkyBlockProfileIdentity): String? = runCatching {
        // Profile switches can read a cache whose last snapshot is still being saved.
        writer.awaitIdle()
        val file = profileDirectory(root, profile).resolve("$name.json")
        if (Files.isRegularFile(file)) Files.readString(file) else null
    }.getOrElse {
        logger.warn("Could not read $name for ${profile.profileName}", it)
        null
    }

    fun writeAsync(name: String, profile: SkyBlockProfileIdentity, serialize: () -> String) {
        val file = profileDirectory(root, profile).resolve("$name.json")
        writer.submit(file) { write(file, serialize()) }
    }

    private fun write(file: Path, contents: String) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temporary, contents)
        runCatching {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun clear(name: String, profile: SkyBlockProfileIdentity) {
        val file = profileDirectory(root, profile).resolve("$name.json")
        // Keep clears ordered after an in-flight save, so it cannot resurrect a cleared cache.
        writer.submit(file) { Files.deleteIfExists(file) }
    }

    fun awaitPendingWrites() = writer.awaitIdle()

    internal fun profileDirectory(base: Path, profile: SkyBlockProfileIdentity): Path = base
        .resolve(profile.accountUuid.toString())
        .resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(profile.profileName.toByteArray(StandardCharsets.UTF_8)))
}
