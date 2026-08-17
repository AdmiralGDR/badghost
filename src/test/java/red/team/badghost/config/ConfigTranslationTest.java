// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated settings screen names every option by looking up
 * {@code badghost.configuration.<key>}; a missing entry shows the raw key to the player instead
 * of a name. Nothing in the build catches that, so it is pinned here — an option added without a
 * translation fails this test rather than shipping a broken-looking screen.
 */
class ConfigTranslationTest {

    private static final String PREFIX = "badghost.configuration.";
    private static final List<String> LANGUAGES = List.of("en_us", "ru_ru");

    /** Sections and options declared by the spec, exactly as the screen will ask for them. */
    private static final List<String> KEYS = List.of(
            "Visuals", "Template", "Automation", "Preview", "AutoScan", "ESP",
            "ghostBlock", "frozenSlippery", "bouncy", "disableNegatives", "modelOffset",
            "cameraDistance", "ghostLimit",
            "templateShape", "templateSize",
            "automationEnabled", "planMode", "supportBlock", "limitMax", "maxRetries",
            "waitTicks", "rotateSettleTicks", "skipInstaMineCheck",
            "previewEnabled",
            "autoScanEnabled", "autoScanRadius", "autoScanInterval",
            "espEnabled", "espColor", "espAlpha");

    private static JsonObject lang(String language) {
        String path = "/assets/badghost/lang/" + language + ".json";
        try (InputStream in = ConfigTranslationTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing language file " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    @DisplayName("every settings key is named in every language")
    void everyKeyIsTranslated() {
        for (String language : LANGUAGES) {
            JsonObject json = lang(language);
            List<String> missing = new ArrayList<>();
            for (String key : KEYS) {
                String full = PREFIX + key;
                if (!json.has(full) || json.get(full).getAsString().isBlank()) {
                    missing.add(full);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
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
                String value = json.get(key).getAsString();
                assertTrue(!value.equals(key), language + ": " + key + " is untranslated");
                assertTrue(!value.isBlank(), language + ": " + key + " is blank");
            }
        }
    }
}
