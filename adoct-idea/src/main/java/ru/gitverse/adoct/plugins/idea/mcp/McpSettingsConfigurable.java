package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DoubleClickListener;
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
import java.awt.event.MouseEvent;
import java.io.File;
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
    private JBCheckBox toolsJira;
    private JBCheckBox toolsConfluence;
    private JBCheckBox toolsBitbucket;
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
        toolsJira = new JBCheckBox("Jira");
        toolsConfluence = new JBCheckBox("Confluence");
        toolsBitbucket = new JBCheckBox("Bitbucket");

        teamModel = readOnlyModel("username", "Имя", "Роль");
        teamTable = new JBTable(teamModel);
        teamTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent e) {
                editMember(teamTable.getSelectedRow());
                return true;
            }
        }.installOn(teamTable);
        JPanel teamPanel = ToolbarDecorator.createDecorator(teamTable)
                .setAddAction(b -> editMember(-1))
                .setEditAction(b -> editMember(teamTable.getSelectedRow()))
                .setRemoveAction(b -> removeSelected(teamTable, teamModel))
                .createPanel();

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addLabeledComponent("Адрес MCP:", urlRow())
                .addLabeledComponent("Статус:", statusRow())
                .addLabeledComponent("Диагностика:", diagRow())
                .addLabeledComponent("Адрес привязки:", bindHost)
                .addLabeledComponent("Порт:", port)
                .addLabeledComponent("Проект Jira по умолчанию:", defaultJiraProject)
                .addTooltip("Ключ (ABC) или URL задачи/проекта")
                .addLabeledComponent("Пространство Confluence по умолчанию:", defaultConfluenceSpace)
                .addTooltip("Ключ (PLCHAT) или URL страницы")
                .addLabeledComponent("Группы инструментов:", toolGroupsRow())
                .addLabeledComponent("Команда (username / имя / роль):", teamPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    /** Строка адреса: URL эндпоинта (только чтение) + кнопка копирования. */
    private JComponent urlRow() {
        urlField = new JBTextField();
        urlField.setEditable(false);
        urlField.setColumns(22);
        JButton copy = new JButton("Копировать");
        copy.addActionListener(e -> CopyPasteManager.getInstance().setContents(new StringSelection(urlField.getText())));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(urlField);
        row.add(copy);
        return row;
    }

    /** Строка статуса: живой индикатор (ping по HTTP) + обновить / перезапустить / открыть лог. */
    private JComponent statusRow() {
        statusLabel = new JBLabel();
        JButton refresh = new JButton("Обновить");
        refresh.addActionListener(e -> refreshStatus());
        JButton restart = new JButton("Перезапустить");
        restart.addActionListener(e -> {
            McpServerService.getInstance().restart();
            refreshStatus();
            // Старт асинхронный — обновим статус ещё раз чуть позже.
            javax.swing.Timer delayed = new javax.swing.Timer(900, ev -> refreshStatus());
            delayed.setRepeats(false);
            delayed.start();
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(statusLabel);
        row.add(refresh);
        row.add(restart);
        return row;
    }

    /** Строка диагностики: открыть idea.log и выгрузить список тулов с живого сервера в буфер. */
    private JComponent diagRow() {
        JButton openLog = new JButton("Открыть лог");
        openLog.addActionListener(e ->
                RevealFileAction.openFile(new File(PathManager.getLogPath(), "idea.log")));
        JButton toolsToClipboard = new JButton("Список тулов → буфер");
        toolsToClipboard.addActionListener(e -> copyToolsToClipboard());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(openLog);
        row.add(toolsToClipboard);
        return row;
    }

    /** Тянет {@code tools/list} с живого сервера (off-EDT), кладёт список в буфер и показывает счётчик. */
    private void copyToolsToClipboard() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String clipboard;
            String message;
            boolean ok;
            try {
                List<String> names = McpServerService.getInstance().fetchToolNames();
                clipboard = names.size() + " тулов:\n" + String.join("\n", names);
                message = "Скопировано в буфер: " + names.size() + " тулов.";
                ok = true;
            } catch (Exception ex) {
                clipboard = null;
                message = "Не удалось получить список тулов: "
                        + (ex.getMessage() == null ? ex.toString() : ex.getMessage()) + ".\nСервер запущен?";
                ok = false;
            }
            String clip = clipboard;
            String msg = message;
            boolean success = ok;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (success) {
                    CopyPasteManager.getInstance().setContents(new StringSelection(clip));
                    Messages.showInfoMessage(msg, "Список инструментов MCP");
                } else {
                    Messages.showErrorDialog(msg, "Список инструментов MCP");
                }
            }, ModalityState.any());
        });
    }

    /** Галки групп инструментов: выключенная группа не отдаётся по MCP (тулы скрываются). */
    private JComponent toolGroupsRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(toolsJira);
        row.add(toolsConfluence);
        row.add(toolsBitbucket);
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
                || toolsJira.isSelected() != s.isToolsJira()
                || toolsConfluence.isSelected() != s.isToolsConfluence()
                || toolsBitbucket.isSelected() != s.isToolsBitbucket()
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
        state.toolsJira = toolsJira.isSelected();
        state.toolsConfluence = toolsConfluence.isSelected();
        state.toolsBitbucket = toolsBitbucket.isSelected();
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
        toolsJira.setSelected(s.isToolsJira());
        toolsConfluence.setSelected(s.isToolsConfluence());
        toolsBitbucket.setSelected(s.isToolsBitbucket());

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

    /** Живой статус: реальный HTTP-ping off-EDT, затем обновление метки на EDT (с ошибкой старта, если есть). */
    private void refreshStatus() {
        statusLabel.setText("проверка…");
        statusLabel.setForeground(JBColor.GRAY);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            McpServerService svc = McpServerService.getInstance();
            long start = System.nanoTime();
            boolean up = svc.pingHttp();
            String error = svc.lastError();
            // Минимально показываем «проверка…» ~600 мс, иначе локальный ping мелькает незаметно.
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMs < 600) {
                try {
                    Thread.sleep(600 - elapsedMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            // ModalityState.any() — иначе апдейт встанет в очередь за модальным окном Settings и «зависнет».
            ApplicationManager.getApplication().invokeLater(() -> {
                if (statusLabel == null) {
                    return;
                }
                if (up) {
                    statusLabel.setText("● Запущен");
                    statusLabel.setForeground(JBColor.GREEN);
                } else {
                    statusLabel.setText("○ Остановлен" + (error == null || error.isBlank() ? "" : " — " + error));
                    statusLabel.setForeground(JBColor.RED);
                }
            }, ModalityState.any());
        });
    }

    // ---- helpers ----

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(new Object[0][], columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Правка — через диалог editMember, не inline: корректные фокус и вставка.
                return false;
            }
        };
    }

    /**
     * Диалог добавления/изменения участника команды (username / имя / роль) — обычными полями, где
     * корректно работают фокус и вставка из буфера.
     *
     * @param row индекс строки для изменения; {@code < 0} — добавление нового
     */
    private void editMember(int row) {
        boolean adding = row < 0;
        JBTextField usernameField = new JBTextField(24);
        JBTextField displayNameField = new JBTextField(24);
        JBTextField roleField = new JBTextField(24);
        if (!adding) {
            usernameField.setText(cell(teamModel, row, 0));
            displayNameField.setText(cell(teamModel, row, 1));
            roleField.setText(cell(teamModel, row, 2));
        }

        JComponent form = FormBuilder.createFormBuilder()
                .addLabeledComponent("username:", usernameField)
                .addLabeledComponent("Имя:", displayNameField)
                .addLabeledComponent("Роль:", roleField)
                .getPanel();

        DialogBuilder builder = new DialogBuilder(panel);
        builder.setTitle(adding ? "Добавить участника" : "Изменить участника");
        builder.setCenterPanel(form);
        builder.setPreferredFocusComponent(usernameField);
        builder.addOkAction();
        builder.addCancelAction();
        if (builder.show() != DialogWrapper.OK_EXIT_CODE) {
            return;
        }

        Object[] values = {
                usernameField.getText().trim(), displayNameField.getText().trim(), roleField.getText().trim()
        };
        if (adding) {
            teamModel.addRow(values);
        } else {
            for (int col = 0; col < values.length; col++) {
                teamModel.setValueAt(values[col], row, col);
            }
        }
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
