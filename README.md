# HenClient Reborn

A client-side Fabric utility mod for **Minecraft 1.21.11** with a red-and-black GUI, a clean HUD, and exploration / combat helpers.

| | |
|---|---|
| Minecraft | 1.21.11 (Mojang mappings, Loom `fabric-loom-remap`) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |
| Build | Gradle 9.5.1 via the included wrapper |

## Modules

**HUD & Info:** Coordinates, FPS Counter, Health HUD, Sprint Status, Armor Status (low-durability warning), Direction, Biome.

**Exploration:** Stash Finder, Spawner Finder, Sus Chunk Finder (heuristic), Storage ESP, Freecam.

**Combat:** Auto Clicker, Aim Assist.

## Using it

- **Right Shift** opens the GUI (change it with the *Menu key* button in the title bar).
- Left-click a module to toggle it, right-click to only select it. The settings panel on the right shows its options, a keybind button (click it, then press a key; Backspace/Delete clears) and notes.
- Settings, keybinds and enabled states are saved to `config/henclient-reborn.json`.

## Notes and limitations

- **Combat utilities:** many servers prohibit auto-clicking and aim assistance, and server-side anticheat can detect or limit them. Use them only where they are allowed.
- **Stash Finder** works like a stash hunter: it counts storage blocks (chests, barrels, shulkers, optionally ender chests, furnaces, hoppers, dispensers) per chunk as chunks become available and records chunks at or above a threshold (default 4). Records are saved per server and dimension in `config/henclient-reborn/stashes/`, announced in chat with coordinates, and shown as arrows on a ring around the crosshair. The Spawner and Sus Chunk finders use the same chat alerts and arrows.
- **Sus Chunk Finder** is a heuristic, not proof. It combines (1) a block score from blocks that rarely generate naturally (chests, spawners, torches, rails, obsidian, redstone parts, crafting stations) below a chosen Y, and (2) an optional chunk-age guess: a freshly generated chunk still has liquids that have not flowed yet, so chunks classified as old while most neighbours are new look like trails someone walked. Age is only guessed for chunks that contain liquids, only for chunks that arrive while you are watching, and can be wrong. Flagged chunks and the age map are saved per server and dimension (`config/henclient-reborn/suschunks/` and `chunkage/`).
- **Finders** only search chunks the client has already loaded and never load new ones. Scanning is interval-based and capped.
- **Storage ESP** draws screen-space boxes (projected from the camera and FOV setting) instead of using a custom world render pipeline, so they show through walls and follow Freecam. They ignore temporary FOV effects such as sprinting.
- **Freecam** moves a hidden camera entity while your body stands still. Clicking while in Freecam still acts from the camera's view, so avoid interacting. Disabling it, leaving the world or disconnecting restores the camera, input and your original view direction.
- No mixins are used.

## Build with GitHub Actions (no local Gradle needed)

1. Push this folder to your GitHub repository's `main` branch.
2. Open the repository on GitHub, click the **Actions** tab, and select **Build HenClient Reborn**. (You can also click **Run workflow** to start it manually.)
3. Open the finished run, scroll to **Artifacts** at the bottom of the summary page and download **HenClient-Reborn** (a zip containing the mod JAR).
4. Unzip it and put `henclient-reborn-1.0.0.jar` in your `.minecraft/mods` folder together with Fabric API.

### Pushing the code

```bash
git init
git add .
git commit -m "HenClient Reborn"
git branch -M main
git remote add origin https://github.com/<your-user>/<your-repo>.git
git push -u origin main --force
```

(`--force` replaces the old broken code in the repository. Drop it if you want to keep the history.)

## Local build (optional)

```bash
./gradlew build
```

The mod JAR is written to `build/libs/`.
