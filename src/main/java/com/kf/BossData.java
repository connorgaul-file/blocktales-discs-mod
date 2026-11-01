package com.kf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//parseAll() + install() refresh the visuals (resource packs can override this i think)
public final class BossData {

    private static final Component DEFAULT_NAME = Component.literal("Music Disc");

    public record Timing(long originalStays, long originalScrambles, long phraseAppears,
                         long phraseStays, long phraseScrambles, long originalReturns) {
        public long cycle() {
            return originalStays + originalScrambles + phraseAppears + phraseStays + phraseScrambles + originalReturns;
        }
    }

    public record Glitch(long tickMs, float burstChance, float letterChance, float flickerAmount) {}

    public record Phrases(int[] gradient, List<String> list, Timing timing, Glitch glitch) {}

    public record Boss(String id, String disc, @Nullable PulsingName name,
                       TooltipFrames.@Nullable Frame frame, @Nullable Phrases phrases) {

        public double speed() {
            return frame == null ? 0 : frame.speed();
        }

        public Component displayName(long nowMs) {
            return name == null ? DEFAULT_NAME : name.at(speed(), nowMs);
        }
    }

    private static volatile Map<String, Boss> byId = Map.of();
    private static volatile Map<String, String> idByDisc = Map.of();

    public static @Nullable Boss get(String id) {
        return byId.get(id);
    }

    public static @Nullable String bossIdForDisc(String discPath) {
        return idByDisc.get(discPath);
    }

    public static @Nullable Boss forDisc(String discPath) {
        String id = idByDisc.get(discPath);
        return id == null ? null : byId.get(id);
    }

    public static boolean isBoss(String discPath) {
        return idByDisc.containsKey(discPath);
    }

    public static void loadFromJar(Path dir) {
        Map<String, Boss> parsed = new HashMap<>();
        try (var files = Files.walk(dir)) {
            files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(file -> {
                String id = file.getFileName().toString().replace(".json", "");
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    parsed.put(id, parse(id, reader));
                } catch (Exception e) {
                    System.err.println("[Discs] failed loading boss file " + file + " (" + e.getMessage() + ")");
                }
            });
        } catch (Exception e) {
            System.err.println("[Discs] error walking bosses directory: " + e.getMessage());
        }

        Map<String, String> discs = new HashMap<>();
        parsed.values().forEach(boss -> discs.put(boss.disc(), boss.id()));
        byId = Map.copyOf(parsed);
        idByDisc = Map.copyOf(discs);
    }

    public static Map<String, Boss> parseAll(ResourceManager manager) {
        FileToIdConverter converter = FileToIdConverter.json("bosses");
        Map<String, Boss> parsed = new HashMap<>();

        converter.listMatchingResources(manager).forEach((file, resource) -> {
            if (!file.getNamespace().equals(Discs.MOD_ID)) return;
            String id = converter.fileToId(file).getPath();
            try (BufferedReader reader = resource.openAsReader()) {
                parsed.put(id, parse(id, reader));
            } catch (Exception e) {
                System.err.println("[Discs] failed reloading boss file " + file + " (" + e.getMessage() + ")");
            }
        });

        return parsed;
    }

    public static void install(Map<String, Boss> parsed) {
        byId = Map.copyOf(parsed);
    }

    private static Boss parse(String id, Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        String disc = json.get("disc").getAsString();

        PulsingName name = null;
        if (json.has("name")) {
            String text = json.has("text") ? json.get("text").getAsString() : "Music Disc";
            name = PulsingName.fromStops(text, colors(json.getAsJsonArray("name")));
        }

        TooltipFrames.Frame frame = null;
        if (json.has("outer")) {
            JsonObject outer = json.getAsJsonObject("outer");

            TooltipFrames.Background background = null;
            if (json.has("frame")) {
                JsonObject inner = json.getAsJsonObject("frame");
                background = new TooltipFrames.Background(
                        color(inner.get("first")), color(inner.get("second")),
                        inner.has("softness") ? inner.get("softness").getAsFloat() : TooltipFrames.DEFAULT_SOFTNESS);
            }

            frame = new TooltipFrames.Frame(
                    ARGB.opaque(color(outer.get("top"))), ARGB.opaque(color(outer.get("bottom"))),
                    outer.has("speed") ? outer.get("speed").getAsDouble() : 0, background);
        }

        Phrases phrases = null;
        if (json.has("phrases")) {
            JsonObject p = json.getAsJsonObject("phrases");
            List<String> list = new ArrayList<>();
            p.getAsJsonArray("list").forEach(el -> list.add(el.getAsString()));
            if (!list.isEmpty()) {
                phrases = new Phrases(colors(p.getAsJsonArray("gradient")), List.copyOf(list),
                        timing(obj(p, "timing")), glitch(obj(p, "glitch")));
            }
        }

        return new Boss(id, disc, name, frame, phrases);
    }

    private static Timing timing(JsonObject o) {
        return new Timing(
                num(o, "original_stays", 36000), num(o, "original_scrambles", 2400),
                num(o, "phrase_appears", 1200), num(o, "phrase_stays", 2600),
                num(o, "phrase_scrambles", 1200), num(o, "original_returns", 1800));
    }

    private static Glitch glitch(JsonObject o) {
        return new Glitch(num(o, "tick", 100),
                (float) dec(o, "burst_chance", 0.24), (float) dec(o, "letter_chance", 0.2), (float) dec(o, "flicker", 0.2));
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static long num(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }

    private static double dec(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static int color(JsonElement el) {
        return Integer.parseInt(el.getAsString().replace("#", "").trim(), 16) & 0xFFFFFF;
    }

    private static int[] colors(JsonArray array) {
        int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = color(array.get(i));
        }
        return result;
    }
}
