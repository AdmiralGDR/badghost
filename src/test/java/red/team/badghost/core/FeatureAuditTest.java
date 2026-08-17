// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audit is only worth having if it covers everything that can silently die.
 *
 * <p>A mixin added later without a matching probe would be exactly the blind spot the audit
 * exists to remove — it would report all-clear while a feature was dead. That drift is what
 * these tests catch. Whether a mixin actually applied is a question only the running game can
 * answer, and the self-test asks it there; nothing here pretends otherwise.</p>
 */
class FeatureAuditTest {

    /** Which capability each shipped mixin carries. Kept here so a new mixin must be classified. */
    private static final Map<String, String> MIXIN_FEATURE = new LinkedHashMap<>(Map.of(
            "ServerboundMovePlayerPacketMixin", "rotation",
            "BlockFrictionMixin", "friction",
            "EntityBounceMixin", "bounce",
            "ConfusionOverlayMixin", "negatives",
            "ClientSendMixin", "packets"));

    private static final List<String> LANGUAGES = List.of("en_us", "ru_ru");

    private static JsonObject resourceJson(String path) {
        try (InputStream in = FeatureAuditTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static List<String> shippedMixins() {
        JsonObject config = resourceJson("/badghost.mixins.json");
        List<String> names = new ArrayList<>();
        for (String array : List.of("mixins", "client", "server")) {
            if (config.has(array)) {
                config.getAsJsonArray(array).forEach(e -> names.add(e.getAsString()));
            }
        }
        return names;
    }

    @Test
    @DisplayName("every shipped mixin is classified under some capability")
    void everyMixinIsClassified() {
        List<String> unclassified = new ArrayList<>();
        for (String mixin : shippedMixins()) {
            if (!MIXIN_FEATURE.containsKey(mixin)) {
                unclassified.add(mixin);
            }
        }
        assertTrue(unclassified.isEmpty(),
                "these mixins can die silently because nothing probes them: " + unclassified);
    }

    @Test
    @DisplayName("no capability is classified for a mixin that is no longer shipped")
    void noStaleClassifications() {
        List<String> shipped = shippedMixins();
        List<String> stale = new ArrayList<>();
        for (String mixin : MIXIN_FEATURE.keySet()) {
            if (!shipped.contains(mixin)) {
                stale.add(mixin);
            }
        }
        assertTrue(stale.isEmpty(), "probed but not shipped any more: " + stale);
    }

    @Test
    @DisplayName("every capability has a name in every language")
    void everyFeatureIsNamed() {
        for (String language : LANGUAGES) {
            JsonObject json = resourceJson("/assets/badghost/lang/" + language + ".json");
            List<String> missing = new ArrayList<>();
            for (String feature : MIXIN_FEATURE.values()) {
                String key = "badghost.feature." + feature;
                if (!json.has(key) || json.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
            // The key is built by concatenation at the call site, so the shell gate in
            // scripts/test.sh cannot see it; this is the only thing standing between a new
            // capability and a raw identifier shown to the player.
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    @Test
    @DisplayName("every verdict the audit command can print is translated")
    void everyHealthIsTranslated() {
        // Built as "badghost.command.audit." + health, so the shell gate cannot see these either.
        for (String language : LANGUAGES) {
            JsonObject json = resourceJson("/assets/badghost/lang/" + language + ".json");
            List<String> missing = new ArrayList<>();
            for (FeatureAudit.Health health : FeatureAudit.Health.values()) {
                String key = "badghost.command.audit."
                        + health.name().toLowerCase(java.util.Locale.ROOT);
                if (!json.has(key) || json.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }
}
