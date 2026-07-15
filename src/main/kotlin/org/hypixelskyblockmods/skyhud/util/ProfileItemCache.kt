package org.hypixelskyblockmods.skyhud.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

object ProfileItemCache {
    private val logger = LoggerFactory.getLogger("SkyHUD Cache")
    private val cacheRoot = FabricLoader.getInstance().configDir.resolve("skyhud-cache")
    private const val maxItemBytes = 1_000_000L

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

    fun encode(stack: ItemStack): String = runCatching {
        if (stack.isEmpty) return ""
        val tag = ItemStack.CODEC.encodeStart(registryOps(), stack.copy())
            .resultOrPartial { error -> logger.warn("Could not encode cached item: $error") }
            .orElse(null) as? CompoundTag ?: return ""
        val root = CompoundTag()
        root.put("stack", tag)
        return ByteArrayOutputStream().use { output ->
            NbtIo.writeCompressed(root, output)
            Base64.getEncoder().encodeToString(output.toByteArray())
        }
    }.getOrElse {
        logger.warn("Could not encode cached item stack", it)
        ""
    }

    fun decode(encoded: String): ItemStack {
        if (encoded.isBlank()) return ItemStack.EMPTY
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(maxItemBytes))
            val tag = root.getCompound("stack").orElse(null) ?: return ItemStack.EMPTY
            ItemStack.CODEC.parse(registryOps(), tag)
                .resultOrPartial { error -> logger.warn("Could not decode cached item: $error") }
                .orElse(ItemStack.EMPTY)
        }.getOrElse {
            logger.warn("Could not decode cached item stack", it)
            ItemStack.EMPTY
        }
    }

    fun stacksMatch(first: List<ItemStack>, second: List<ItemStack>): Boolean =
        first.size == second.size && first.indices.all { ItemStack.matches(first[it], second[it]) }

    private fun cacheFile(name: String, profile: UUID) =
        cacheRoot.resolve(profile.toString()).resolve("$name.json")

    private fun registryOps(): RegistryOps<Tag> {
        val registries = Minecraft.getInstance().connection?.registryAccess() ?: RegistryAccess.EMPTY
        return RegistryOps.create(NbtOps.INSTANCE, registries)
    }
}
