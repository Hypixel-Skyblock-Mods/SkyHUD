package org.hypixelskyblockmods.skyhud.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
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

object ItemStackSerialization {
    private val logger = LoggerFactory.getLogger("SkyHUD Item Serialization")
    private const val maxItemBytes = 1_000_000L

    fun encode(stack: ItemStack): String = encode(stack.copy(), registryOps())

    /** Capture registry access on the client thread before encoding detached snapshots on a worker. */
    fun encoder(): (ItemStack) -> String {
        val ops = registryOps()
        return { stack -> encode(stack, ops) }
    }

    internal fun encode(stack: ItemStack, ops: RegistryOps<Tag>): String {
        if (stack.isEmpty) return ""
        // Vanilla's persistent codec limits counts to 99; Hypixel can exceed that.
        val tag = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1))
            .getOrThrow { error -> IllegalStateException("Could not encode ${stack.item}: $error") } as CompoundTag
        val root = CompoundTag()
        root.put("stack", tag)
        root.putInt("count", stack.count)
        return ByteArrayOutputStream().use { output ->
            NbtIo.writeCompressed(root, output)
            Base64.getEncoder().encodeToString(output.toByteArray())
        }
    }

    fun decode(encoded: String): ItemStack = decode(encoded, registryOps())

    internal fun decode(encoded: String, ops: RegistryOps<Tag>): ItemStack {
        if (encoded.isBlank()) return ItemStack.EMPTY
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(maxItemBytes))
            val tag = root.getCompound("stack").orElse(null) ?: return ItemStack.EMPTY
            val stack = ItemStack.CODEC.parse(ops, tag)
                .resultOrPartial { error -> logger.warn("Could not decode item: $error") }
                .orElse(ItemStack.EMPTY)
            root.getInt("count").orElse(null)?.takeIf { it > 0 }?.let { stack.count = it }
            stack
        }.getOrElse {
            logger.warn("Could not decode item stack", it)
            ItemStack.EMPTY
        }
    }

    fun stacksMatch(first: List<ItemStack>, second: List<ItemStack>): Boolean =
        first.size == second.size && first.indices.all { ItemStack.matches(first[it], second[it]) }

    private fun registryOps(): RegistryOps<Tag> {
        val registries = runCatching { Minecraft.getInstance().connection?.registryAccess() }.getOrNull() ?: RegistryAccess.EMPTY
        return RegistryOps.create(NbtOps.INSTANCE, registries)
    }
}
