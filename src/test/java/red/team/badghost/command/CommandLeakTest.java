// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proof that no {@code /badghost} input can escape to the server.
 *
 * <p>NeoForge decides whether to forward a command by what Brigadier does with it: after parsing,
 * if any input is left unread the dispatcher throws unknown-command or unknown-argument, and the
 * handler answers "not mine" so the server gets the line. For this mod that would mean a typo
 * putting the word {@code badghost} into a stranger's server log — the mod betraying its own
 * presence through nothing but a slip of the finger.</p>
 *
 * <p>So the property under test is exact and mechanical: <em>for every input starting with the
 * root, parsing must leave nothing unread.</em> Not "the command works" — that is a different
 * question — but "the input stops here".</p>
 */
class CommandLeakTest {

    private static CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll
    static void buildTree() {
        dispatcher = new CommandDispatcher<>();
        dispatcher.register(BadghostCommands.tree());
    }

    /**
     * Parses as NeoForge does. The source is null on purpose: the tree sets no requirements, so
     * Brigadier never consults it while parsing, and demanding a real client here would only test
     * the harness.
     */
    private static ParseResults<CommandSourceStack> parse(String input) {
        return dispatcher.parse(input, null);
    }

    /**
     * What the game actually dispatches for a typed line.
     *
     * <p>Mirrors {@code ChatScreen#normalizeChatMessage}, which is
     * {@code StringUtil.trimChatMessage(StringUtils.normalizeSpace(input.trim()))}: outer blanks
     * go, and runs of whitespace collapse to one space. Inputs are put through this because
     * asserting on shapes the game cannot produce would fail the suite over nothing — see
     * {@link #trailingBlankIsRemovedBeforeDispatch()} for the one shape that matters.</p>
     */
    private static String asDispatched(String typed) {
        return typed.trim().replaceAll("\\s+", " ");
    }

    /** The condition under which NeoForge keeps the command instead of forwarding it. */
    private static void assertStaysLocal(String typed) {
        String input = asDispatched(typed);
        ParseResults<CommandSourceStack> results = parse(input);
        String leftover = results.getReader().getRemaining();
        assertFalse(results.getReader().canRead(),
                "would be forwarded to the server, unread: '" + leftover + "' from input: " + input);
    }

    private static final List<String> HAND_PICKED = List.of(
            "badghost",
            "badghost ",
            "badghost  ",
            "badghost help",
            "badghost stats",
            "badghost queue",
            "badghost why",
            "badghost audit",
            "badghost undo",
            "badghost clear",
            "badghost profile",
            "badghost profile safe",
            "badghost profile fast",
            "badghost profile debug",
            // Wrong values must be a message, not a syntax error.
            "badghost profile bogus",
            "badghost profile SAFE",
            "badghost profile safe extra",
            "badghost profile 12345",
            // Mistyped subcommands: the case that would have leaked.
            "badghost stst",
            "badghost stat",
            "badghost sttaus",
            "badghost hlep",
            // Trailing rubbish after a valid subcommand.
            "badghost stats extra words here",
            "badghost template",
            "badghost template wall",
            "badghost template wall 5",
            "badghost template wall 99",
            "badghost template wall abc",
            "badghost template bogus",
            "badghost template wall 5 extra junk",
            "badghost undo undo undo",
            "badghost audit --verbose",
            "badghost clear;;;",
            "badghost why?",
            // Shapes that tend to break hand-rolled parsers.
            "badghost -",
            "badghost --",
            "badghost \"quoted string\"",
            "badghost 'single'",
            "badghost {json:true}",
            "badghost a\tb",
            "badghost   multiple   spaces   ",
            "badghost ünïcødé",
            "badghost 日本語",
            "badghost ../../etc/passwd",
            "badghost $(whoami)",
            "badghost %s%s%n",
            "badghost \\",
            "badghost PROFILE safe",
            "badghost Stats");

    @Test
    @DisplayName("no hand-picked input is left half-parsed")
    void handPickedInputsStayLocal() {
        for (String input : HAND_PICKED) {
            assertStaysLocal(input);
        }
    }

    @Test
    @DisplayName("no random input under the root escapes either")
    void fuzzedInputsStayLocal() {
        // Fixed seed: a leak found here must be reproducible, not a story about one lucky run.
        Random random = new Random(20260817L);
        String alphabet = "abz09 _-.:/\\\"'{}[]$%&*#@!?\té中";
        for (int i = 0; i < 2000; i++) {
            // The separator is deliberate: without it the result is a different command name
            // altogether, which is not ours to answer for and belongs to the server.
            StringBuilder input = new StringBuilder("badghost ");
            int length = random.nextInt(24);
            for (int c = 0; c < length; c++) {
                input.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertStaysLocal(input.toString());
        }
    }

    @Test
    @DisplayName("a lone trailing blank is removed by the game before dispatch")
    void trailingBlankIsRemovedBeforeDispatch() {
        // The one shape no command tree can absorb, and the reason inputs are normalised above.
        // Brigadier only descends into a node's children when at least two characters remain
        // (verified in CommandDispatcher: canRead(2) without a redirect, canRead(1) with one), so
        // with a single trailing space the children are never offered it and it stays unread —
        // which is precisely what makes NeoForge hand the line to the server.
        assertTrue(parse("badghost ").getReader().canRead(),
                "if this ever stops holding, the normalisation below is no longer load-bearing");

        // What saves it is that the game trims and collapses whitespace first, so the dispatcher
        // never sees that shape from a typed command.
        assertEquals("badghost", asDispatched("badghost "));
        assertEquals("badghost", asDispatched("  badghost   "));
        assertEquals("badghost stats", asDispatched("badghost \t stats "));
    }

    @Test
    @DisplayName("every subcommand is reachable and not shadowed by the catch-all")
    void literalsWinOverTheCatchAll() {
        for (String name : List.of("help", "stats", "queue", "why", "audit", "undo", "clear",
                "profile", "template")) {
            List<ParsedCommandNode<CommandSourceStack>> nodes =
                    parse("badghost " + name).getContext().getNodes();
            List<String> path = new ArrayList<>();
            nodes.forEach(node -> path.add(node.getNode().getName()));
            assertEquals(List.of(BadghostCommands.ROOT, name), path,
                    "the greedy fallback swallowed a real subcommand");
        }
    }

    @Test
    @DisplayName("commands that are not ours are left for the server")
    void foreignCommandsAreNotHijacked() {
        // The other half of the contract: swallowing everything would break vanilla commands.
        for (String input : List.of("gamemode creative", "time set day", "badghostly", "help")) {
            ParseResults<CommandSourceStack> results = parse(input);
            assertTrue(results.getReader().canRead(),
                    "this mod must not answer for: " + input);
        }
    }
}
