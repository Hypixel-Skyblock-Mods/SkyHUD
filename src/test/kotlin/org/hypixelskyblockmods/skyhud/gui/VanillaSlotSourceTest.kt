package org.hypixelskyblockmods.skyhud.gui

import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class VanillaSlotSourceTest {
    @Test
    fun `live slots retain their identity and server coordinates`() {
        val container = SimpleContainer(ItemStack(Items.DIAMOND, 3))
        val live = Slot(container, 0, 8, 18).apply { index = 9 }

        val rendered = VanillaSlotSource().resolve(live.item.copy(), live)

        assertSame(live, rendered)
        assertSame(container, rendered.container)
        assertEquals(9, rendered.index)
        assertEquals(8, live.x)
        assertEquals(18, live.y)
    }

    @Test
    fun `cached pages never replace live inventory contents`() {
        val live = Slot(SimpleContainer(ItemStack(Items.DIAMOND, 3)), 0, 8, 18)
        val cached = ItemStack(Items.EMERALD, 5)

        val rendered = VanillaSlotSource().resolve(cached, live)

        assertNotSame(live, rendered)
        assertNotSame(live.container, rendered.container)
        assertSame(cached, rendered.item)
        assertTrue(live.item.`is`(Items.DIAMOND))
        assertEquals(3, live.item.count)
        assertFalse(rendered.mayPlace(ItemStack(Items.STONE)))
    }

    @Test
    fun `filtered filler items stay empty instead of rendering the backing pane`() {
        val live = Slot(SimpleContainer(ItemStack(Items.GLASS_PANE)), 0, 8, 18)

        val rendered = VanillaSlotSource().resolve(ItemStack.EMPTY, live)

        assertTrue(rendered.item.isEmpty)
        assertFalse(live.item.isEmpty)
    }

    @Test
    fun `different item components do not borrow a live slot`() {
        val live = Slot(SimpleContainer(ItemStack(Items.DIAMOND)), 0, 8, 18)
        val cached = live.item.copy().apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Cached item"))
        }

        val rendered = VanillaSlotSource().resolve(cached, live)

        assertNotSame(live, rendered)
        assertSame(cached, rendered.item)
        assertFalse(live.item.has(DataComponents.CUSTOM_NAME))
    }

    @Test
    fun `preview rendering preserves counts beyond container limits`() {
        val cached = ItemStack(Items.DIAMOND, 127)

        val rendered = VanillaSlotSource().resolve(cached, null)

        assertEquals(127, rendered.item.count)
        assertEquals(127, cached.count)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            // These tests need stack identity/components, not a world's data packs.
            listOf(Items.DIAMOND, Items.EMERALD, Items.GLASS_PANE, Items.STONE).forEach {
                it.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY)
            }
        }
    }
}
