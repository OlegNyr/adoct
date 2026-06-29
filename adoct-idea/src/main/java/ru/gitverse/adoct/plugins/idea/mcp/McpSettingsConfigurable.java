package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import ru.gitverse.adoct.plugins.idea.mcp.McpSettingsService.TeamMemberState;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Экран настроек встроенного MCP-сервера (Settings → Tools → AsciiDocTools → MCP-сервер): включение,
 * адрес/порт, статус и URL для подключения, значения по умолчанию (проект Jira, пространство Confluence)
 * и ростер команды. Типы задач (шаблон + состояния) — на отдельной странице
 * {@link McpIssueTypesConfigurable}. Применение перезапускает сервер.
 */
public final class McpSettingsConfigurable implements Configurable {

    private JBCheckBox enabled;
    private JBTextField bindHost;
    private JBTextField port;
    private JBTextField defaultJiraProject;
    private JBTextField defaultConfluenceSpace;
    private JBTextField urlField;
    private JBLabel statusLabel;
    private DefaultTableModel teamModel;
    private JBTable teamTable;
    private JPanel panel;

    @Override
    public @Nls String getDisplayName() {
        return "MCP-сервер";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enabled = new JBCheckBox("Запускать MCP-сервер при старте IDE");
        bindHost = new JBTextField();
        port = new JBTextField();
        defaultJiraProject = new JBTextField();
        defaultConfluenceSpace = new JBTextField();

        teamModel = editableModel("username", "Имя", "Роль");
        teamTable = new JBTable(teamModel);
        teamTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel teamPanel = ToolbarDecorator.createDecorator(teamTable)
                .setAddAction(b -> teamModel.addRow(new Object[] {"", "", ""}))
                .setRemoveAction(b -> removeSelected(teamTable, teamModel))
                .createPanel();

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addLabeledComponent("Адрес MCP:", statusRow())
                .addLabeledComponent("Адрес привязки:", bindHost)
                .addLabeledComponent("Порт:", port)
                .addLabeledComponent("Проект Jira по умолчанию:", defaultJiraProject)
                .addTooltip("Ключ (ABC) или URL задачи/проекта")
                .addLabeledComponent("Пространство Confluence по умолчанию:", defaultConfluenceSpace)
                .addTooltip("Ключ (PLCHAT) или URL страницы")
                .addLabeledComponent("Команда (username / имя / роль):", teamPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    /** Строка статуса: URL для копирования + индикатор «поднялся / нет» + обновление. */
    private JComponent statusRow() {
        urlField = new JBTextField();
        urlField.setEditable(false);
        urlField.setColumns(20);
        statusLabel = new JBLabel();
        JButton copy = new JButton("Копировать");
        copy.addActionListener(e -> CopyPasteManager.getInstance().setContents(new StringSelection(urlField.getText())));
        JButton refresh = new JButton("Обновить");
        refresh.addActionListener(e -> refreshStatus());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(urlField);
        row.add(copy);
        row.add(statusLabel);
        row.add(refresh);
        return row;
    }

    @Override
    public boolean isModified() {
        McpSettingsService s = McpSettingsService.getInstance();
        return enabled.isSelected() != s.isEnabled()
                || !bindHost.getText().trim().equals(s.getBindHost())
                || !port.getText().trim().equals(String.valueOf(s.getPort()))
                || !defaultJiraProject.getText().trim().equals(s.getDefaultJiraProject())
                || !defaultConfluenceSpace.getText().trim().equals(s.getDefaultConfluenceSpace())
                || !sameTeam(teamFromTable(), s.getTeam());
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

        // Сохраняем только свой срез, не затирая типы задач (их редактирует McpIssueTypesConfigurable).
        McpSettingsService.StateData state = McpSettingsService.getInstance().snapshot();
        state.enabled = enabled.isSelected();
        state.bindHost = bindHost.getText().trim();
        state.port = portValue;
        state.defaultJiraProject = defaultJiraProject.getText().trim();
        state.defaultConfluenceSpace = defaultConfluenceSpace.getText().trim();
        state.team = teamFromTable();
        McpSettingsService.getInstance().loadState(state);

        McpServerService.getInstance().restart();
        urlField.setText(McpServerService.endpointUrl());
        refreshStatus();
    }

    @Override
    public void reset() {
        McpSettingsService s = McpSettingsService.getInstance();
        enabled.setSelected(s.isEnabled());
        bindHost.setText(s.getBindHost());
        port.setText(String.valueOf(s.getPort()));
        defaultJiraProject.setText(s.getDefaultJiraProject());
        defaultConfluenceSpace.setText(s.getDefaultConfluenceSpace());

        teamModel.setRowCount(0);
        for (TeamMemberState m : s.getTeam()) {
            teamModel.addRow(new Object[] {m.username, m.displayName, m.role});
        }

        urlField.setText(McpServerService.endpointUrl());
        refreshStatus();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        teamTable = null;
        teamModel = null;
        urlField = null;
        statusLabel = null;
    }

    private void refreshStatus() {
        boolean up = McpServerService.getInstance().isRunning();
        statusLabel.setText(up ? "● Запущен" : "○ Остановлен");
        statusLabel.setForeground(up ? JBColor.GREEN : JBColor.RED);
    }

    // ---- helpers ----

    private static DefaultTableModel editableModel(String... columns) {
        return new DefaultTableModel(new Object[0][], columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
    }

    private static void removeSelected(JBTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row >= 0) {
            model.removeRow(row);
        }
    }

    private List<TeamMemberState> teamFromTable() {
        if (teamTable.isEditing() && teamTable.getCellEditor() != null) {
            teamTable.getCellEditor().stopCellEditing();
        }
        List<TeamMemberState> out = new ArrayList<>();
        for (int r = 0; r < teamModel.getRowCount(); r++) {
            String username = cell(teamModel, r, 0);
            if (username.isBlank()) {
                continue;
            }
            out.add(new TeamMemberState(username, cell(teamModel, r, 1), cell(teamModel, r, 2)));
        }
        return out;
    }

    private static String cell(DefaultTableModel model, int row, int column) {
        return Objects.toString(model.getValueAt(row, column), "").trim();
    }

    private static boolean sameTeam(List<TeamMemberState> a, List<TeamMemberState> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).username, b.get(i).username)
                    || !Objects.equals(a.get(i).displayName, b.get(i).displayName)
                    || !Objects.equals(a.get(i).role, b.get(i).role)) {
                return false;
            }
        }
        return true;
    }
}
