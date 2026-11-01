package com.kf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class PulsingName {
    private final String text;
    private final int[] colors;

    private PulsingName(String text, int[] colors) {
        this.text = text;
        this.colors = colors;
    }

    public static PulsingName fromStops(String text, int[] stops) {
        int[] colors = new int[text.length()];
        for (int i = 0; i < colors.length; i++) {
            float position = colors.length == 1 ? 0f : (float) i / (colors.length - 1);
            colors[i] = TooltipFrames.gradientColor(stops, position);
        }
        return new PulsingName(text, colors);
    }

    public Component at(double speed, long nowMs) {
        MutableComponent result = Component.empty();

        for (int i = 0; i < text.length(); i++) {
            float position = text.length() == 1 ? 0f : (float) i / (text.length() - 1);
            int color = TooltipFrames.slidingColor(colors, position, speed, nowMs);

            result.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }

        return result;
    }
}
