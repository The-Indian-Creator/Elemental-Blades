package com.elementalblades.blade;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** The seven elemental blades. Display text lives here; all numbers live in config.yml. */
public enum BladeType {

    FLAME("flame", "Flame Blade", TextColor.color(0xFF5A1F), Color.fromRGB(255, 90, 31), Material.BLAZE_POWDER,
            "Ring of Fire", "A ring of flame surrounds you, burning anyone inside it (armour applies).",
            "Fireball Stream", "Shoot a constant stream of fireballs wherever you look.",
            List.of("Choose: Fire Resistance OR Fire Aspect", "50% chance to resist Frost Blade dash freezes"),
            "Meteor Storm", "Call down a storm of meteors on every enemy around you."),

    FROST("frost", "Frost Blade", TextColor.color(0x7FDBFF), Color.fromRGB(127, 219, 255), Material.BLUE_ICE,
            "Glacial Dash", "Dash forward; anyone you pass through is frozen solid.",
            "Frost Nova", "Freeze and chill everyone within 5 blocks.",
            List.of("Dolphin's Grace I + toggleable Frost Walker", "Every 10th hit: 25% chance to freeze 0.5s",
                    "Every 20th hit: 0.5% chance to freeze 3s"),
            "Blizzard", "A raging blizzard follows you, then freezes everyone caught in it."),

    LIFE("life", "Life Blade", TextColor.color(0x4CFF6A), Color.fromRGB(76, 255, 106), Material.TOTEM_OF_UNDYING,
            "Draining Dash", "Dash through enemies, draining 1 heart each (50% chance of +1 absorption heart).",
            "Vitality Surge", "Raise your health to 15 hearts for a few seconds.",
            List.of("Immune to Wither, Poison and Harming", "Every 10th hit grants a Healing or Poison potion"),
            "Sanctuary", "Fully heal you and nearby team-mates, and cheat death once for 10s."),

    SILENCE("silence", "Silence Blade", TextColor.color(0x9B6BFF), Color.fromRGB(155, 107, 255), Material.ECHO_SHARD,
            "Void Sphere", "A transparent sphere shields you from attacks.",
            "Silent Dome", "Raise an unbreakable 15-block dome that traps everyone inside.",
            List.of("Every kill grants an Ender Pearl with 5 uses"),
            "Void Collapse", "Drag every enemy into a void well, then blind and silence them."),

    LIGHTNING("lightning", "Lightning Blade", TextColor.color(0xFFE53B), Color.fromRGB(255, 229, 59), Material.LIGHTNING_ROD,
            "Static Shockwave", "A ring of lightning strips potion effects, abilities and weapons for 5s.",
            "Storm Strikes", "Every 3rd hit calls down lightning that ignores armour (never kills).",
            List.of("Strength I in rain, Strength II in thunderstorms"),
            "Thunder God", "Lightning strikes the nearest enemies every second; you gain Speed & Strength."),

    VELOCITY("velocity", "Velocity Blade", TextColor.color(0xE8E8FF), Color.fromRGB(232, 232, 255), Material.FEATHER,
            "Frame Step", "Move at high speed, leaving afterimages that frame-lock anyone who touches them.",
            "Blink Barrage", "Flash-step behind nearby enemies, slashing each and leaving trap afterimages.",
            List.of("Permanent Speed I", "15% chance to dodge melee attacks (leaves an afterimage)"),
            "Freeze Frame", "Frame-lock every enemy around you; your hits on them deal +50%."),

    WATER("water", "Water Blade", TextColor.color(0x2F7BFF), Color.fromRGB(47, 123, 255), Material.HEART_OF_THE_SEA,
            "Bubble Prison", "Trap the player you're looking at inside a drowning water bubble.",
            "Tidal Wave", "Send a crashing wave forward that sweeps enemies away.",
            List.of("Water Breathing + Conduit Power", "Riptide surge: hold sneak, release to launch"),
            "Maelstrom", "Summon a whirlpool that drags enemies around and drowns them.");

    private final String id;
    private final String displayName;
    private final TextColor textColor;
    private final Color particleColor;
    private final Material icon;
    private final String ability1Name, ability1Desc, ability2Name, ability2Desc;
    private final List<String> passiveLines;
    private final String ultimateName, ultimateDesc;

    BladeType(String id, String displayName, TextColor textColor, Color particleColor, Material icon,
              String a1, String a1d, String a2, String a2d, List<String> passiveLines, String ult, String ultDesc) {
        this.id = id;
        this.displayName = displayName;
        this.textColor = textColor;
        this.particleColor = particleColor;
        this.icon = icon;
        this.ability1Name = a1;
        this.ability1Desc = a1d;
        this.ability2Name = a2;
        this.ability2Desc = a2d;
        this.passiveLines = passiveLines;
        this.ultimateName = ult;
        this.ultimateDesc = ultDesc;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public TextColor textColor() { return textColor; }
    public Color particleColor() { return particleColor; }
    public Material icon() { return icon; }
    public String ability1Name() { return ability1Name; }
    public String ability1Desc() { return ability1Desc; }
    public String ability2Name() { return ability2Name; }
    public String ability2Desc() { return ability2Desc; }
    public List<String> passiveLines() { return passiveLines; }
    public String ultimateName() { return ultimateName; }
    public String ultimateDesc() { return ultimateDesc; }

    /** Config path prefix, e.g. "blades.flame." */
    public String path() { return "blades." + id + "."; }

    public static BladeType fromId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase(Locale.ROOT);
        for (BladeType t : values()) if (t.id.equals(lower)) return t;
        return null;
    }
}
