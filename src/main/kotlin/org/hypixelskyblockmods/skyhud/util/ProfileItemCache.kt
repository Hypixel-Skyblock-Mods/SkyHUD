package org.hypixelskyblockmods.skyhud.util

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

object ProfileItemCache {
    private val logger = LoggerFactory.getLogger("SkyHUD Cache")
    private val cacheRoot = FabricLoader.getInstance().configDir.resolve("skyhud-cache")

    fun currentProfile(): UUID = Minecraft.getInstance().user.profileId

    fun read(name: String, profile: UUID): String? = runCatching {
        val accountFile = cacheFile(name, profile)
        if (Files.isRegularFile(accountFile)) return@runCatching Files.readString(accountFile)

        val legacyProfile = Minecraft.getInstance().player?.uuid
            ?.takeUnless { it == profile }
            ?: return@runCatching null
        val legacyFile = cacheFile(name, legacyProfile)
        if (Files.isRegularFile(legacyFile)) Files.readString(legacyFile) else null
    }.getOrElse {
        logger.warn("Could not read $name cache for $profile", it)
        null
    }

    fun write(name: String, profile: UUID, contents: String): Boolean =
        runCatching {
            val directory = cacheRoot.resolve(profile.toString())
            Files.createDirectories(directory)
            val file = directory.resolve("$name.json")
            val temporary = directory.resolve("$name.json.tmp")
            Files.writeString(temporary, contents)
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
            true
        }.getOrElse {
            logger.warn("Could not write $name cache for $profile", it)
            false
        }

    fun encode(stack: ItemStack): String = ItemStackSerialization.encode(stack)

    fun decode(encoded: String): ItemStack = ItemStackSerialization.decode(encoded)

    fun stacksMatch(first: List<ItemStack>, second: List<ItemStack>): Boolean =
        first.size == second.size && first.indices.all { ItemStack.matches(first[it], second[it]) }

    private fun cacheFile(name: String, profile: UUID) =
        cacheRoot.resolve(profile.toString()).resolve("$name.json")

}
