# SkyHUD

SkyHUD is a client-side Fabric mod for Hypixel SkyBlock that replaces cramped
inventory menus with clean, modern interfaces.

## Features

### Ender Chest

- Shows every Ender Chest page in one searchable overview.
- Remembers pages after they have been opened during the current session.
- Displays an **Open this page** action for pages that have not been loaded yet.
- Loads unopened pages through Hypixel's normal `/enderchest <page>` command
  while keeping the SkyHUD overview visible.
- Preserves normal item clicks, counts, tooltips, and server-side inventory
  behavior on the active page.
- Uses a compact draggable scrollbar instead of the vanilla-style control.

### Wardrobe

- Replaces the current `(n/n) Armor Sets` menu while retaining compatibility
  with the legacy Wardrobe title.
- Presents three large outfits per row with each title above its four armor
  slots.
- Outlines only the equipped outfit and leaves inactive sets unframed.
- Supports armor-name search, equip/unequip, and validated page navigation.

### Loadouts

- Replaces Hypixel's new `(n/n) Loadouts` pages with five loadouts per row.
- Displays armor on a player mannequin, equipment beside it, and the selected pet in the top-left.
- Remembers details after a loadout has been selected and outlines only the
  equipped loadout.
- Supports native item tooltips, loadout and item search, locked slots, validated
  page navigation, and a pen button that opens Hypixel's loadout editor.

### Equipment Sets

- Replaces the Equipment Sets menu opened by `/eq`.
- Presents three large sets per row with four equipment slots per set.
- Uses the same selected-only outline, search, and page controls as Wardrobe.
- Sends equip and unequip actions through the original server inventory slots.

All interfaces use SkyHUD's dark palette: `#0D0D0D` backgrounds with
`#1E3A69` accents. Detection is limited to verified Hypixel screen titles,
container layouts, and action items; unrelated chests are left untouched.

## Supported versions

- Minecraft 26.1.2 + Fabric
- Minecraft 26.2 + Fabric

SkyHUD requires Fabric Loader 0.19.3 or newer, Fabric API, Fabric Language
Kotlin, and Java 25. The correct MoulConfig platform is bundled in each SkyHUD
JAR.

## Settings

Run `/skyhud` or use Mod Menu's Config button. Ender Chest, Loadouts, Wardrobe,
and Equipment Sets can be enabled independently, and settings are stored in
`config/skyhud.json`.

## Building

Build both supported versions:

```bash
./gradlew build
```

The production JARs are written to:

```text
versions/mc26_1_2/build/libs/SkyHUD-1.0.0+mc26.1.2.jar
versions/mc26_2/build/libs/SkyHUD-1.0.0+mc26.2.jar
```

SkyHUD is behaviorally informed by established SkyBlock storage mods, but its
UI and implementation are independent and it does not depend on Firmament.
