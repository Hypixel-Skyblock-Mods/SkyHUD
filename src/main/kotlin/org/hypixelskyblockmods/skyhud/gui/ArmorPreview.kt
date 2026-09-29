package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

internal object ArmorPreview {
    /** Menu-only component overrides must not hide a real armor piece on the mannequin. */
    fun equipment(stack: ItemStack, slot: EquipmentSlot): ItemStack {
        if (stack.isEmpty) return stack
        val equipped = stack.get(DataComponents.EQUIPPABLE)
        if (equipped?.slot() == slot && equipped.assetId().isPresent) return stack
        val default = stack.item.components().get(DataComponents.EQUIPPABLE) ?: return stack
        if (default.slot() != slot || default.assetId().isEmpty) return stack
        return stack.copy().apply { set(DataComponents.EQUIPPABLE, default) }
    }

    // Include the hat/skull and boots, with space for the model's mouse-follow rotation.
    fun scale(width: Int, height: Int, maximum: Int): Int =
        minOf(maximum, ((width - 8).coerceAtLeast(1) / 1.1f).toInt(),
            ((height - 8).coerceAtLeast(1) / 2.2f).toInt()).coerceAtLeast(1)
}
