package org.hypixelskyblockmods.skyhud.mixin

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.hypixelskyblockmods.skyhud.gui.OverlayScreenRouter
import org.hypixelskyblockmods.skyhud.platform.ScreenCompat
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
abstract class ScreenRedirectMixin {
    @Inject(method = ["setScreen"], at = [At("HEAD")], cancellable = true)
    private fun skyhudRedirectScreen(screen: Screen?, callback: CallbackInfo) {
        val incoming = screen ?: return
        val redirected = OverlayScreenRouter.redirect(Minecraft.getInstance(), incoming)
        if (redirected === incoming) return
        callback.cancel()
        if (ScreenCompat.currentScreen() !== redirected) ScreenCompat.setScreen(redirected)
    }
}
