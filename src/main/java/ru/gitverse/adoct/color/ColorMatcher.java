package ru.gitverse.adoct.color;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ColorMatcher {
    // Статический список доступных цветов
    private static final Map<String, int[]> COLORS = new HashMap<>();

    static {
        COLORS.put("aqua", new int[]{0, 191, 191});
        COLORS.put("black", new int[]{0, 0, 0});
        COLORS.put("blue", new int[]{0, 0, 191});
        COLORS.put("fuchsia", new int[]{191, 0, 191});
        COLORS.put("gray", new int[]{96, 96, 96});
        COLORS.put("green", new int[]{0, 96, 0});
        COLORS.put("lime", new int[]{0, 191, 0});
        COLORS.put("maroon", new int[]{96, 0, 0});
        COLORS.put("navy", new int[]{0, 0, 96});
        COLORS.put("olive", new int[]{96, 96, 0});
        COLORS.put("purple", new int[]{96, 0, 96});
        COLORS.put("red", new int[]{191, 0, 0});
        COLORS.put("silver", new int[]{144, 144, 144});
        COLORS.put("teal", new int[]{0, 96, 96});
        COLORS.put("white", new int[]{191, 191, 191});
        COLORS.put("yellow", new int[]{191, 191, 0});
    }

    public static Optional<String> findNameColor(String name) {
        return Optional.of(name.toLowerCase(Locale.ROOT)).filter(COLORS::containsKey);
    }

    // Метод находит ближайший цвет по евклидову расстоянию
    public static String findClosestColor(int r, int g, int b) {
        String closestColor = null;
        long minDistanceSquared = Long.MAX_VALUE;

        for (Map.Entry<String, int[]> entry : COLORS.entrySet()) {
            int[] rgb = entry.getValue();
            long dr = r - rgb[0];
            long dg = g - rgb[1];
            long db = b - rgb[2];
            long distanceSquared = dr * dr + dg * dg + db * db;

            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                closestColor = entry.getKey();
            }
        }

        return closestColor;
    }

}
