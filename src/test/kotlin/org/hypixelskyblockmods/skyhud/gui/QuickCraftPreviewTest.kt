package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.core.component.DataComponents
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class QuickCraftPreviewTest {
    private fun stack(count: Int) = ItemStack(Items.DIAMOND, count).apply {
        set(DataComponents.MAX_STACK_SIZE, 64)
    }

    private fun slot(index: Int, count: Int = 0, limit: Int = 64): Slot =
        object : Slot(SimpleContainer(if (count == 0) ItemStack.EMPTY else stack(count)), 0, 0, 0) {
            override fun getMaxStackSize(stack: ItemStack) = limit
        }.apply { this.index = index }

    @Test
    fun `left drag previews even distribution and remainder without changing slots`() {
        val carried = stack(64)
        val slots = listOf(slot(9), slot(10), slot(54))
        val preview = QuickCraftPreview.create(carried, slots, 0)
        assertEquals(listOf(21, 21, 21), preview.slots.values.map { it.count })
        assertEquals(1, preview.carried.count)
        assertEquals(64, carried.count)
        assertTrue(slots.all { it.item.isEmpty })
    }

    @Test
    fun `right drag adds one per slot and preserves existing counts`() {
        val slots = listOf(slot(9, 20), slot(54))
        val preview = QuickCraftPreview.create(stack(8), slots, 1)
        assertEquals(21, preview.slots.getValue(9).count)
        assertEquals(1, preview.slots.getValue(54).count)
        assertEquals(6, preview.carried.count)
        assertEquals(20, slots.first().item.count)
    }

    @Test
    fun `full and restricted slots return excess items to the cursor`() {
        val preview = QuickCraftPreview.create(stack(10), listOf(slot(9, 63), slot(10, limit = 2)), 0)
        assertEquals(64, preview.slots.getValue(9).count)
        assertEquals(2, preview.slots.getValue(10).count)
        assertEquals(7, preview.carried.count)
    }

    @Test
    fun `single selected slot previews immediately and empty selection keeps cursor`() {
        assertTrue(QuickCraftPreview.create(stack(8), listOf(slot(9)), 0).carried.isEmpty)
        assertEquals(8, QuickCraftPreview.create(stack(8), emptyList(), 0).carried.count)
    }

    companion object {
        @BeforeAll @JvmStatic
        fun bootstrap() = VanillaSlotSourceTest.bootstrapMinecraft()
    }
}
