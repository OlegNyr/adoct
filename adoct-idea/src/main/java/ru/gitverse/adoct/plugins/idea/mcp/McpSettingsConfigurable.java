package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import ru.gitverse.adoct.plugins.idea.mcp.McpSettingsService.TeamMemberState;
import ru.gitverse.adoct.plugins.idea.mcp.McpSettingsService.TemplateState;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Экран настроек встроенного MCP-сервера (Settings → Tools → AsciiDocTools MCP): включение, адрес/порт,
 * значения по умолчанию (проект Jira, пространство Confluence), ростер команды, шаблоны задач и
 * диаграмма состояний (PlantUML). Применение перезапускает сервер.
 */
public final class McpSettingsConfigurable implements Configurable {

    private JBCheckBox enabled;
    private JBTextField bindHost;
    private JBTextField port;
    private JBTextField defaultJiraProject;
    private JBTextField defaultConfluenceSpace;
    private DefaultTableModel teamModel;
    private JBTable teamTable;
    private DefaultTableModel templatesModel;
    private JBTable templatesTable;
    private JBTextArea workflowArea;
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

        teamModel = editableModel("username", "Имя", "Роль");
        teamTable = new JBTable(teamModel);
        teamTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel teamPanel = ToolbarDecorator.createDecorator(teamTable)
                .setAddAction(b -> teamModel.addRow(new Object[] {"", "", ""}))
                .setRemoveAction(b -> removeSelected(teamTable, teamModel))
                .createPanel();

        templatesModel = editableModel("Имя", "Текст шаблона");
        templatesTable = new JBTable(templatesModel);
        templatesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel templatesPanel = ToolbarDecorator.createDecorator(templatesTable)
                .setAddAction(b -> templatesModel.addRow(new Object[] {"", ""}))
                .setRemoveAction(b -> removeSelected(templatesTable, templatesModel))
                .createPanel();

        workflowArea = new JBTextArea(8, 60);

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addLabeledComponent("Адрес привязки:", bindHost)
                .addLabeledComponent("Порт:", port)
                .addLabeledComponent("Проект Jira по умолчанию:", defaultJiraProject)
                .addLabeledComponent("Пространство Confluence по умолчанию:", defaultConfluenceSpace)
                .addTooltip("Можно вставить ключ (PLCHAT) или полный URL страницы — ключ извлечётся автоматически")
                .addLabeledComponent("Команда (username / имя / роль):", teamPanel)
                .addLabeledComponent("Шаблоны задач (имя / текст):", templatesPanel)
                .addLabeledComponent("Состояния задач (PlantUML state):", new JBScrollPane(workflowArea))
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
                || !defaultConfluenceSpace.getText().trim().equals(s.getDefaultConfluenceSpace())
                || !workflowArea.getText().equals(s.getWorkflowDiagram())
                || !sameTeam(teamFromTable(), s.getTeam())
                || !sameTemplates(templatesFromTable(), s.getTemplates());
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
        state.team = teamFromTable();
        state.templates = templatesFromTable();
        state.workflowDiagram = workflowArea.getText();
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

        teamModel.setRowCount(0);
        for (TeamMemberState m : s.getTeam()) {
            teamModel.addRow(new Object[] {m.username, m.displayName, m.role});
        }
        templatesModel.setRowCount(0);
        for (TemplateState t : s.getTemplates()) {
            templatesModel.addRow(new Object[] {t.name, t.body});
        }
        workflowArea.setText(s.getWorkflowDiagram());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        teamTable = null;
        teamModel = null;
        templatesTable = null;
        templatesModel = null;
        workflowArea = null;
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

    private List<TemplateState> templatesFromTable() {
        List<TemplateState> out = new ArrayList<>();
        for (int r = 0; r < templatesModel.getRowCount(); r++) {
            String name = cell(templatesModel, r, 0);
            if (name.isBlank()) {
                continue;
            }
            out.add(new TemplateState(name, cell(templatesModel, r, 1)));
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

    private static boolean sameTemplates(List<TemplateState> a, List<TemplateState> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).name, b.get(i).name) || !Objects.equals(a.get(i).body, b.get(i).body)) {
                return false;
            }
        }
        return true;
    }
}
