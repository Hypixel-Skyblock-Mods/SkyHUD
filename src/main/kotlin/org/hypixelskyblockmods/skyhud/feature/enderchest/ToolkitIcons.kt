package org.hypixelskyblockmods.skyhud.feature.enderchest

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import java.nio.charset.StandardCharsets
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

object ToolkitIcons {
    private const val farmingTexture =
        "ewogICJ0aW1lc3RhbXAiIDogMTc3MjIxMTUwOTg3NiwKICAicHJvZmlsZUlkIiA6ICJhZTQyMTU2N2QzODg0YWUxOTJhNmNiNTY4ZmZkNGZhMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJ0aW1vdGh5ZGVvZG9yYW50IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzc0MThjNzRlMGQ3ZDU2YmRhZjVmYjUwMThhNzM4ZDQzMDBiOGIzM2EwZWE4ODc1ZmI4NzYxMjM5NDMwODlmNWQiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="
    private const val huntingTexture =
        "ewogICJ0aW1lc3RhbXAiIDogMTcyMTUxNzI5NjIxNywKICAicHJvZmlsZUlkIiA6ICI4YWFlYTdlYjViOWM0ZWEwODUxNWU3MDhhZGIxODBkNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYVBhODA3MTEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM2MjQzOGZmNGVjZjhmNGEyY2FhMTI3NzU2MWM5NTEzYzlhOTg2ZGJlMzhhODBiOWJhZmNiZmVkOGIyYTljOCIKICAgIH0KICB9Cn0="

    val farming: ItemStack by lazy { playerHead("SkyHUD Farming Toolkit", farmingTexture) }
    val hunting: ItemStack by lazy { playerHead("SkyHUD Hunting Toolkit", huntingTexture) }

    private fun playerHead(key: String, texture: String): ItemStack {
        val profile = GameProfile(UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)), key)
        profile.properties().put("textures", Property("textures", texture))
        return ItemStack(Items.PLAYER_HEAD).also {
            it.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
        }
    }
}
