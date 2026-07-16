# SkyHUD

SkyHUD is a client-side Fabric mod for Hypixel SkyBlock that replaces cramped
inventory menus with clean, modern interfaces.

## Features

### Ender Chest

- Shows Ender Chest pages and discovered Storage backpacks in one searchable,
  three-column overview.
- Queries the populated Storage overview when needed, so unlocked backpacks are
  discovered even when the overlay was opened from an Ender Chest command.
- Remembers pages after they have been opened during the current session.
- Displays an **Open this page** action for pages that have not been loaded yet.
- Loads unopened pages through Hypixel's normal `/enderchest <page>` and
  `/backpack <page>` commands while keeping the SkyHUD overview visible.
- Uses a compact partial-screen panel with the live player inventory and hotbar
  below the page grid.
- Detects and renders each opened page's real row count, and supports persistent
  star favorites that move chosen pages to the top.
- Preserves left-click, right-click, shift-click, carried stacks, counts,
  tooltips, and server-side inventory behavior on the active page.
- Uses a compact draggable scrollbar instead of the vanilla-style control.

### Wardrobe

- Replaces the current `(n/n) Armor Sets` menu while retaining compatibility
  with the legacy Wardrobe title.
- Presents every server page in one compact five-column grid and renders each
  outfit's armor on a player mannequin.
- Outlines only the equipped outfit and leaves inactive sets unframed.
- Supports armor-name search, background page loading, and equip/unequip.

### Loadouts

- Replaces Hypixel's new `(n/n) Loadouts` pages with five loadouts per row.
- Displays armor on a player mannequin, equipment beside it, and the selected pet in the top-left.
- Displays HOTM, HOTF, Power Stone, and stat-tuning slots at the bottom-left.
- Keeps all three server pages in one retained grid, with load actions for pages
  that have not been visited yet.
- Remembers details after a loadout has been selected and outlines only the
  equipped loadout.
- Grays out empty loadouts without removing their edit pen, and lets the
  loadout title open the server edit/rename flow.
- Supports native item tooltips, loadout and item search, locked slots, validated
  page navigation, and a pen button that opens Hypixel's loadout editor.

### Equipment Sets

- Replaces the Equipment Sets menu opened by `/eq`.
- Presents all sets in one compact five-column retained grid.
- Uses the same selected-only outline, search, and background page loading as
  Wardrobe.
- Sends equip and unequip actions through the original server inventory slots.

Each custom screen has a compact **Edit** action that temporarily opens the
original Hypixel menu without the overlay replacing it.

All interfaces use SkyHUD's dark palette: `#0D0D0D` backgrounds with
`#1E3A69` accents. Detection is limited to verified Hypixel screen titles,
container layouts, and action items; unrelated chests are left untouched.

## Supported versions

- Minecraft 26.1.2 + Fabric
- Minecraft 26.2 + Fabric

SkyHUD requires Fabric Loader 0.19.3 or newer, Fabric API, Fabric Language
Kotlin, and Java 25. The correct MoulConfig platform is bundled in each SkyHUD
JAR. [SkyblockAPI](https://github.com/SkyblockAPI/SkyblockAPI) 4.2.10 is also
embedded, together with its required runtime libraries, so players do not need
to install SkyblockAPI or Hypixel Mod API separately.

## Settings

Run `/skyhud` or use Mod Menu's Config button. Ender Chest, Loadouts, Wardrobe,
and Equipment Sets can be enabled independently, and settings are stored in
`config/skyhud.json`. The Dashboard opens by default and shows the installed
SkyHUD version plus whether a newer stable GitHub release is available.

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

## Embedded libraries

SkyHUD uses SkyblockAPI as its profile-aware Ender Chest and backpack cache.
SkyblockAPI is distributed under the MIT License; its complete license notice
is packaged in every SkyHUD JAR at
`META-INF/licenses/skyblock-api/LICENSE.txt`.
