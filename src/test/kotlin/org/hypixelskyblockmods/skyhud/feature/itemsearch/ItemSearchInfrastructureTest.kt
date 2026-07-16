package org.hypixelskyblockmods.skyhud.feature.itemsearch

import com.mojang.blaze3d.platform.InputConstants
import java.nio.file.Files
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import org.hypixelskyblockmods.skyhud.config.SkyHudConfig
import org.hypixelskyblockmods.skyhud.feature.enderchest.StoragePageType
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.SkyBlockProfileIdentity
import org.hypixelskyblockmods.skyhud.integration.skyblockapi.storagePageKeyFromApiIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemSearchInfrastructureTest {
    @Test
    fun `item search is a top level config section defaulting to O`() {
        assertEquals(InputConstants.KEY_O, SkyHudConfig().itemSearch.keybind)
    }

    @Test
    fun `storage api indexes convert to one based validated pages`() {
        assertEquals(1, storagePageKeyFromApiIndex(StoragePageType.ENDER_CHEST, 0)?.number)
        assertEquals(9, storagePageKeyFromApiIndex(StoragePageType.ENDER_CHEST, 8)?.number)
        assertNull(storagePageKeyFromApiIndex(StoragePageType.ENDER_CHEST, -1))
        assertNull(storagePageKeyFromApiIndex(StoragePageType.ENDER_CHEST, 9))
        assertEquals(18, storagePageKeyFromApiIndex(StoragePageType.BACKPACK, 17)?.number)
        assertNull(storagePageKeyFromApiIndex(StoragePageType.BACKPACK, 18))
    }

    @Test
    fun `profile paths isolate exact profile names and clearing is scoped`() {
        val root = Files.createTempDirectory("skyhud-search-test")
        val account = UUID.randomUUID()
        val apple = SkyBlockProfileIdentity(account, "Apple", null)
        val grapes = SkyBlockProfileIdentity(account, "Grapes/Δ", null)

        assertNotEquals(SkyBlockProfileStore.profileDirectory(root, apple), SkyBlockProfileStore.profileDirectory(root, grapes))
        assertTrue(SkyBlockProfileStore.writeToRoot(root, "inventory", apple, "apple"))
        assertTrue(SkyBlockProfileStore.writeToRoot(root, "inventory", grapes, "grapes"))
        assertTrue(SkyBlockProfileStore.clearFromRoot(root, "inventory", apple))
        assertNull(SkyBlockProfileStore.readFromRoot(root, "inventory", apple))
        assertEquals("grapes", SkyBlockProfileStore.readFromRoot(root, "inventory", grapes))
        assertFalse(Files.exists(SkyBlockProfileStore.profileDirectory(root, grapes).resolve("inventory.json.tmp")))
    }

    @Test
    fun `double chest positions canonicalize and either half invalidates container`() {
        val left = BlockPos(10, 64, 20)
        val right = BlockPos(11, 64, 20)
        assertEquals(listOf(left, right), IslandChestGeometry.canonical(listOf(right, left, right)))

        val containers = mapOf("double" to listOf(left, right), "other" to listOf(BlockPos(30, 64, 30)))
        assertEquals(setOf("double"), IslandChestGeometry.containersRemovedByBreak(containers, right))
        assertTrue(IslandChestGeometry.containersRemovedByBreak(containers, BlockPos.ZERO).isEmpty())
    }

    @Test
    fun `routing enforces realms and island warp preference`() {
        val normalInventory = searchable(ItemNavigationAction.Inventory(InventoryRealm.NORMAL, 0), ItemSourceId.INVENTORY)
        val riftStorage = searchable(
            ItemNavigationAction.Storage(org.hypixelskyblockmods.skyhud.feature.enderchest.StoragePageKey.enderChest(1), 0, rift = true),
            ItemSourceId.RIFT,
        )
        val island = searchable(ItemNavigationAction.IslandChest(listOf(BlockPos.ZERO)), ItemSourceId.ISLAND_CHESTS)

        assertEquals(ItemSearchRoute.INVENTORY, itemSearchRoute(normalInventory, InventoryRealm.NORMAL, false, false))
        assertEquals(ItemSearchRoute.WRONG_REALM, itemSearchRoute(normalInventory, InventoryRealm.RIFT, false, false))
        assertEquals(ItemSearchRoute.WRONG_REALM, itemSearchRoute(riftStorage, InventoryRealm.NORMAL, false, false))
        assertEquals(ItemSearchRoute.STORAGE, itemSearchRoute(riftStorage, InventoryRealm.RIFT, false, false))
        assertEquals(ItemSearchRoute.ISLAND_HIGHLIGHT, itemSearchRoute(island, InventoryRealm.NORMAL, true, false))
        assertEquals(ItemSearchRoute.INFORMATIONAL, itemSearchRoute(island, InventoryRealm.NORMAL, false, false))
        assertEquals(ItemSearchRoute.ISLAND_WARP, itemSearchRoute(island, InventoryRealm.NORMAL, false, true))
    }

    private fun searchable(action: ItemNavigationAction, source: ItemSourceId): SearchableItem {
        val stack = ItemStack.EMPTY
        return SearchableItem(
            stack,
            1,
            source,
            ItemLocation.Generic("Test"),
            action,
            ItemDataOrigin.LOCAL_OBSERVATION,
            fingerprint = ItemFingerprint("minecraft:stone", null, "test", "test"),
            searchableName = "Test",
        )
    }
}
