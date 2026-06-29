package ru.gitverse.adoct.plugins.idea.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Корневой узел группы настроек «AsciiDocTools» в дереве Settings. Сам по себе ничего не редактирует —
 * под ним собраны страницы: серверы Confluence/Jira, встроенный MCP-сервер и типы задач.
 */
public final class AdoctSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

    private JPanel panel;

    @Override
    public @NotNull String getId() {
        return "adoct.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "AsciiDocTools";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            panel = new JPanel(new BorderLayout());
            panel.add(new JBLabel("<html>Настройки AsciiDocTools: серверы Confluence/Jira, встроенный "
                    + "MCP-сервер и типы задач.<br>Выберите раздел в дереве слева.</html>"), BorderLayout.NORTH);
        }
        return panel;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() {
        // Узел-контейнер: настроек нет.
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
