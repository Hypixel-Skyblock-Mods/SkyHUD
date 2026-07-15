# SkyHUD implementation plan

## Goal

Build a client-side Fabric mod for Hypixel SkyBlock that improves the Ender
Chest, Loadouts, Wardrobe, and Equipment interfaces. Use Kotlin, Fabric API,
MoulConfig for the configuration UI, and Mod Menu to expose it.

## 1. Bootstrap the project

- Create a standard Kotlin/Fabric Loom project; do not copy Firmod's custom
  Gradle modules or source-set layout.
- Target the same Minecraft/Fabric versions as the intended client profile.
- Add Fabric API and Fabric Language Kotlin.
- Create the client entrypoint, `fabric.mod.json`, a mixin configuration, and
  the `skyhud` asset namespace.
- Verify the empty mod builds and starts in the development client.

## 2. Add configuration and settings UI

- Add MoulConfig as an embedded dependency so SkyHUD's configuration screen is
  available without users installing another mod.
- Add Mod Menu as an optional runtime integration.
- Define a small persistent `SkyHudConfig` with feature toggles, beginning with
  one toggle per supported screen.
- Implement a MoulConfig settings screen with a `General` category and a
  category for each supported UI.
- Register a `ModMenuApi` config-screen factory so Mod Menu's Config button
  opens SkyHUD settings.
- Add a `/skyhud` client command as a second way to open settings.

## 3. Build the first vertical slice: Ender Chest

- Capture the Ender Chest screen/title and slot state through a narrowly
  scoped mixin or Fabric screen event.
- Add a `SkyBlockGuiDetector` that identifies a supported screen from the
  server-provided title, expected slot layout, and known item metadata; never
  identify a GUI from its pixels.
- For an Ender Chest page, require a matching title such as `Ender Chest
  (3/9)`, then parse the page number only after the expected container layout
  is present.
- Implement the requested visual improvement behind the Ender Chest config
  toggle.
- Preserve vanilla click, drag, tooltip, and inventory behaviour.
- Test with normal, selected, and unavailable Ender Chest pages.

## 4. Add the remaining interfaces incrementally

- Implement Loadouts as a self-contained feature package with its own toggle.
- Implement Wardrobe the same way.
- Implement Equipment the same way.
- Keep each feature limited to Hypixel-specific screen detection; never alter
  unrelated containers that happen to share a vanilla screen type.

## GUI identification and actions

- Treat each Hypixel GUI as server data: Minecraft receives its title, slot
  indices, and the `ItemStack` in every slot.
- Identify screens in layers: match the title, confirm the expected slot count
  and layout, then validate key button item types, display names, and lore.
- Read SkyBlock's internal item ID from `ExtraAttributes.id` / modern custom
  item data where needed. Use display-name or lore matching only as a narrowly
  scoped fallback for special items.
- Keep recognised screen rules next to their feature, e.g.
  `feature/wardrobe/WardrobeDetector.kt`, rather than maintaining one large
  global list of fragile slot constants.
- When SkyHUD adds a custom button, map it to a verified backing inventory slot
  and perform a normal vanilla slot click. Do not synthesize packets or bypass
  the server's normal inventory interaction path.
- Example Wardrobe rule: accept `Wardrobe (n/n)` only after validating the
  navigation/item slots; Firmod uses slots 45 and 53 for arrow navigation and
  slots 36 through 44 for outfit choices, with icon validation before clicking.
- Fail closed: if any title, layout, item, or lore check is unexpected, leave
  the vanilla screen and its input untouched.

## 5. Polish and release readiness

- Add clear labels, descriptions, defaults, and reset behaviour to settings.
- Test with Mod Menu present and absent.
- Test each UI at common GUI scales and window sizes.
- Verify a clean production JAR, config persistence, and no client crashes when
  SkyBlock data is unavailable.
- Add a README with installation, supported Minecraft version, and feature
  list.

## Proposed source layout

```text
src/main/kotlin/<package>/skyhud/
  SkyHudClient.kt
  config/SkyHudConfig.kt
  compat/modmenu/SkyHudModMenu.kt
  command/SkyHudCommand.kt
  feature/enderchest/
  feature/loadouts/
  feature/wardrobe/
  feature/equipment/
  mixin/

src/main/resources/
  fabric.mod.json
  skyhud.mixins.json
  assets/skyhud/
    lang/en_us.json
    gui/config/
```

## Deliberate non-goals for v1

- No Firmod-style custom build logic, code generation, or compatibility source
  sets.
- No dependency on Firmod itself.
- No broad inventory mixins: only the specific SkyBlock interfaces SkyHUD is
  meant to improve.
