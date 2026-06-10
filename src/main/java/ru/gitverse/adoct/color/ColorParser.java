package ru.gitverse.adoct.color;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorParser {// Регулярное выражение для разбора rgb(r,g,b)
    private static final Pattern RGB_PATTERN = Pattern.compile("rgb\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)");

    public static Optional<String> parseColor(String line) {
        // Убираем лишние пробелы
        line = line.trim();

        // Проверяем, начинается ли строка с "color:"
        if (!line.startsWith("color:")) {
            throw new IllegalArgumentException("Invalid format: does not start with 'color:'");
        }

        // Извлекаем значение после "color:"
        String colorStr = line.substring(6).trim();

        // Если не начинается с "rgb(", считаем, что это именованный цвет
        if (!colorStr.startsWith("rgb(")) {
            return ColorMatcher.findNameColor(colorStr).filter(it -> !"black".equals(it)); // возвращаем имя цвета как строку
        }

        // Применяем регулярное выражение
        Matcher matcher = RGB_PATTERN.matcher(colorStr);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        int r = Integer.parseInt(matcher.group(1));
        int g = Integer.parseInt(matcher.group(2));
        int b = Integer.parseInt(matcher.group(3));

        // Проверка диапазона [0, 255]
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException("RGB values out of range: (" + r + ", " + g + ", " + b + ")");
        }

        return Optional.ofNullable(ColorMatcher.findClosestColor(r, g, b)).filter(it -> !"black".equals(it));

    }

}
