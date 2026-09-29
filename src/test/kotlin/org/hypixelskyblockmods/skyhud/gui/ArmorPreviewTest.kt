package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.equipment.EquipmentAssets
import net.minecraft.world.item.equipment.Equippable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ArmorPreviewTest {
    @Test
    fun `boots stripped of equippable metadata recover only on the preview copy`() {
        val boots = ItemStack(Items.LEATHER_BOOTS).apply {
            remove(DataComponents.EQUIPPABLE)
            set(DataComponents.CUSTOM_NAME, Component.literal("Boots"))
        }
        val preview = ArmorPreview.equipment(boots, EquipmentSlot.FEET)
        assertNotSame(boots, preview)
        assertEquals(EquipmentSlot.FEET, preview.get(DataComponents.EQUIPPABLE)?.slot())
        assertEquals(EquipmentAssets.LEATHER, preview.get(DataComponents.EQUIPPABLE)?.assetId()?.orElse(null))
        assertEquals(boots.hoverName, preview.hoverName)
        assertFalse(boots.has(DataComponents.EQUIPPABLE))
    }

    @Test
    fun `helmet with menu-only equipment metadata recovers its armor model`() {
        val helmet = ItemStack(Items.IRON_HELMET).apply {
            set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build())
        }
        assertEquals(EquipmentAssets.IRON,
            ArmorPreview.equipment(helmet, EquipmentSlot.HEAD).get(DataComponents.EQUIPPABLE)?.assetId()?.orElse(null))
        assertTrue(helmet.get(DataComponents.EQUIPPABLE)!!.assetId().isEmpty)
    }

    @Test
    fun `renderable custom armor and skull items retain all their components`() {
        val helmet = ItemStack(Items.IRON_HELMET).apply {
            set(DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.HEAD).setAsset(EquipmentAssets.DIAMOND).build())
        }
        assertSame(helmet, ArmorPreview.equipment(helmet, EquipmentSlot.HEAD))
        val head = ItemStack(Items.PLAYER_HEAD)
        assertSame(head, ArmorPreview.equipment(head, EquipmentSlot.HEAD))
        assertSame(helmet, ArmorPreview.equipment(helmet, EquipmentSlot.FEET))
    }

    @Test
    fun `small viewports fit a complete armored model without a minimum scale overflow`() {
        val scale = ArmorPreview.scale(30, 48, 46)
        assertTrue(scale * 2.2f <= 40)
        assertTrue(scale * 1.1f <= 22)
        assertEquals(46, ArmorPreview.scale(100, 150, 46))
    }

    companion object {
        @BeforeAll @JvmStatic
        fun bootstrap() {
            VanillaSlotSourceTest.bootstrapMinecraft()
            listOf(Items.LEATHER_BOOTS to (EquipmentSlot.FEET to EquipmentAssets.LEATHER),
                Items.IRON_HELMET to (EquipmentSlot.HEAD to EquipmentAssets.IRON)).forEach { (item, armor) ->
                item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.EQUIPPABLE, Equippable.builder(armor.first).setAsset(armor.second).build()).build())
            }
            Items.PLAYER_HEAD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY)
        }
    }
}
