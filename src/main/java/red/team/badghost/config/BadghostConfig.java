// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import red.team.badghost.automation.plan.PlanMode;

/** Client config spec. Never registered on a dedicated server. */
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

    // Bedrock miner
    public static final ModConfigSpec.BooleanValue AUTOMATION_ENABLED;
    public static final ModConfigSpec.EnumValue<PlanMode> PLAN_MODE;
    public static final ModConfigSpec.ConfigValue<String> SUPPORT_BLOCK;
    public static final ModConfigSpec.IntValue LIMIT_MAX;
    public static final ModConfigSpec.IntValue MINER_MAX_RETRIES;
    public static final ModConfigSpec.IntValue WAIT_TICKS;
    public static final ModConfigSpec.IntValue ROTATE_SETTLE_TICKS;
    public static final ModConfigSpec.BooleanValue SKIP_INSTAMINE_CHECK;

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

        builder.push("Visuals");
        GHOST_BLOCK = builder
                .comment("Block id placed by the ghost-block key. Invalid ids are ignored.")
                .define("ghostBlock", "minecraft:bedrock");
        FROZEN_SLIPPERY = builder.define("frozenSlippery", false);
        BOUNCY = builder.define("bouncy", false);
        DISABLE_NEGATIVES = builder.define("disableNegatives", false);
        MODEL_OFFSET = builder.defineInRange("modelOffset", 0.0D, -10.0D, 10.0D);
        CAMERA_DISTANCE = builder.defineInRange("cameraDistance", 4.0D, 1.0D, 100.0D);
        builder.pop();

        builder.push("Automation");
        AUTOMATION_ENABLED = builder
                .comment("Whether the miner starts armed when a world is joined.")
                .define("automationEnabled", false);
        PLAN_MODE = builder
                .comment("VERTICAL_FAST places the piston above or below the target and needs no",
                        "rotation spoofing. ALL_DIRECTION also considers the four side faces but",
                        "has to hold a faked yaw for a tick, which is easier to detect.")
                .defineEnum("planMode", PlanMode.VERTICAL_FAST);
        SUPPORT_BLOCK = builder
                .comment("Block placed under the torch when it would not survive on its own.")
                .define("supportBlock", "minecraft:slime_block");
        LIMIT_MAX = builder
                .comment("Maximum number of targets worked on at once.")
                .defineInRange("limitMax", 1, 1, 10);
        MINER_MAX_RETRIES = builder
                .comment("Attempts per target before it is abandoned.")
                .defineInRange("maxRetries", 3, 1, 10);
        WAIT_TICKS = builder
                .comment("Tick budget for each wait-for-piston-state step.")
                .defineInRange("waitTicks", 20, 5, 100);
        ROTATE_SETTLE_TICKS = builder
                .comment("Ticks the faked yaw is held before the swap, ALL_DIRECTION only.",
                        "Gives the server a tick to apply the rotation before the placement.")
                .defineInRange("rotateSettleTicks", 1, 1, 10);
        SKIP_INSTAMINE_CHECK = builder
                .comment("Accept the fastest available tool even if it cannot instantly break a",
                        "piston. The glitch will usually fail; for debugging only.")
                .define("skipInstaMineCheck", false);
        builder.pop();

        builder.push("AutoScan");
        AUTO_SCAN_ENABLED = builder
                .comment("Queue nearby bedrock without being asked. Off by default: on a bedrock",
                        "floor or the nether roof this consumes pistons indefinitely.")
                .define("autoScanEnabled", false);
        AUTO_SCAN_RADIUS = builder.defineInRange("autoScanRadius", 4, 1, 8);
        AUTO_SCAN_INTERVAL = builder
                .comment("Ticks between scans.")
                .defineInRange("autoScanInterval", 20, 5, 200);
        builder.pop();

        builder.push("ESP");
        ESP_ENABLED = builder
                .comment("Highlight bedrock queued for mining.")
                .define("espEnabled", true);
        ESP_COLOR = builder
                .comment("Outline color, hex RRGGBB.")
                .define("espColor", "FFAA00");
        ESP_ALPHA = builder.defineInRange("espAlpha", 1.0D, 0.0D, 1.0D);
        builder.pop();

        SPEC = builder.build();
    }
}
