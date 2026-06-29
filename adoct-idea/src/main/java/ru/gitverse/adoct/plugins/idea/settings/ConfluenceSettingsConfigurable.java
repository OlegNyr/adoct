package ru.gitverse.adoct.plugins.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.gitverse.adoct.bitbucket.BitbucketClient;
import ru.gitverse.adoct.jira.JiraClient;
import ru.gitverse.adoct.mcp.AtlassianKind;
import ru.gitverse.adoct.parser.confluence.ConfluenceClient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.gitverse.adoct.plugins.idea.Bundle.message;

public class ConfluenceSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private static final int HOST_COLUMN = 0;
    private static final int TYPE_COLUMN = 1;
    private static final int DEFAULT_COLUMN = 2;
    private static final int TOKEN_COLUMN = 3;

    /** Защита от рекурсии при программном изменении модели в слушателе. */
    private boolean updatingModel = false;

    private JPanel panel;
    private JBTable serversTable;
    private DefaultTableModel tableModel;
    private JButton verifyTokenButton;

    @Override
    public @NotNull String getId() {
        return "ru.gitverse.adoct.plugins.idea.settings.confluence";
    }

    @Override
    public @Nls String getDisplayName() {
        return message("settings.Confluence.title");
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            panel = new JPanel(new BorderLayout(8, 8));

            tableModel = new DefaultTableModel(
                    new Object[][]{},
                    new String[]{
                            message("settings.Confluence.table.host"),
                            message("settings.Confluence.table.type"),
                            message("settings.Confluence.table.default"),
                            message("settings.Confluence.table.token")
                    }
            ) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    // Редактирование — через диалог (Add/Edit), не inline: так корректно работают
                    // фокус и вставка из буфера (особенно для длинного токена).
                    return false;
                }

                @Override
                public Class<?> getColumnClass(int column) {
                    return column == DEFAULT_COLUMN ? Boolean.class : String.class;
                }
            };

            serversTable = new JBTable(tableModel);
            serversTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            configureTokenColumn();
            sizeFixedColumns();
            new DoubleClickListener() {
                @Override
                protected boolean onDoubleClick(MouseEvent e) {
                    editServer(serversTable.getSelectedRow());
                    return true;
                }
            }.installOn(serversTable);

            JPanel tablePanel = ToolbarDecorator.createDecorator(serversTable)
                    .setAddAction(button -> editServer(-1))
                    .setEditAction(button -> editServer(serversTable.getSelectedRow()))
                    .setRemoveAction(button -> {
                        int selectedRow = serversTable.getSelectedRow();
                        if (selectedRow >= 0) {
                            tableModel.removeRow(selectedRow);
                        }
                    })
                    .createPanel();

            verifyTokenButton = new JButton(message("settings.Confluence.verifyToken.button"));
            verifyTokenButton.addActionListener(e -> verifySelectedServer());

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            footer.add(verifyTokenButton);

            panel.add(tablePanel, BorderLayout.CENTER);
            panel.add(footer, BorderLayout.SOUTH);
        }
        return panel;
    }

    @Override
    public boolean isModified() {
        List<ConfluenceSettingsService.ServerEntry> current = getServersFromTable();
        List<ConfluenceSettingsService.ServerEntry> saved
                = ConfluenceSettingsService.getInstance().getServers();
        return !areSame(current, saved);
    }

    @Override
    public void apply() {
        ConfluenceSettingsService.getInstance().setServers(getServersFromTable());
    }

    @Override
    public void reset() {
        if (tableModel == null) {
            return;
        }
        updatingModel = true;
        try {
            tableModel.setRowCount(0);
            for (ConfluenceSettingsService.ServerEntry server : ConfluenceSettingsService.getInstance().getServers()) {
                String type = AtlassianKind.parse(server.getType(), AtlassianKind.detect(server.getHost())).name();
                tableModel.addRow(new Object[]{server.getHost(), type, server.isPrimary(), server.getToken()});
            }
        } finally {
            updatingModel = false;
        }
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        serversTable = null;
        tableModel = null;
        verifyTokenButton = null;
    }

    private void verifySelectedServer() {
        int row = serversTable.getSelectedRow();
        if (row < 0) {
            Messages.showErrorDialog(message("settings.Confluence.verifyToken.noSelection"), message("settings.Confluence.verifyToken.error.title"));
            return;
        }

        String host = Objects.toString(tableModel.getValueAt(row, HOST_COLUMN), "").trim();
        String token = Objects.toString(tableModel.getValueAt(row, TOKEN_COLUMN), "").trim();
        String type = Objects.toString(tableModel.getValueAt(row, TYPE_COLUMN), "").trim();
        AtlassianKind kind = AtlassianKind.parse(type, AtlassianKind.detect(host));

        if (host.isEmpty()) {
            Messages.showErrorDialog(message("settings.Confluence.verifyToken.emptyHost"), message("settings.Confluence.verifyToken.error.title"));
            return;
        }
        if (token.isEmpty()) {
            Messages.showErrorDialog(message("settings.Confluence.verifyToken.emptyToken"), message("settings.Confluence.verifyToken.error.title"));
            return;
        }

        verifyTokenButton.setEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VerificationResult result = checkToken(host, token, kind);
            SwingUtilities.invokeLater(() -> {
                verifyTokenButton.setEnabled(true);
                if (result.success()) {
                    Messages.showInfoMessage(result.message(), message("settings.Confluence.verifyToken.success.title"));
                } else {
                    Messages.showErrorDialog(result.message(), message("settings.Confluence.verifyToken.error.title"));
                }
            });
        });
    }

    private static VerificationResult checkToken(String host, String token, AtlassianKind kind) {
        try {
            int code = switch (kind) {
                case JIRA -> new JiraClient(host, token).verifyToken();
                case BITBUCKET -> new BitbucketClient(host, token).verifyToken();
                case CONFLUENCE -> new ConfluenceClient(host, token).verifyToken();
            };
            if (code >= 200 && code < 300) {
                return new VerificationResult(true, message("settings.Confluence.verifyToken.success.message", code));
            }
            if (code == 401 || code == 403) {
                return new VerificationResult(false, message("settings.Confluence.verifyToken.unauthorized.message", code));
            }
            return new VerificationResult(false, message("settings.Confluence.verifyToken.httpError.message", code));
        } catch (Exception ex) {
            return new VerificationResult(false, message("settings.Confluence.verifyToken.requestError.message", ex.getMessage()));
        }
    }

    private List<ConfluenceSettingsService.ServerEntry> getServersFromTable() {
        List<ConfluenceSettingsService.ServerEntry> servers = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String host = Objects.toString(tableModel.getValueAt(row, HOST_COLUMN), "").trim();
            String token = Objects.toString(tableModel.getValueAt(row, TOKEN_COLUMN), "").trim();
            if (host.isEmpty() && token.isEmpty()) {
                continue;
            }
            String type = Objects.toString(tableModel.getValueAt(row, TYPE_COLUMN), "").trim();
            boolean primary = Boolean.TRUE.equals(tableModel.getValueAt(row, DEFAULT_COLUMN));
            servers.add(new ConfluenceSettingsService.ServerEntry(host, token, type, primary));
        }
        return servers;
    }

    private void sizeFixedColumns() {
        serversTable.getColumnModel().getColumn(TYPE_COLUMN).setPreferredWidth(110);
        serversTable.getColumnModel().getColumn(TYPE_COLUMN).setMaxWidth(160);
        serversTable.getColumnModel().getColumn(DEFAULT_COLUMN).setPreferredWidth(90);
        serversTable.getColumnModel().getColumn(DEFAULT_COLUMN).setMaxWidth(110);
    }

    /**
     * Диалог добавления/изменения сервера: host / тип / токен / «по умолчанию» — обычными полями, где
     * корректно работают фокус и вставка из буфера (в отличие от inline-редактирования ячеек таблицы).
     *
     * @param row индекс строки для изменения; {@code < 0} — добавление новой
     */
    private void editServer(int row) {
        boolean adding = row < 0;
        JBTextField hostField = new JBTextField(30);
        ComboBox<AtlassianKind> typeCombo = new ComboBox<>(AtlassianKind.values());
        JBPasswordField tokenField = new JBPasswordField();
        JBCheckBox defaultBox = new JBCheckBox("Использовать по умолчанию для этого типа");

        if (!adding) {
            String host = Objects.toString(tableModel.getValueAt(row, HOST_COLUMN), "");
            hostField.setText(host);
            typeCombo.setSelectedItem(AtlassianKind.parse(
                    Objects.toString(tableModel.getValueAt(row, TYPE_COLUMN), ""), AtlassianKind.detect(host)));
            defaultBox.setSelected(Boolean.TRUE.equals(tableModel.getValueAt(row, DEFAULT_COLUMN)));
            tokenField.setText(Objects.toString(tableModel.getValueAt(row, TOKEN_COLUMN), ""));
        }

        // Авто-определение типа по хосту, пока пользователь сам не выбрал тип.
        boolean[] manual = {!adding};
        boolean[] programmatic = {false};
        typeCombo.addActionListener(e -> {
            if (!programmatic[0]) {
                manual[0] = true;
            }
        });
        hostField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                if (!manual[0]) {
                    programmatic[0] = true;
                    typeCombo.setSelectedItem(AtlassianKind.detect(hostField.getText().trim()));
                    programmatic[0] = false;
                }
            }
        });

        JComponent form = FormBuilder.createFormBuilder()
                .addLabeledComponent("Адрес сервера:", hostField)
                .addLabeledComponent("Тип:", typeCombo)
                .addLabeledComponent("Токен (PAT):", tokenField)
                .addComponent(defaultBox)
                .getPanel();

        DialogBuilder builder = new DialogBuilder(panel);
        builder.setTitle(adding ? "Добавить сервер" : "Изменить сервер");
        builder.setCenterPanel(form);
        builder.setPreferredFocusComponent(hostField);
        builder.addOkAction();
        builder.addCancelAction();
        if (builder.show() != DialogWrapper.OK_EXIT_CODE) {
            return;
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            Messages.showErrorDialog(message("settings.Confluence.verifyToken.emptyHost"),
                    message("settings.Confluence.verifyToken.error.title"));
            return;
        }
        String type = ((AtlassianKind) typeCombo.getSelectedItem()).name();
        String token = new String(tokenField.getPassword());
        boolean primary = defaultBox.isSelected();

        int target = row;
        if (adding) {
            tableModel.addRow(new Object[]{host, type, primary, token});
            target = tableModel.getRowCount() - 1;
        } else {
            tableModel.setValueAt(host, target, HOST_COLUMN);
            tableModel.setValueAt(type, target, TYPE_COLUMN);
            tableModel.setValueAt(primary, target, DEFAULT_COLUMN);
            tableModel.setValueAt(token, target, TOKEN_COLUMN);
        }
        if (primary) {
            enforceSingleDefault(target, type);
        }
    }

    /** Снимает «по умолчанию» с остальных строк того же типа. */
    private void enforceSingleDefault(int keepRow, String type) {
        for (int other = 0; other < tableModel.getRowCount(); other++) {
            if (other != keepRow && type.equals(effectiveType(other))
                    && Boolean.TRUE.equals(tableModel.getValueAt(other, DEFAULT_COLUMN))) {
                tableModel.setValueAt(Boolean.FALSE, other, DEFAULT_COLUMN);
            }
        }
    }

    private String effectiveType(int row) {
        String type = Objects.toString(tableModel.getValueAt(row, TYPE_COLUMN), "").trim();
        String host = Objects.toString(tableModel.getValueAt(row, HOST_COLUMN), "").trim();
        return AtlassianKind.parse(type, AtlassianKind.detect(host)).name();
    }

    private void configureTokenColumn() {
        // Токен в таблице показываем замаскированным; правка — через диалог editServer.
        serversTable.getColumnModel().getColumn(TOKEN_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                String token = Objects.toString(value, "");
                super.setValue(token.isEmpty() ? "" : "********");
            }
        });
    }

    private static boolean areSame(List<ConfluenceSettingsService.ServerEntry> left, List<ConfluenceSettingsService.ServerEntry> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            ConfluenceSettingsService.ServerEntry a = left.get(i);
            ConfluenceSettingsService.ServerEntry b = right.get(i);
            if (!Objects.equals(a.getHost(), b.getHost()) || !Objects.equals(a.getToken(), b.getToken())
                    || !normalizedType(a).equals(normalizedType(b)) || a.isPrimary() != b.isPrimary()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizedType(ConfluenceSettingsService.ServerEntry entry) {
        return AtlassianKind.parse(entry.getType(), AtlassianKind.detect(entry.getHost())).name();
    }

    private record VerificationResult(boolean success, String message) {
    }
}
