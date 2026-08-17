// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings screen must read like something a person wrote, in whichever language it is read in.
 *
 * <p>Two ways it can fail quietly. A missing {@code badghost.configuration.<key>} shows the player a
 * raw identifier where a name should be. A missing {@code .tooltip} is subtler: the screen falls
 * back to the comment out of the config file, which exists only in English, so a Russian player gets
 * a Russian label above an English explanation and nothing anywhere reports it.</p>
 *
 * <p>The list of settings is taken from the spec itself rather than repeated here. An earlier
 * version kept it by hand, which meant a setting added to the spec and forgotten here was simply not
 * checked — the test would keep passing while the screen showed an identifier.</p>
 */
class ConfigTranslationTest {

    private static final String PREFIX = "badghost.configuration.";
    private static final List<String> LANGUAGES = List.of("en_us", "ru_ru");

    /** Every leaf setting name in the spec. Leaf, because that is the key the screen looks up. */
    private static final Set<String> OPTIONS = new LinkedHashSet<>();
    /** Every section name in the spec. */
    private static final Set<String> SECTIONS = new LinkedHashSet<>();

    static {
        collect(BadghostConfig.SPEC.getSpec());
    }

    private static void collect(UnmodifiableConfig config) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            if (entry.getRawValue() instanceof UnmodifiableConfig section) {
                SECTIONS.add(entry.getKey());
                collect(section);
            } else {
                OPTIONS.add(entry.getKey());
            }
        }
    }

    /** Everything the screen will ask for a name for. */
    private static Set<String> named() {
        Set<String> all = new LinkedHashSet<>(SECTIONS);
        all.addAll(OPTIONS);
        return all;
    }

    private static JsonObject lang(String language) {
        String path = "/assets/badghost/lang/" + language + ".json";
        try (InputStream in = ConfigTranslationTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing language file " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static String value(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : null;
    }

    @Test
    @DisplayName("the spec was read, so the rest of this test means something")
    void specWasWalked() {
        // Without this, a spec that failed to walk would leave every list empty and every assertion
        // below vacuously true.
        assertTrue(OPTIONS.size() >= 20, "only found " + OPTIONS.size() + " settings in the spec");
        assertTrue(SECTIONS.size() >= 5, "only found " + SECTIONS.size() + " sections in the spec");
    }

    @Test
    @DisplayName("every setting and section is named in every language")
    void everythingIsNamed() {
        for (String language : LANGUAGES) {
            JsonObject json = lang(language);
            List<String> missing = new ArrayList<>();
            for (String key : named()) {
                String text = value(json, PREFIX + key);
                if (text == null || text.isBlank()) {
                    missing.add(PREFIX + key);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    @Test
    @DisplayName("every setting and section explains itself in every language")
    void everythingHasATranslatedTooltip() {
        for (String language : LANGUAGES) {
            JsonObject json = lang(language);
            List<String> missing = new ArrayList<>();
            for (String key : named()) {
                String text = value(json, PREFIX + key + ".tooltip");
                if (text == null || text.isBlank()) {
                    missing.add(PREFIX + key + ".tooltip");
                }
            }
            assertTrue(missing.isEmpty(),
                    language + " would fall back to the English config comment for " + missing);
        }
    }

    @Test
    @DisplayName("an explanation says more than the name it sits under")
    void tooltipsAreNotJustTheLabelAgain() {
        for (String language : LANGUAGES) {
            JsonObject json = lang(language);
            for (String key : named()) {
                String label = value(json, PREFIX + key);
                String tooltip = value(json, PREFIX + key + ".tooltip");
                assertFalse(tooltip.equals(label),
                        language + ": " + key + " repeats its name instead of explaining it");
                assertTrue(tooltip.length() > label.length(),
                        language + ": " + key + " has an explanation shorter than its own name");
            }
        }
    }

    @Test
    @DisplayName("no translation is left over from a setting that no longer exists")
    void noStaleTranslations() {
        Set<String> live = new LinkedHashSet<>();
        for (String key : named()) {
            live.add(PREFIX + key);
            live.add(PREFIX + key + ".tooltip");
        }
        for (String language : LANGUAGES) {
            List<String> stale = new ArrayList<>();
            for (String key : lang(language).keySet()) {
                if (key.startsWith(PREFIX) && !live.contains(key)) {
                    stale.add(key);
                }
            }
            // A renamed setting leaves its old text behind, where it reads as though the option is
            // still there.
            assertTrue(stale.isEmpty(), language + " still describes settings that are gone: " + stale);
        }
    }

    @Test
    @DisplayName("the languages describe the same set of keys")
    void languagesAgree() {
        Set<String> en = lang("en_us").keySet();
        Set<String> ru = lang("ru_ru").keySet();

        List<String> onlyEn = new ArrayList<>(en);
        onlyEn.removeAll(ru);
        List<String> onlyRu = new ArrayList<>(ru);
        onlyRu.removeAll(en);

        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(),
                "only in en_us: " + onlyEn + "; only in ru_ru: " + onlyRu);
    }

    @Test
    @DisplayName("no translation is left as its own key")
    void noPlaceholderValues() {
        for (String language : LANGUAGES) {
            JsonObject json = lang(language);
            for (String key : json.keySet()) {
                String text = json.get(key).getAsString();
                assertFalse(text.equals(key), language + ": " + key + " is untranslated");
                assertFalse(text.isBlank(), language + ": " + key + " is blank");
            }
        }
    }
}
