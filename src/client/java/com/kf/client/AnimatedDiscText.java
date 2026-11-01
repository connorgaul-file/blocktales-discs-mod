package com.kf.client;

import com.kf.BossData;
import com.kf.Discs;
import com.kf.TooltipFrames;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.JukeboxSong;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

public class AnimatedDiscText {

    private static final Gradient NORMAL_LOOK = Gradient.of(0xAAAAAA);

    public static void init() {
        ItemTooltipCallback.EVENT.register((stack, _, _, lines) -> {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!itemId.getNamespace().equals(Discs.MOD_ID)) return;

            BossData.Boss boss = BossData.forDisc(itemId.getPath());
            if (boss == null || boss.phrases() == null) return;

            String original = songDescription(itemId.getPath());
            if (original == null) return;

            Entry entry = new Entry(original, boss.phrases());
            double speed = boss.speed();
            long now = System.currentTimeMillis();

            lines.replaceAll(line -> line.getString().equals(original) ? entry.line(now, speed) : line);
        });
    }

    private static @Nullable String songDescription(String discPath) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;

        ResourceKey<JukeboxSong> key = ResourceKey.create(Registries.JUKEBOX_SONG,
                Identifier.fromNamespaceAndPath(Discs.MOD_ID, discPath));

        return client.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG)
                .get(key)
                .map(holder -> holder.value().description().getString())
                .orElse(null);
    }

    public static final class Gradient {
        private final int[] stops;

        private Gradient(int[] stops) {
            this.stops = stops;
        }

        public static Gradient of(int... stops) {
            if (stops.length == 0) {
                throw new IllegalArgumentException("a gradient needs at least one color");
            }
            return new Gradient(stops);
        }

        int colorAt(float position, double speed, long nowMs) {
            return TooltipFrames.slidingColor(stops, position, speed, nowMs);
        }
    }

    private record Entry(String original, BossData.Phrases phrases) {

        Component line(long now, double speed) {
            BossData.Timing t = phrases.timing();
            BossData.Glitch g = phrases.glitch();
            Gradient phraseLook = Gradient.of(phrases.gradient());

            long cycleLength = t.cycle();
            long cycleNumber = now / cycleLength;
            long into = now % cycleLength;

            long seed = cycleNumber * 1000 + original.hashCode();
            String phrase = pickPhrase(cycleNumber);

            if (into < t.originalStays()) {
                return sitting(original, NORMAL_LOOK, now, speed, seed + 1, g);
            }
            into -= t.originalStays();

            if (into < t.originalScrambles()) {
                return disappearing(original, NORMAL_LOOK, (float) into / t.originalScrambles(), now, speed, seed + 2, g);
            }
            into -= t.originalScrambles();

            if (into < t.phraseAppears()) {
                return appearing(phrase, phraseLook, (float) into / t.phraseAppears(), now, speed, seed + 3, g);
            }
            into -= t.phraseAppears();

            if (into < t.phraseStays()) {
                return sitting(phrase, phraseLook, now, speed, seed + 4, g);
            }
            into -= t.phraseStays();

            if (into < t.phraseScrambles()) {
                return disappearing(phrase, phraseLook, (float) into / t.phraseScrambles(), now, speed, seed + 5, g);
            }
            into -= t.phraseScrambles();

            return appearing(original, NORMAL_LOOK, (float) into / t.originalReturns(), now, speed, seed + 6, g);
        }

        private String pickPhrase(long cycleNumber) {
            List<String> list = phrases.list();
            int count = list.size();

            if (count <= 2) {
                return list.get((int) Math.floorMod(cycleNumber + original.hashCode(), (long) count));
            }

            long round = cycleNumber / count;
            int slot = (int) (cycleNumber % count);

            List<Integer> order = shuffledOrder(round, count);
            int lastOfPreviousRound = shuffledOrder(round - 1, count).get(count - 1);

            if (order.getFirst() == lastOfPreviousRound) {
                Collections.swap(order, 0, 1);
            }

            return list.get(order.get(slot));
        }

        private List<Integer> shuffledOrder(long round, int count) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                order.add(i);
            }
            Collections.shuffle(order, new Random(mix(round * 31 + original.hashCode())));
            return order;
        }
    }

    private static Component sitting(String text, Gradient look, long now, double speed, long seed, BossData.Glitch g) {
        long glitchTick = now / g.tickMs();
        boolean glitching = noise(seed * 7 + glitchTick) < g.burstChance();
        return build(text, look, now, speed, letter -> glitching && noise(seed * 13 + glitchTick * 57 + letter) < g.letterChance());
    }

    private static Component appearing(String text, Gradient look, float progress, long now, double speed, long seed, BossData.Glitch g) {
        long glitchTick = now / g.tickMs();
        return build(text, look, now, speed, letter -> !hasFlipped(letter, progress, glitchTick, seed, g));
    }

    private static Component disappearing(String text, Gradient look, float progress, long now, double speed, long seed, BossData.Glitch g) {
        long glitchTick = now / g.tickMs();
        return build(text, look, now, speed, letter -> hasFlipped(letter, progress, glitchTick, seed, g));
    }

    private static boolean hasFlipped(int letter, float progress, long glitchTick, long seed, BossData.Glitch g) {
        float flipMoment = 0.12f + 0.76f * noise(seed * 31 + letter);
        float flicker = (noise(seed * 17 + glitchTick * 101 + letter) - 0.5f) * 2 * g.flickerAmount();
        return flipMoment + flicker <= progress;
    }

    private static Component build(String text, Gradient look, long now, double speed, IntPredicate isScrambled) {
        MutableComponent result = Component.empty();

        StringBuilder currentRun = new StringBuilder();
        int runColor = 0;
        boolean runScrambled = false;

        for (int i = 0; i < text.length(); i++) {
            float position = text.length() == 1 ? 0f : (float) i / (text.length() - 1);
            int color = look.colorAt(position, speed, now);
            boolean scrambled = isScrambled.test(i);

            boolean runChanged = color != runColor || scrambled != runScrambled;
            if (!currentRun.isEmpty() && runChanged) {
                result.append(piece(currentRun.toString(), runColor, runScrambled));
                currentRun.setLength(0);
            }

            currentRun.append(text.charAt(i));
            runColor = color;
            runScrambled = scrambled;
        }

        if (!currentRun.isEmpty()) {
            result.append(piece(currentRun.toString(), runColor, runScrambled));
        }

        return result;
    }

    private static Component piece(String text, int color, boolean scrambled) {
        MutableComponent piece = Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        if (scrambled) {
            piece.withStyle(ChatFormatting.OBFUSCATED);
        }
        return piece;
    }

    private static float noise(long seed) {
        return (mix(seed) >>> 40) / (float) (1 << 24);
    }

    // splitmix64
    private static long mix(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
