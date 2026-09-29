package org.hypixelskyblockmods.skyhud.util

import java.io.ByteArrayOutputStream
import java.util.Base64
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import org.hypixelskyblockmods.skyhud.gui.VanillaSlotSourceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ItemStackSerializationTest {
    private val ops = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.EMPTY)

    @Test
    fun `drill data lore and item components survive a saved cache round trip`() {
        val stack = ItemStack(Items.PRISMARINE_SHARD).apply {
            val attributes = CompoundTag().apply {
                putString("id", "TITANIUM_DRILL_4")
                putInt("drill_fuel", 3000)
                putString("drill_engine", "MITHRIL_PLATED_DRILL_ENGINE")
                put("gems", CompoundTag().apply { putString("JADE_0", "PERFECT") })
            }
            set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { put("ExtraAttributes", attributes) }))
            set(DataComponents.CUSTOM_NAME, Component.literal("Titanium Drill"))
            set(DataComponents.LORE, ItemLore(listOf(Component.literal("Fuel: 3,000/3,000"))))
            set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        }
        val decoded = ItemStackSerialization.decode(ItemStackSerialization.encode(stack, ops), ops)
        assertTrue(ItemStack.matches(stack, decoded))
    }

    @Test
    fun `Hypixel stacks larger than vanilla codec limit keep their exact count`() {
        val stack = ItemStack(Items.PRISMARINE_SHARD, 127)
        val decoded = ItemStackSerialization.decode(ItemStackSerialization.encode(stack, ops), ops)
        assertEquals(127, decoded.count)
        assertTrue(ItemStack.matches(stack, decoded))
    }

    @Test
    fun `old cache format without separate count remains readable`() {
        val stack = ItemStack(Items.PRISMARINE_SHARD, 3)
        val root = CompoundTag().apply { put("stack", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()) }
        val bytes = ByteArrayOutputStream().also { NbtIo.writeCompressed(root, it) }.toByteArray()
        assertTrue(ItemStack.matches(stack, ItemStackSerialization.decode(Base64.getEncoder().encodeToString(bytes), ops)))
    }

    @Test
    fun `failed component encoding aborts the snapshot instead of silently omitting the item`() {
        val stack = ItemStack(Items.PRISMARINE_SHARD).apply { set(DataComponents.MAX_STACK_SIZE, 128) }
        assertThrows(IllegalStateException::class.java) { ItemStackSerialization.encode(stack, ops) }
    }

    companion object {
        @BeforeAll @JvmStatic
        fun bootstrap() {
            VanillaSlotSourceTest.bootstrapMinecraft()
            Items.PRISMARINE_SHARD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY)
        }
    }
}
