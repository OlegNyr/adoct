package ru.gitverse.adoct.plugins.idea;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bundle {
    @NotNull
    private final static String MESSAGES_BUNDLE = "messages.Bundle";
    @NotNull
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(MESSAGES_BUNDLE);

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = MESSAGES_BUNDLE) String key, Object... params) {
        var value = AbstractBundle.message(BUNDLE, key, params);
        return processPlaceholders(value);
    }

    /**
     * Подстановка в значение свойства других свойств:
     *
     * <p>формат</p>
     *      <p>Bundle.properties</p>
     *      --
     *      prop1=Foo -> "Foo"
     *      prop2= Bar ${prop1} -> "Bar Foo"
     *
     * @param value
     * @return
     */
    private static String processPlaceholders(String value) {
       //todo устранить возможность зацикливания: заменить рекурсию для цикл, в цикле вести мапу, которая исключит повторный вход
        Pattern pattern = Pattern.compile("\\$\\{([^}]*)}");
        Matcher matcher = pattern.matcher(value);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String mv = matcher.group(1);
            matcher.appendReplacement(sb, message(mv));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
