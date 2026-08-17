// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.MinerTask;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.config.Profile;
import red.team.badghost.core.FeatureAudit;
import red.team.badghost.core.SessionStats;
import red.team.badghost.visuals.GhostBlockRegistry;
import red.team.badghost.visuals.VisualService;
import red.team.badghost.visuals.template.GhostTemplate;

import java.util.List;

/**
 * Client-side {@code /badghost} commands: what the mod knows, asked for on demand.
 *
 * <p>The HUD can only hold a few lines and a chat message scrolls away, so anything you might want
 * to look up after the fact — why the last target was abandoned, what this session has managed,
 * whether a feature is even wired in — needs somewhere to be asked for. That is what these are.</p>
 *
 * <h2>Why the tree is total</h2>
 *
 * <p>NeoForge's client command handler runs a command locally and, when Brigadier reports the
 * command or an argument unknown, returns false so <em>the server</em> can try instead. That is
 * sensible for a chat line meant for the server and disastrous here: mistyping a subcommand would
 * hand {@code /badghost stst} to the server, and the mod would have announced itself in someone
 * else's log through nothing but a typo.</p>
 *
 * <p>So every input beginning with the root must parse to completion locally. Two rules keep that
 * true, and {@code CommandLeakTest} holds them:</p>
 *
 * <ol>
 *   <li>every value is a {@code greedyString} in final position, validated in the executor — so a
 *       wrong value is a message, never a syntax error, and nothing is left unread;</li>
 *   <li>every literal accepts trailing text as well, since leftover input after an otherwise
 *       valid command is also reported as an unknown argument.</li>
 * </ol>
 */
public final class BadghostCommands {
    private BadghostCommands() {}

    public static final String ROOT = "badghost";

    /** Argument name for everything the tree swallows on purpose. */
    private static final String REST = "rest";

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tree());
    }

    /** Built separately from registration so the shape can be examined without a client. */
    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal(ROOT)
                .executes(BadghostCommands::help)
                .then(subcommand("help", BadghostCommands::help))
                .then(subcommand("stats", BadghostCommands::stats))
                .then(subcommand("queue", BadghostCommands::queue))
                .then(subcommand("why", BadghostCommands::why))
                .then(subcommand("audit", BadghostCommands::audit))
                .then(subcommand("undo", BadghostCommands::undo))
                .then(subcommand("clear", BadghostCommands::clear))
                .then(Commands.literal("profile")
                        .executes(BadghostCommands::listProfiles)
                        .then(Commands.argument(REST, StringArgumentType.greedyString())
                                .executes(BadghostCommands::applyProfile)))
                .then(Commands.literal("template")
                        .executes(BadghostCommands::showTemplate)
                        .then(Commands.argument(REST, StringArgumentType.greedyString())
                                .executes(BadghostCommands::setTemplate)))
                // Last resort, reached only when no literal above matched. Brigadier offers
                // literals ahead of arguments, so this shadows nothing.
                .then(Commands.argument(REST, StringArgumentType.greedyString())
                        .executes(BadghostCommands::unknown));
    }

    /**
     * A subcommand that takes no value, and tolerates being given one anyway.
     *
     * <p>The trailing branch says what it ignored instead of pretending the input was clean: a
     * command that quietly drops half of what you typed teaches you to distrust it.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> subcommand(
            String name, Command<CommandSourceStack> action) {
        return Commands.literal(name)
                .executes(action)
                .then(Commands.argument(REST, StringArgumentType.greedyString())
                        .executes(context -> {
                            say(context, Component.translatable("badghost.command.trailing_ignored",
                                    StringArgumentType.getString(context, REST)));
                            return action.run(context);
                        }));
    }

    private static void say(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendSuccess(() -> text, false);
    }

    // -- subcommands --

    private static int help(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("badghost.command.help.title"));
        for (String name : List.of("stats", "queue", "why", "audit", "undo", "clear",
                "profile", "template")) {
            say(context, Component.translatable("badghost.command.help." + name));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        if (SessionStats.broken() == 0 && SessionStats.failed() == 0) {
            say(context, Component.translatable("badghost.command.stats.none"));
            return Command.SINGLE_SUCCESS;
        }
        say(context, Component.translatable("badghost.command.stats",
                SessionStats.broken(),
                SessionStats.failed(),
                tenths(SessionStats.attemptsPerBreakTenths()),
                tenths((int) (SessionStats.averageTicksPerBlock() / 2))));
        return Command.SINGLE_SUCCESS;
    }

    private static int queue(CommandContext<CommandSourceStack> context) {
        List<MinerTask> tasks = AutomationEngine.getActiveTasks();
        if (tasks.isEmpty()) {
            say(context, Component.translatable("badghost.command.queue.empty"));
            return Command.SINGLE_SUCCESS;
        }
        say(context, Component.translatable("badghost.command.queue", tasks.size()));
        for (int i = 0; i < tasks.size(); i++) {
            MinerTask task = tasks.get(i);
            say(context, Component.translatable("badghost.command.queue.entry",
                    task.getTarget().toShortString(), task.getState().name(), task.ticksSpent()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int why(CommandContext<CommandSourceStack> context) {
        String failure = AutomationEngine.lastFailure();
        say(context, failure == null
                ? Component.translatable("badghost.command.why.none")
                : Component.translatable("badghost.command.why", Component.translatable(failure)));
        return Command.SINGLE_SUCCESS;
    }

    private static int audit(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("badghost.command.audit.title"));
        for (FeatureAudit.Row row : FeatureAudit.report()) {
            String fired = row.fired() == FeatureAudit.NOT_COUNTED
                    ? "-" : Integer.toString(row.fired());
            say(context, Component.translatable(
                    "badghost.command.audit." + row.health().name().toLowerCase(java.util.Locale.ROOT),
                    row.name(), fired));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int undo(CommandContext<CommandSourceStack> context) {
        VisualService.undoLast();
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        int before = GhostBlockRegistry.size();
        VisualService.clearAll();
        if (before == 0) {
            // clearAll already says so, but a command with no visible effect deserves a reason
            // in the same place the command was typed.
            say(context, Component.translatable("badghost.command.clear.none"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listProfiles(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("badghost.command.profile.usage",
                String.join(", ", Profile.names())));
        return Command.SINGLE_SUCCESS;
    }

    private static int applyProfile(CommandContext<CommandSourceStack> context) {
        String wanted = StringArgumentType.getString(context, REST);
        Profile profile = Profile.byName(wanted);
        if (profile == null) {
            say(context, Component.translatable("badghost.command.profile.unknown",
                    wanted, String.join(", ", Profile.names())));
            return 0;
        }
        if (!profile.apply()) {
            say(context, Component.translatable("badghost.command.profile.unsaved"));
            return 0;
        }
        say(context, Component.translatable("badghost.command.profile.applied",
                Component.translatable(profile.translationKey())));
        return Command.SINGLE_SUCCESS;
    }

    private static int showTemplate(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("badghost.command.template.usage",
                Component.translatable(BadghostConfig.TEMPLATE_SHAPE.get().translationKey()),
                BadghostConfig.TEMPLATE_SIZE.get(),
                String.join(", ", GhostTemplate.names())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reads {@code <shape> [size]} out of one greedy argument.
     *
     * <p>Split here rather than by Brigadier because a wrong word must be an answer, not a syntax
     * error: a syntax error is what sends the line to the server.</p>
     */
    private static int setTemplate(CommandContext<CommandSourceStack> context) {
        String[] words = StringArgumentType.getString(context, REST).trim().split("\\s+");
        GhostTemplate shape = GhostTemplate.byName(words[0]);
        if (shape == null) {
            say(context, Component.translatable("badghost.command.template.unknown",
                    words[0], String.join(", ", GhostTemplate.names())));
            return 0;
        }

        int size = BadghostConfig.TEMPLATE_SIZE.get();
        if (words.length > 1) {
            try {
                size = Math.clamp(Integer.parseInt(words[1]), 1, GhostTemplate.MAX_SIZE);
            } catch (NumberFormatException ignored) {
                // Out of range or not a number at all: the shape still gets set, and the size
                // simply stays as it was rather than the whole command failing.
                size = BadghostConfig.TEMPLATE_SIZE.get();
            }
        }

        BadghostConfig.TEMPLATE_SHAPE.set(shape);
        BadghostConfig.TEMPLATE_SIZE.set(size);
        BadghostConfig.SPEC.save();
        say(context, Component.translatable("badghost.command.template.set",
                Component.translatable(shape.translationKey()), size));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Anything under the root that matched no subcommand.
     *
     * <p>This exists to keep the input here rather than let it reach the server, so it must always
     * succeed — and it says what it did not understand, because a command that answers nothing is
     * indistinguishable from a broken one.</p>
     */
    private static int unknown(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("badghost.command.unknown",
                StringArgumentType.getString(context, REST)));
        return help(context);
    }

    /** One decimal without dragging in locale-dependent formatting. */
    private static String tenths(int value) {
        return (value / 10) + "." + Math.abs(value % 10);
    }
}
