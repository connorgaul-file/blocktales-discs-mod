package com.kf;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

// the tooltip style id on a disc is discs:<bossId>
public class TooltipFrames {

    public static final float DEFAULT_SOFTNESS = 0.2f;

    public record Background(int firstColor, int secondColor, float softness) {}

    public record Frame(int topColor, int bottomColor, double speed, @Nullable Background background) {

        public int colorAt(float position, long nowMs) {
            return 0xFF000000 | slidingColor(new int[]{topColor, bottomColor}, position, speed, nowMs);
        }
    }

    public static @Nullable Frame get(@Nullable Identifier style) {
        if (style == null || !style.getNamespace().equals(Discs.MOD_ID)) {
            return null;
        }
        BossData.Boss boss = BossData.get(style.getPath());
        return boss == null ? null : boss.frame();
    }

    public static int slidingColor(int[] stops, float position, double speed, long nowMs) {
        double phase = position * 0.5 + (nowMs / 1000.0) * speed;
        phase -= Math.floor(phase);

        double wave = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
        return gradientColor(stops, (float) wave);
    }

    public static int gradientColor(int[] stops, float position) {
        if (stops.length == 1) {
            return stops[0] & 0xFFFFFF;
        }

        float scaled = position * (stops.length - 1);
        int index = Math.min((int) scaled, stops.length - 2);
        float blend = scaled - index;

        int from = stops[index];
        int to = stops[index + 1];

        int red = blendChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, blend);
        int green = blendChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, blend);
        int blue = blendChannel(from & 0xFF, to & 0xFF, blend);
        return (red << 16) | (green << 8) | blue;
    }

    private static int blendChannel(int from, int to, float blend) {
        return Math.round(from + (to - from) * blend);
    }
}
