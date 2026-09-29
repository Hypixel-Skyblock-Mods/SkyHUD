package org.hypixelskyblockmods.skyhud.feature.enderchest

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.hypixelskyblockmods.skyhud.gui.VanillaSlotSourceTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class StoragePagePreferenceTest {
    private fun page(timestamp: Long, items: List<ItemStack>) =
        CachedEnderChestPage(StoragePageKey.enderChest(1), 1, items, timestamp)

    @Test
    fun `later API fetch cannot erase a tool from a visited page`() {
        val observed = page(100, listOf(ItemStack(Items.DIAMOND), ItemStack(Items.EMERALD)))
        val api = page(200, listOf(ItemStack.EMPTY, ItemStack(Items.EMERALD)))
        assertSame(observed, preferredStoragePage(observed, api))
    }

    @Test
    fun `API snapshot cannot resurrect items removed from a visited page`() {
        val observed = page(100, listOf(ItemStack.EMPTY))
        val api = page(200, listOf(ItemStack(Items.DIAMOND)))
        assertSame(observed, preferredStoragePage(observed, api))
    }

    @Test
    fun `unvisited pages still use the API cache`() {
        val api = page(200, listOf(ItemStack(Items.DIAMOND)))
        assertSame(api, preferredStoragePage(null, api))
    }

    companion object {
        @BeforeAll @JvmStatic
        fun bootstrap() = VanillaSlotSourceTest.bootstrapMinecraft()
    }
}
