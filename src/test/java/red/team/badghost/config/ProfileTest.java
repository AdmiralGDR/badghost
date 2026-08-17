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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Name handling for the profile command, and the names it shows the player.
 *
 * <p>Whether a profile writes the right values is a question for the running game — the settings
 * have to be loaded before they can be written — and the self-test asks it there. What can be
 * settled here is that a name typed in any reasonable way finds its profile, and that a name typed
 * wrongly comes back as "no such profile" rather than as an exception, which in a command tree
 * would mean the line escaping to the server.</p>
 */
class ProfileTest {

    private static final List<String> LANGUAGES = List.of("en_us", "ru_ru");

    private static JsonObject lang(String language) {
        String path = "/assets/badghost/lang/" + language + ".json";
        try (InputStream in = ProfileTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing language file " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    @DisplayName("a name is found however it is capitalised or padded")
    void nameLookupIsForgiving() {
        for (Profile profile : Profile.values()) {
            String key = profile.key();
            assertSame(profile, Profile.byName(key));
            assertSame(profile, Profile.byName(key.toUpperCase(java.util.Locale.ROOT)));
            assertSame(profile, Profile.byName("  " + key + "  "));
        }
    }

    @Test
    @DisplayName("an unknown name is an answer, not an exception")
    void unknownNameReturnsNull() {
        // The command validates in its executor for exactly this reason: a throw here would become
        // a Brigadier syntax error, and NeoForge hands those to the server.
        for (String bogus : List.of("", " ", "nope", "saf", "safeish", "профиль", "12", "-")) {
            assertNull(Profile.byName(bogus), "expected no match for '" + bogus + "'");
        }
    }

    @Test
    @DisplayName("names are unique and offered in a stable order")
    void namesAreUnique() {
        List<String> names = Profile.names();
        assertEquals(Profile.values().length, names.size());
        assertEquals(new HashSet<>(names).size(), names.size(), "duplicate profile name");
        assertEquals(names, Profile.names(), "the order shown to the player must not wander");
    }

    @Test
    @DisplayName("every profile is named in every language")
    void everyProfileIsNamed() {
        for (String language : LANGUAGES) {
            JsonObject json = lang(language);
            List<String> missing = new ArrayList<>();
            for (Profile profile : Profile.values()) {
                String key = profile.translationKey();
                if (!json.has(key) || json.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    @Test
    @DisplayName("no two profiles share a translation key")
    void translationKeysAreDistinct() {
        Set<String> keys = new HashSet<>();
        for (Profile profile : Profile.values()) {
            assertTrue(keys.add(profile.translationKey()), "duplicate key " + profile.translationKey());
        }
    }
}
