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

- Presents all nine outfits on the current Wardrobe page as responsive cards.
- Shows each outfit's four armor pieces together.
- Provides clear active, ready, and unavailable states.
- Supports armor-name search, equip/unequip, and validated page navigation.

Both interfaces use SkyHUD's dark palette: `#0D0D0D` backgrounds with
`#1E3A69` accents. Detection is limited to verified Hypixel screen titles,
container layouts, and action items; unrelated chests are left untouched.

## Supported versions

- Minecraft 26.1.2 + Fabric
- Minecraft 26.2 + Fabric

SkyHUD requires Fabric Loader 0.19.3 or newer, Fabric API, Fabric Language
Kotlin, and Java 25. The correct MoulConfig platform is bundled in each SkyHUD
JAR.

## Settings

Run `/skyhud` or use Mod Menu's Config button. Ender Chest and Wardrobe
replacements can be enabled independently, and settings are stored in
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
