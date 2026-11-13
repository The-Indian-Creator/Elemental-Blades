# ElementalBlades — Paper 26.3 plugin

Seven craftable elemental swords with two abilities and a passive each, custom textures,
an upgrade/configuration GUI, and a built-in PvP team system (team chest, team home, team chat,
friendly-fire controls) that the abilities respect.

> You asked for 6 swords but described 7 (Flame, Frost, Life, Silence, Lightning, Velocity, Water),
> so all 7 are in. Disable any of them by setting `recipes.<blade>.enabled: false`.

---

## 1. Build the jar (one time)

This sandbox had no internet access to the Paper repository, so the jar must be built on your PC.
It's one command, and Gradle downloads everything it needs (Paper 26.3 API and, if missing, a Java 25 JDK).

**Windows**
```
cd ElementalBlades
gradlew.bat build
```
**Linux / macOS / Raspberry Pi**
```
cd ElementalBlades
chmod +x gradlew
./gradlew build
```
The plugin appears at `build/libs/ElementalBlades-1.0.0.jar`. Drop it in your server's `plugins/` folder and restart.

Requirements: Paper **26.3** (the server itself needs Java 25, which Paper 26.x already requires).

---

## 2. Textures (resource pack)

The swords are netherite swords with a custom item model, so players need the pack to see the colours.
Without it they still work but look like normal netherite swords.

The plugin builds the pack itself at startup into `plugins/ElementalBlades/ElementalBlades-pack.zip`.
You can edit the textures in `plugins/ElementalBlades/resourcepack/` and run `/blades reload`.

Choose one in `config.yml → resource-pack.mode`:

| Mode | What to do |
|---|---|
| `SELF_HOST` (default) | The plugin serves the pack on port `8163`. Set `self-host.public-address` to the address players connect to, and make sure that port is reachable. **If you use playit.gg**, add a second TCP tunnel for port 8163 and put that tunnel's address + port here. |
| `URL` (easiest with playit) | Upload `ElementalBlades-pack.zip` somewhere with a direct download link (GitHub release, Dropbox `?dl=1`, mc-packs.net…) and paste it into `resource-pack.url`. Re-upload after editing textures. |
| `NONE` | Players install the zip manually from the resource-pack menu. |

`/blades pack` re-sends the pack to yourself.

### Sword style: 2D or 3D
`resource-pack.style` in config.yml (then `/blades reload`):
- `MODEL_3D`: ornate 3D swords, one design per element: flame licks and fire wings, an ice-crystal blade with icicles, a vine blade with a heart gem, a void staff with diamond frames, a zig-zag lightning blade, a rapier with wind rings and feather wings, a trident. Their gems glow in the dark.
- `FLAT`: 2D swords, 32x32, with element decorations and a coloured glow outline
- `HYBRID` (default): 3D in your hand and on other players, 2D icon in inventories, item frames and on the ground

### 2D swords from the REAL netherite sword texture (recommended)
The plugin can recolour the vanilla netherite sword one-to-one and add the element decorations on top.
Mojang's texture can't be shipped with the plugin, so give it your own copy:

1. Open `%APPDATA%\.minecraft\versions\26.3\26.3.jar` with 7-Zip or WinRAR (it's a zip file).
2. Copy `assets/minecraft/textures/item/netherite_sword.png` into `plugins/ElementalBlades/`.
3. Run `/blades reload`. The console prints "2D textures built from your vanilla netherite sword".

Alternatively, set `resource-pack.netherite-texture` to the full path of the `.jar` if the server runs on the same PC.
Toggles: `flat-decorations`, `flat-glow-outline`, `keep-vanilla-handle`, `flat-source` (AUTO / VANILLA / BUILTIN).
Without the file, the built-in chunky art is used, with the same decorations.

Editing tools: `tools/make_models3d.py` (3D designs, also editable in Blockbench) and `tools/make_textures.py` (built-in 2D art).

---

## 3. Controls

| Action | Input |
|---|---|
| Ability 1 | Right-click (or **F** / swap-hands key) |
| Ability 2 | Sneak + Right-click (or **Sneak + F**) |
| Passives | Active while the blade is in your main hand |
| GUI | `/blades` (aliases `/eb`, `/swords`) |

The action bar shows both cooldowns while you hold a blade.

---

## 4. The blades

All numbers below are defaults. Every one is configurable in `config.yml`.
Damage is in hearts. Upgrades (levels II and III) reduce cooldowns by 15% and 30% and raise ability damage by 15% and 30%.

### 🔥 Flame Blade
- **Ring of Fire (A1, 30s):** a ring of fire around you for 5s. Anyone inside takes **5 hearts per hit (armour applies)** once per second and catches fire.
- **Fireball Stream (A2, 35s):** shoots fireballs wherever you look, non-stop for 5s. They don't set blocks on fire unless enabled.
- **Passive:** choose **Fire Resistance** or **Fire Aspect II** in the GUI. While held, there's a **50% chance to resist a Frost Blade dash freeze**.

### ❄ Frost Blade
- **Glacial Dash (A1, 20s):** dash forward. Everyone you pass through is **frozen for 1s**.
- **Frost Nova (A2, 40s):** everyone within **5 blocks** is frozen (2.5s) and takes frost damage (3 hearts).
- **Passive:** Dolphin's Grace I, **Frost Walker** (toggle on/off any time in the GUI), immune to freezing.
  Every **10th hit: 25%** chance to freeze 0.5s; every **20th hit: 0.5%** chance to freeze 3s.

"Frozen" means the player can't move or jump, gets the frost overlay, and (by default) can't attack.

### 💚 Life Blade
- **Draining Dash (A1, 20s):** dash through players to **drain 1 heart each** (ignores armour), with a **50% chance of +1 absorption heart** for you.
- **Vitality Surge (A2, 60s):** your max health becomes **15 hearts** for 5s and you're healed to full.
- **Passive:** immune to **Wither, Poison and Harming**. Every **10th hit** gives you a **Healing II or Poison II** splash potion (50/50).

### 🟣 Silence Blade
- **Void Sphere (A1, 45s):** a transparent shield sphere blocks attacks for 5s and pushes melee attackers away.
- **Silent Dome (A2, 120s):** an **unbreakable 15-block-radius dome** (black glass). Enemies inside are trapped. Walking, ender pearls and other teleports can't cross the wall.
  It lasts **until you use A2 again, die, drop the sword, or log out**. Explosions, pistons and mining can't break it. Blocks are restored afterwards, even after a server crash.
- **Passive:** every player kill gives a **Silent Pearl**, an ender pearl with **5 uses**.

### ⚡ Lightning Blade
- **Static Shockwave (A1, 50s):** lightning ring around you. Every enemy within 6 blocks is **shocked for 5s**: potion effects removed, no new effects, **no blade abilities** (running ones are cancelled too) and **no weapons** (melee, bows, tridents).
- **Storm Strikes (A2, 60s):** for 10s, each hit has a **90% chance** to call lightning that removes **35% of the target's current health, ignoring all armour. It never kills.**
- **Passive:** **Strength I in rain, Strength II in a thunderstorm.**

### 💨 Velocity Blade (A2 designed by me)
- **Lightspeed (A1, 60s):** **2x speed and fully invisible** (armour too) for 20s. Enemies only catch a lightning flash of you every 2s. Team-mates can still see you.
- **Sonic Slam (A2, 30s):** a shockwave blasts enemies within 7 blocks back and up, deals 3 hearts and gives Slowness II for 3s.
- **Passive:** Speed I.

### 🌊 Water Blade (both abilities and the passive designed by me)
- **Bubble Prison (A1, 40s):** traps the player you're looking at (up to 20 blocks) in a floating water bubble for 4s. They can't move and start **drowning** (Water Breathing and Conduit Power protect them).
- **Tidal Wave (A2, 35s):** a 14-block wave rolls forward. It sweeps enemies along, deals 3 hearts, slows them and **puts out fire on you and team-mates**.
- **Passive:** Water Breathing, Conduit Power in water, and a **Riptide Surge**: hold sneak ~1s, release to launch like Riptide III. In water or rain only by default (`riptide-anywhere: true` makes it work everywhere).

---

## 5. Crafting

Shaped recipes around a **plain netherite sword** (an existing blade can't be used). Every recipe can be viewed in-game: `/blades → Blade Codex`.

| Blade | Top | Middle | Bottom |
|---|---|---|---|
| Flame | Blaze Rod · Magma Block · Blaze Rod | Fire Charge · **Sword** · Fire Charge | Blaze Rod · Netherite Ingot · Blaze Rod |
| Frost | Blue Ice · Powder Snow Bucket · Blue Ice | Snow Block · **Sword** · Snow Block | Blue Ice · Netherite Ingot · Blue Ice |
| Life | Glistering Melon · Totem · Glistering Melon | Golden Apple · **Sword** · Golden Apple | Glistering Melon · Netherite Ingot · Glistering Melon |
| Silence | Echo Shard · Sculk Catalyst · Echo Shard | Sculk Sensor · **Sword** · Sculk Sensor | Echo Shard · Netherite Ingot · Echo Shard |
| Lightning | Lightning Rod · Glowstone · Lightning Rod | Copper Block · **Sword** · Copper Block | Lightning Rod · Netherite Ingot · Lightning Rod |
| Velocity | Feather · Wind Charge · Feather | Rabbit Foot · **Sword** · Rabbit Foot | Feather · Netherite Ingot · Feather |
| Water | Prismarine Shard · Heart of the Sea · Prismarine Shard | Nautilus Shell · **Sword** · Nautilus Shell | Prismarine Shard · Netherite Ingot · Prismarine Shard |

Upgrade costs (GUI → Upgrade): Level II = 2 Netherite Ingots + 2 Diamond Blocks; Level III = 4 Netherite Ingots + 1 Nether Star.

---

## 6. Teams

`/team` opens the team GUI (create, invite, members, chest, home, settings). The command name and aliases are configurable.

| Command | What it does |
|---|---|
| `/team create <name>` | Make a team (you're the owner) |
| `/team invite <player>` · `accept` · `deny` · `join <team>` | Invites (clickable in chat) / join open teams |
| `/team leave` · `kick` · `promote` · `demote` · `transfer` | Membership & ranks (Owner / Admin / Member) |
| `/team chest` | Shared **54-slot double chest**, saved to disk |
| `/team sethome` · `/team home` | Team home (3s warm-up, cancelled by moving or taking damage; 30s cooldown) |
| `/team chat [msg]`, `/tc <msg>`, or start a message with `!` | Team chat |
| `/team ff` | **Friendly fire**: can team-mates hit each other normally? (default off) |
| `/team abilityff` | **Ability friendly fire**: can blade abilities hit team-mates? |
| `/team open` · `rename` · `color` · `info` · `list` · `disband confirm` | Misc |

Team names show as a coloured `[TEAM]` prefix above heads and in tab.

**How abilities treat team-mates:** each blade has `spare-teammates` in the config. When it's `true` (the default for all 7),
that blade's abilities never hurt, freeze, shock, bubble or dome-trap team-mates. Set it to `false` for a blade and the team's own
"Ability friendly fire" toggle decides instead. Under the same rule, team-mates can walk through each other's Silent Dome walls, and team-mates' tamed pets are spared too.

---

## 7. Admin

| Command | Permission |
|---|---|
| `/blades give <player> <blade> [level]` | `elementalblades.admin` |
| `/blades reload` (config, recipes, pack) | `elementalblades.admin` |
| `/blades resetcooldowns` · `/blades pearl` | `elementalblades.admin` |
| GUI → Admin: Give Blades (left-click Lv I, right-click max) | `elementalblades.admin` |
| No cooldowns (for testing) | `elementalblades.nocooldown` (not given to ops by default) |

Other permissions (all default `true`): `elementalblades.use`, `.craft`, `.upgrade`, `.team`.

---

## 8. "When in doubt, make two versions" — every yes/no toggle

Rather than two separate jars, each ambiguous point is a switch in `config.yml`. Flip it and run `/blades reload`.

| Question | Key | Default |
|---|---|---|
| Ability on right-click? | `controls.right-click` | yes |
| Ability on F (swap hands)? | `controls.swap-hand-key` | yes |
| Do abilities spare team-mates? | `blades.<blade>.spare-teammates` | yes (all) |
| New Flame blades start with Fire Res or Fire Aspect? | `blades.flame.passive.default-choice` | Fire Resistance |
| Do fireballs set blocks alight? | `blades.flame.ability2.ignite-blocks` | no |
| Can frozen players attack? | `blades.frost.freeze-blocks-attacks` | no (they can't) |
| Life: also block harming (magic) damage? | `blades.life.passive.block-harming-damage` | yes |
| Life potions splash or drinkable? | `blades.life.passive.splash` | splash |
| Silence sphere blocks ALL damage (fall, fire…)? | `blades.silence.ability1.block-all-damage` | no, only attacks |
| Dome time limit? | `blades.silence.ability2.max-duration` | none (0) |
| Can the dome owner walk through the wall? | `blades.silence.ability2.owner-can-pass` | yes |
| Shock cancels already-running abilities? | `blades.lightning.ability1.cancel-active-abilities` | yes |
| Storm Strikes timed or always on? | `blades.lightning.ability2.mode` | `TIMED` (10s) |
| Strength levels in rain / thunder | `blades.lightning.passive.rain-strength` / `thunder-strength` | I / II |
| Team-mates see invisible Velocity user? | `blades.velocity.ability1.teammates-can-see` | yes |
| Attacking ends Lightspeed invisibility? | `blades.velocity.ability1.reveal-on-attack` | no |
| Riptide surge anywhere or only water/rain? | `blades.water.passive.riptide-anywhere` | water/rain only |
| Count mob hits/kills for passives? | `count-mob-hits` / `count-mob-kills` | hits yes / kills no |
| Resource pack required to join? | `resource-pack.required` | no |

---

## 9. What was tested, and what to check in-game

Tested in the build sandbox:
- Full compile of all 4,900 lines against signature stubs of the Paper 26.3 API, with zero errors and zero warnings. Uncertain methods were checked one by one against the official 26.3 javadocs, and deprecated calls were replaced.
- The real Gradle build script (version filtering, packaging; 96 files in the jar).
- Every config path the code reads exists in `config.yml` (176 reads).
- Recipes are well formed and unique. Every resource-pack file exists, the JSON is valid, and the textures are 16×16.
- Unit tests for the helper functions.

Not possible there (no Minecraft server): actually running it. Please try this checklist on a test world:
1. `/blades give <you> flame` → texture shows after accepting the pack.
2. Each blade: A1, A2, passive. Cooldowns show on the action bar.
3. Two players in one `/team`: abilities don't hit each other. Toggle `/team ff`.
4. Silence dome: try mining, TNT and pearls against the wall, then drop the sword → the dome disappears.
5. `/team chest` with two players at once, restart the server → items persist.

If something errors, the console stack trace points at the exact line. Paste it back to me.

## Project layout
```
src/main/java/com/elementalblades/
  ElementalBlades.java        main class
  ability/                    AbilityManager, Fx (particles/dash), blades/ (one class per sword)
  blade/                      BladeType, BladeItems, RecipeManager
  combat/                     BladeListener (events), CombatRules (team-aware targeting), StatusManager (freeze/shock)
  team/                       Team, TeamManager (persistence, chest, home), TeamListener
  gui/                        all menus + chat prompts
  command/                    /blades, /team, /tc (Brigadier)
  pack/                       resource pack builder + optional web server
src/main/resources/           plugin.yml, config.yml, resourcepack/
```
