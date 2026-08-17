// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import red.team.badghost.automation.plan.PlanMode;
import red.team.badghost.visuals.template.GhostTemplate;

/**
 * Client config spec. Never registered on a dedicated server.
 *
 * <p>The comments here are read by two audiences and both matter. Someone editing the TOML by hand
 * sees them as written; the in-game settings screen shows them as the tooltip only when no
 * translated {@code badghost.configuration.<key>.tooltip} exists. So each comment says what the
 * setting does and, where it is not obvious, what it does <em>not</em> do — a setting whose
 * description overpromises is the same defect as one that does nothing.</p>
 */
public final class BadghostConfig {
    private BadghostConfig() {}

    public static final ModConfigSpec SPEC;

    // Ghost blocks
    public static final ModConfigSpec.ConfigValue<String> GHOST_BLOCK;
    public static final ModConfigSpec.BooleanValue FROZEN_SLIPPERY;
    public static final ModConfigSpec.BooleanValue BOUNCY;
    public static final ModConfigSpec.BooleanValue DISABLE_NEGATIVES;
    public static final ModConfigSpec.DoubleValue MODEL_OFFSET;
    public static final ModConfigSpec.DoubleValue CAMERA_DISTANCE;
    public static final ModConfigSpec.IntValue GHOST_LIMIT;

    // Ghost-block shapes
    public static final ModConfigSpec.EnumValue<GhostTemplate> TEMPLATE_SHAPE;
    public static final ModConfigSpec.IntValue TEMPLATE_SIZE;

    // On-screen panel
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;

    // Bedrock miner
    public static final ModConfigSpec.BooleanValue AUTOMATION_ENABLED;
    public static final ModConfigSpec.EnumValue<PlanMode> PLAN_MODE;
    public static final ModConfigSpec.ConfigValue<String> SUPPORT_BLOCK;
    public static final ModConfigSpec.IntValue LIMIT_MAX;
    public static final ModConfigSpec.IntValue MINER_MAX_RETRIES;
    public static final ModConfigSpec.IntValue WAIT_TICKS;
    public static final ModConfigSpec.IntValue ROTATE_SETTLE_TICKS;
    public static final ModConfigSpec.BooleanValue SKIP_INSTAMINE_CHECK;

    // Preview
    public static final ModConfigSpec.BooleanValue PREVIEW_ENABLED;

    // Auto scan
    public static final ModConfigSpec.BooleanValue AUTO_SCAN_ENABLED;
    public static final ModConfigSpec.IntValue AUTO_SCAN_RADIUS;
    public static final ModConfigSpec.IntValue AUTO_SCAN_INTERVAL;

    // ESP
    public static final ModConfigSpec.BooleanValue ESP_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> ESP_COLOR;
    public static final ModConfigSpec.DoubleValue ESP_ALPHA;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Fake blocks that only your own game knows about. The server keeps its own",
                        "idea of the world, so you can stand on one and still fall through.")
                .push("Visuals");
        GHOST_BLOCK = builder
                .comment("Which block the ghost key puts down, as a block id.",
                        "A name the game does not know is ignored and the key tells you so.")
                .define("ghostBlock", "minecraft:bedrock");
        FROZEN_SLIPPERY = builder
                .comment("Makes ghost blocks as slippery as ice under your feet.",
                        "Only you feel it. The server still moves you by the real block below,",
                        "so this changes how walking feels and nothing more.")
                .define("frozenSlippery", false);
        BOUNCY = builder
                .comment("Makes landing on a ghost block bounce you, the way a slime block would.",
                        "Only you bounce; the server sees an ordinary landing.")
                .define("bouncy", false);
        DISABLE_NEGATIVES = builder
                .comment("Stops nausea from swirling your view, and stops blindness and darkness",
                        "from closing the fog in around you.",
                        "Only the picture changes. The effects belong to the server: they keep their",
                        "full length and everything else they do to you still happens.")
                .define("disableNegatives", false);
        MODEL_OFFSET = builder
                .comment("Shifts player models up or down by this many blocks when they are drawn.",
                        "Applies to everyone you can see, not just you, and moves only the picture —",
                        "a player drawn higher is still standing where they were.",
                        "Zero draws everyone normally.")
                .defineInRange("modelOffset", 0.0D, -10.0D, 10.0D);
        CAMERA_DISTANCE = builder
                .comment("How far back the third-person camera sits, in blocks. The game uses 4.",
                        "Larger pulls the view further out; it still stops short of walls rather",
                        "than pushing through them.")
                .defineInRange("cameraDistance", 4.0D, 1.0D, 100.0D);
        GHOST_LIMIT = builder
                .comment("How many ghost blocks may exist at once.",
                        "A ceiling so that holding the key cannot fill your world without end.",
                        "Reaching it stops placement and says so rather than failing quietly.")
                .defineInRange("ghostLimit", 256, 1, 4096);
        builder.pop();

        builder.comment("Whether the ghost key places one block or lays out a whole shape.")
                .push("Template");
        TEMPLATE_SHAPE = builder
                .comment("What one press lays out.",
                        "SINGLE paints a block at a time while you hold the key, as it always has.",
                        "Every other shape goes down whole on each press, and one undo takes the",
                        "whole thing back instead of asking for a press per block.")
                .defineEnum("templateShape", GhostTemplate.SINGLE);
        TEMPLATE_SIZE = builder
                .comment("How large a shape is, in blocks. SINGLE ignores this.",
                        "Cells already occupied are stepped over, and the count is reported, so a",
                        "shape that comes out smaller than you asked for tells you why.")
                .defineInRange("templateSize", 3, 1, GhostTemplate.MAX_SIZE);
        builder.pop();

        builder.comment("What the mod draws on your screen.")
                .push("Interface");
        HUD_ENABLED = builder
                .comment("Shows the status panel in the top-left corner while the miner is armed.",
                        "Turn it off for a clean screen; the miner keeps working either way, and",
                        "/badghost stats and /badghost why still answer.",
                        "There is a key for this too, so it can be toggled without leaving the game.")
                .define("hudEnabled", true);
        builder.pop();

        builder.comment("Removing bedrock with the piston glitch, one target at a time or several.")
                .push("Automation");
        AUTOMATION_ENABLED = builder
                .comment("Whether the miner is already armed when you join a world.",
                        "Off means you arm it yourself with the key, which is the safer default.")
                .define("automationEnabled", false);
        PLAN_MODE = builder
                .comment("Which faces of a bedrock block the miner is allowed to work from.",
                        "VERTICAL_FAST uses only above and below. Everything it needs happens in one",
                        "tick, so nothing about your aim has to be faked.",
                        "ALL_DIRECTION also considers the four sides, which reaches blocks the",
                        "vertical approach cannot — at the cost of holding a false aim for a tick.")
                .defineEnum("planMode", PlanMode.VERTICAL_FAST);
        SUPPORT_BLOCK = builder
                .comment("Which block to put under the torch when there is nothing for it to sit on.",
                        "Any solid block will do; slime is easy to break back out again.")
                .define("supportBlock", "minecraft:slime_block");
        LIMIT_MAX = builder
                .comment("How many bedrock blocks may be worked on at the same time.",
                        "More is faster and spends pistons faster; one is easiest to watch.")
                .defineInRange("limitMax", 1, 1, 10);
        MINER_MAX_RETRIES = builder
                .comment("How many times to try a block before giving up on it and saying why.")
                .defineInRange("maxRetries", 3, 1, 10);
        WAIT_TICKS = builder
                .comment("How long to wait for the piston to reach the state the trick needs,",
                        "in ticks; twenty ticks make a second.",
                        "Raise it on a laggy connection, where the answer takes longer to come back.")
                .defineInRange("waitTicks", 20, 5, 100);
        ROTATE_SETTLE_TICKS = builder
                .comment("How long to hold a sideways aim before acting on it, in ticks.",
                        "Used only by ALL_DIRECTION, and only to give the server a tick to notice",
                        "the turn before the placement that depends on it.")
                .defineInRange("rotateSettleTicks", 1, 1, 10);
        SKIP_INSTAMINE_CHECK = builder
                .comment("Starts the trick even when your pickaxe cannot break a piston instantly.",
                        "It will usually fail, because the whole trick depends on that one instant",
                        "break. For working out what is going wrong, not for mining.")
                .define("skipInstaMineCheck", false);
        builder.pop();

        builder.comment("Showing what the miner is about to do, before it does it.")
                .push("Preview");
        PREVIEW_ENABLED = builder
                .comment("Outlines the mechanism the miner would build around the block you are",
                        "looking at, or the cell that is in the way when it cannot be mined,",
                        "with the reason named.",
                        "Nothing is placed or broken by looking.")
                .define("previewEnabled", true);
        builder.pop();

        builder.comment("Queueing nearby bedrock without being asked each time.")
                .push("AutoScan");
        AUTO_SCAN_ENABLED = builder
                .comment("Looks for bedrock around you and queues it by itself.",
                        "Off by default on purpose: on a bedrock floor or the nether roof there is",
                        "always more of it, and this will spend every piston you own.")
                .define("autoScanEnabled", false);
        AUTO_SCAN_RADIUS = builder
                .comment("How far around you to look, in blocks.")
                .defineInRange("autoScanRadius", 4, 1, 8);
        AUTO_SCAN_INTERVAL = builder
                .comment("How long between one look and the next, in ticks; twenty make a second.")
                .defineInRange("autoScanInterval", 20, 5, 200);
        builder.pop();

        builder.comment("Drawing an outline around the bedrock waiting to be mined.")
                .push("ESP");
        ESP_ENABLED = builder
                .comment("Outlines the blocks in the queue, through walls, so you can see what the",
                        "miner is going to reach.")
                .define("espEnabled", true);
        ESP_COLOR = builder
                .comment("Outline colour as six hex digits, red-green-blue. FFAA00 is amber.",
                        "A value that is not readable falls back to amber and says so.")
                .define("espColor", "FFAA00");
        ESP_ALPHA = builder
                .comment("How solid the outline looks: 0 is invisible, 1 is fully opaque.")
                .defineInRange("espAlpha", 1.0D, 0.0D, 1.0D);
        builder.pop();

        SPEC = builder.build();
    }
}
