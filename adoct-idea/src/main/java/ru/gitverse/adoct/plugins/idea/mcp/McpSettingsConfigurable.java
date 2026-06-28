package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Экран настроек встроенного MCP-сервера (Settings → Tools → AsciiDocTools MCP): включение, адрес/порт
 * привязки и значения по умолчанию для «однопроектной» инсталляции (проект Jira, пространство Confluence).
 * Применение перезапускает сервер.
 */
public final class McpSettingsConfigurable implements Configurable {

    private JBCheckBox enabled;
    private JBTextField bindHost;
    private JBTextField port;
    private JBTextField defaultJiraProject;
    private JBTextField defaultConfluenceSpace;
    private JPanel panel;

    @Override
    public @Nls String getDisplayName() {
        return "AsciiDocTools MCP";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enabled = new JBCheckBox("Запускать MCP-сервер при старте IDE");
        bindHost = new JBTextField();
        port = new JBTextField();
        defaultJiraProject = new JBTextField();
        defaultConfluenceSpace = new JBTextField();

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addLabeledComponent("Адрес привязки:", bindHost)
                .addLabeledComponent("Порт:", port)
                .addLabeledComponent("Проект Jira по умолчанию:", defaultJiraProject)
                .addLabeledComponent("Пространство Confluence по умолчанию:", defaultConfluenceSpace)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        McpSettingsService s = McpSettingsService.getInstance();
        return enabled.isSelected() != s.isEnabled()
                || !bindHost.getText().trim().equals(s.getBindHost())
                || !port.getText().trim().equals(String.valueOf(s.getPort()))
                || !defaultJiraProject.getText().trim().equals(s.getDefaultJiraProject())
                || !defaultConfluenceSpace.getText().trim().equals(s.getDefaultConfluenceSpace());
    }

    @Override
    public void apply() throws ConfigurationException {
        int portValue;
        try {
            portValue = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Порт должен быть числом");
        }
        if (portValue < 1 || portValue > 65535) {
            throw new ConfigurationException("Порт вне диапазона 1..65535");
        }

        McpSettingsService.StateData state = new McpSettingsService.StateData();
        state.enabled = enabled.isSelected();
        state.bindHost = bindHost.getText().trim();
        state.port = portValue;
        state.defaultJiraProject = defaultJiraProject.getText().trim();
        state.defaultConfluenceSpace = defaultConfluenceSpace.getText().trim();
        McpSettingsService.getInstance().loadState(state);

        McpServerService.getInstance().restart();
    }

    @Override
    public void reset() {
        McpSettingsService s = McpSettingsService.getInstance();
        enabled.setSelected(s.isEnabled());
        bindHost.setText(s.getBindHost());
        port.setText(String.valueOf(s.getPort()));
        defaultJiraProject.setText(s.getDefaultJiraProject());
        defaultConfluenceSpace.setText(s.getDefaultConfluenceSpace());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
