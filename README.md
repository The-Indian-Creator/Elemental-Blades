# ElementalBlades — Paper 26.3 plugin

Seven craftable elemental swords with two abilities, an ultimate and a passive each, custom textures,
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
| Ability 1 | Right-click |
| Ability 2 | Sneak + Right-click |
| **Ultimate** | **F** (swap-hands key) |
| Passives | Active while the blade is in your main hand |
| GUI | `/blades` (aliases `/eb`, `/swords`) |

The action bar shows both cooldowns and your ultimate charge while you hold a blade.
`controls.ultimate-key` can switch to `SNEAK_SWAP` (F = Ability 1, Sneak + F = Ultimate) or `NONE` (old controls, no ultimates key).

### Ultimates
Every blade has an ultimate. The bar **charges as you deal damage** while holding any blade:
- 40 damage (20 hearts) to players fills it. Damage to mobs counts half.
- A player kill adds 30% instantly.
- Charge resets when you die and doesn't build while an ultimate is running.

When it's full you get a sound, a chat message and a flashing **✦ ULT READY** on the action bar.
Set `ultimate.mode: COOLDOWN` for a plain 120s cooldown instead, or `ultimate.enabled: false` to turn ultimates off.

---

## 4. The blades

All numbers below are defaults. Every one is configurable in `config.yml`.
Damage is in hearts. Upgrades (levels II and III) reduce cooldowns by 15% and 30% and raise ability damage by 15% and 30%.

### 🔥 Flame Blade
- **Ring of Fire (A1, 30s):** a ring of fire around you for 5s. Anyone inside takes **5 hearts per hit (armour applies)** once per second and catches fire.
- **Fireball Stream (A2, 35s):** shoots fireballs wherever you look, non-stop for 5s. They don't set blocks on fire unless enabled.
- **Passive:** choose **Fire Resistance** or **Fire Aspect II** in the GUI. While held, there's a **50% chance to resist a Frost Blade dash freeze**.
- **✦ Meteor Storm (Ultimate):** for 6s, 3 meteors per wave crash down on enemies within 12 blocks. Each impact deals 3.5 hearts in a 3.5-block area and sets targets on fire. No block damage.

### ❄ Frost Blade
- **Glacial Dash (A1, 20s):** dash forward. Everyone you pass through is **frozen for 1s**.
- **Frost Nova (A2, 40s):** everyone within **5 blocks** is frozen (2.5s) and takes frost damage (3 hearts).
- **Passive:** Dolphin's Grace I, **Frost Walker** (toggle on/off any time in the GUI), immune to freezing.
  Every **10th hit: 25%** chance to freeze 0.5s; every **20th hit: 0.5%** chance to freeze 3s.

- **✦ Blizzard (Ultimate):** a blizzard follows you for 6s (8-block radius). Enemies inside get Slowness III and take 1.5 hearts of frost damage per second. When it ends, everyone still caught is **frozen for 2s**.

"Frozen" means the player can't move or jump, gets the frost overlay, and (by default) can't attack.

### 💚 Life Blade
- **Draining Dash (A1, 20s):** dash through players to **drain 1 heart each** (ignores armour), with a **50% chance of +1 absorption heart** for you.
- **Vitality Surge (A2, 60s):** your max health becomes **15 hearts** for 5s and you're healed to full.
- **Passive:** immune to **Wither, Poison and Harming**. Every **10th hit** gives you a **Healing II or Poison II** splash potion (50/50).
- **✦ Sanctuary (Ultimate):** heals you and team-mates within 10 blocks to full, cleanses bad effects and gives Regeneration II for 8s. For the next **10s you cheat death once**: a killing blow leaves you on 5 hearts instead.

### 🟣 Silence Blade
- **Void Sphere (A1, 45s):** a transparent shield sphere blocks attacks for 5s and pushes melee attackers away.
- **Silent Dome (A2, 120s):** an **unbreakable 15-block-radius dome** (black glass). Enemies inside are trapped. Walking, ender pearls and other teleports can't cross the wall.
  It lasts **until you use A2 again, die, drop the sword, or log out**. Explosions, pistons and mining can't break it. Blocks are restored afterwards, even after a server crash.
- **Passive:** every player kill gives a **Silent Pearl**, an ender pearl with **5 uses**.
- **✦ Void Collapse (Ultimate):** a void well pulls every enemy within 12 blocks toward you for 3s, then collapses. Enemies within 6 blocks take 3 hearts (ignores armour), get Blindness and Darkness, and are **silenced (shocked) for 4s**.

### ⚡ Lightning Blade
- **Static Shockwave (A1, 50s):** lightning ring around you. Every enemy within 6 blocks is **shocked for 5s**: potion effects removed, no new effects, **no blade abilities** (running ones are cancelled too) and **no weapons** (melee, bows, tridents).
- **Storm Strikes (A2, 60s):** for 10s, **every 3rd hit** calls lightning that removes **35% of the target's current health, ignoring all armour. It never kills.** Sparks and rising pings show the charge-up. (`trigger: CHANCE` brings back the old 90%-per-hit mode.)
- **Passive:** **Strength I in rain, Strength II in a thunderstorm.**
- **✦ Thunder God (Ultimate):** for 8s you get Speed II and Strength I, and every second lightning hits the **3 nearest enemies** within 16 blocks for 3 hearts each (ignores armour).

### 💨 Velocity Blade: moves in "frames"
- **Frame Step (A1, 45s):** 2x speed and Jump Boost II for 10s. While you move you leave **afterimages**, exact copies of you (your skin and armour) that last 2s.
  Any enemy who walks into one gets **frame-locked** for 1s: stuck inside a white film frame, unable to move, attack or use abilities. Your **first hit** during Frame Step frame-locks the target too.
- **Blink Barrage (A2, 30s):** flash-step behind up to 6 enemies within 16 blocks and slash each for 4 hearts. You **can't be hurt** while blinking, every blink point leaves a trap afterimage, and you return to where you started.
- **Passive:** Speed I and a **15% chance to dodge** melee hits. A dodge leaves an afterimage behind.
- **✦ Freeze Frame (Ultimate):** every enemy within 12 blocks is frame-locked for 3s and **hits don't break it**. You get Speed III and deal **+50% damage** to anyone frozen.

Frame-lock rules: getting hit breaks a normal frame-lock, and after one ends that player can't be locked again for 3s, so nobody gets stun-locked forever.
`blades.velocity.frame-lock.afterimage-style: PARTICLES` swaps the player copies for white particle outlines (lighter on the server). The plugin also switches automatically if mannequins can't spawn.

### 🌊 Water Blade (both abilities and the passive designed by me)
- **Bubble Prison (A1, 40s):** traps the player you're looking at (up to 20 blocks) in a floating water bubble for 4s. They can't move and start **drowning** (Water Breathing and Conduit Power protect them).
- **Tidal Wave (A2, 35s):** a 14-block wave rolls forward. It sweeps enemies along, deals 3 hearts, slows them and **puts out fire on you and team-mates**.
- **Passive:** Water Breathing, Conduit Power in water, and a **Riptide Surge**: hold sneak ~1s, release to launch like Riptide III. **Works anywhere, no water or rain needed.** You get **10 surges**, then a **45s recharge**. The action bar shows how many are left.
- **✦ Maelstrom (Ultimate):** a whirlpool forms where you stand for 6s (8-block radius). It drags enemies around in a spiral and drowns them for 1 heart per second. You get Regeneration and Dolphin's Grace.

---

## 5. Crafting

Blades are deliberately hard to get. Every blade needs a **Mythic Core**, and the Core itself needs boss and endgame loot.
All recipes are viewable in-game: `/blades` → Blade Codex. Click the Core in any recipe to see its own recipe.

**Mythic Core**
| | | |
|---|---|---|
| Netherite Block | Dragon's Breath | Netherite Block |
| Totem of Undying | **Nether Star** | Heart of the Sea |
| Netherite Block | Echo Shard | Netherite Block |

That needs a Wither kill, the Ender Dragon, a raid, ocean treasure, an Ancient City, and 36 netherite ingots.

**Blades** all use the same layout: `A B A / B [Mythic Core] B / A [Netherite Sword] A`

| Blade | A (corners) | B (sides) |
|---|---|---|
| Flame | Blaze Rod | Magma Block |
| Frost | Blue Ice | Powder Snow Bucket |
| Life | Golden Apple | Totem of Undying |
| Silence | Echo Shard | Sculk Catalyst |
| Lightning | Lightning Rod | Block of Copper |
| Velocity | Breeze Rod | Phantom Membrane |
| Water | Nautilus Shell | Wet Sponge |

- The whole server gets a message when someone forges a blade or a Core (`crafting.announce`).
- The Mythic Core can't be used as a normal Nether Star, and existing blades can't be used as the sword.
- All recipes, including the Core's, are editable in `config.yml`. Use `MYTHIC_CORE` as an ingredient name.
- Admins: `/blades core [amount]`, or the Admin menu.

Upgrade costs (GUI → Upgrade): Level II = 2 Netherite Ingots + 2 Diamond Blocks; Level III = 4 Netherite Ingots + 1 Nether Star.

---|---|---|---|
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
| Riptide surge anywhere or only water/rain? | `blades.water.passive.riptide-anywhere` | anywhere |
| Riptide surges before recharge / recharge time | `blades.water.passive.riptide-charges` / `riptide-recharge` | 10 / 45s |
| Ultimates on? Charge or cooldown? | `ultimate.enabled` / `ultimate.mode` | on / `CHARGE` |
| What does F do? | `controls.ultimate-key` | `SWAP_HAND` (ultimate) |
| Storm Strikes every 3rd hit or random chance? | `blades.lightning.ability2.trigger` | `EVERY_N` (3) |
| Velocity afterimages: player copies or particles? | `blades.velocity.frame-lock.afterimage-style` | `MANNEQUIN` |
| First Frame Step hit frame-locks? | `blades.velocity.ability1.touch-lock` | yes |
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
