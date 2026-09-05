package com.andye.warmod.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NuclearWastelandBiomeResourceTest {
    private static final String BIOME_RESOURCE =
        "data/war_mod/worldgen/biome/nuclear_wasteland.json";

    @Test
    void preservesAshAmbienceWithoutNetherMobSpawns() throws IOException {
        JsonObject biome = readResource(BIOME_RESOURCE);
        JsonObject attributes = biome.getAsJsonObject("attributes");

        assertEquals("#51515a",
            attributes.get("minecraft:visual/sky_color").getAsString());
        assertEquals("#565044",
            attributes.get("minecraft:visual/fog_color").getAsString());
        assertEquals("#302a18",
            attributes.get("minecraft:visual/water_fog_color").getAsString());
        assertEquals("#6a5b2b",
            biome.getAsJsonObject("effects").get("water_color").getAsString());
        assertEquals("minecraft:white_ash",
            attributes.getAsJsonArray("minecraft:visual/ambient_particles")
                .get(0).getAsJsonObject()
                .getAsJsonObject("particle")
                .get("type").getAsString());
        assertTrue(attributes.has("minecraft:audio/ambient_sounds"));
        assertTrue(attributes.getAsJsonArray("minecraft:visual/ambient_particles")
            .get(0).getAsJsonObject().get("probability").getAsDouble() <= 0.02,
            "Vanilla ash is only a low-rate fallback for the wasteland atmosphere");

        JsonObject spawners = biome.getAsJsonObject("spawners");
        Set<String> mobTypes = new HashSet<>();
        for (JsonElement category : spawners.asMap().values()) {
            for (JsonElement spawn : category.getAsJsonArray()) {
                mobTypes.add(spawn.getAsJsonObject().get("type").getAsString());
            }
        }

        assertFalse(mobTypes.contains("minecraft:ghast"));
        assertFalse(mobTypes.contains("minecraft:magma_cube"));
        assertFalse(mobTypes.contains("minecraft:strider"));
        assertTrue(mobTypes.contains("minecraft:cow"));
        assertTrue(mobTypes.contains("minecraft:zombie"));
    }

    @Test
    void isClassifiedAsOverworldBiome() throws IOException {
        JsonObject tag = readResource(
            "data/minecraft/tags/worldgen/biome/is_overworld.json");
        JsonArray values = tag.getAsJsonArray("values");

        assertTrue(values.asList().stream()
            .map(JsonElement::getAsString)
            .anyMatch("war_mod:nuclear_wasteland"::equals));
    }

    private static JsonObject readResource(final String path) throws IOException {
        try (InputStream stream = NuclearWastelandBiomeResourceTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing test resource: " + path);
            try (InputStreamReader reader = new InputStreamReader(
                    stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
