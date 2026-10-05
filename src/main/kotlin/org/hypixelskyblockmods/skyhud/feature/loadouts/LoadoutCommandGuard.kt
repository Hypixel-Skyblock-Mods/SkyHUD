package org.hypixelskyblockmods.skyhud.feature.loadouts

/** Leaves menus opened while Hypixel processes a named loadout command in vanilla. */
internal class LoadoutCommandGuard {
    private var nativeUntil = 0L

    fun onCommandSent(command: String, now: Long): Boolean {
        val parts = command.trim().removePrefix("/").split(Regex("\\s+"), limit = 2)
        if (!parts[0].equals("loadout", ignoreCase = true) &&
            !parts[0].equals("loadouts", ignoreCase = true)
        ) return false

        // A bare command explicitly opens the browser. A failed named command must
        // also expire, since Hypixel may reject it without opening any menu.
        nativeUntil = if (parts.size > 1) now + 10_000 else 0L
        return true
    }

    fun shouldKeepNative(now: Long): Boolean = now < nativeUntil

    fun clear() {
        nativeUntil = 0L
    }
}
