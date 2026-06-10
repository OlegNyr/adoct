package org.tools.asciidoc.plugins.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.gitverse.adoct.client.ConfluenceClient;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.tools.asciidoc.plugins.idea.Bundle.message;

public class ConfluenceSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private static final int HOST_COLUMN = 0;
    private static final int TOKEN_COLUMN = 1;

    private JPanel panel;
    private JBTable serversTable;
    private DefaultTableModel tableModel;
    private JButton verifyTokenButton;

    @Override
    public @NotNull String getId() {
        return "org.tools.asciidoc.plugins.idea.settings.confluence";
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
                            message("settings.Confluence.table.token")
                    }
            ) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return true;
                }
            };

            serversTable = new JBTable(tableModel);
            serversTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            configureTokenColumn();

            JPanel tablePanel = ToolbarDecorator.createDecorator(serversTable)
                    .setAddAction(button -> tableModel.addRow(new Object[]{"", ""}))
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
        tableModel.setRowCount(0);
        for (ConfluenceSettingsService.ServerEntry server : ConfluenceSettingsService.getInstance().getServers()) {
            tableModel.addRow(new Object[]{server.getHost(), server.getToken()});
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
            VerificationResult result = checkToken(host, token);
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

    private static VerificationResult checkToken(String host, String token) {
        try {
            ConfluenceClient client = new ConfluenceClient(host, token);
            int code = client.verifyToken();
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
            servers.add(new ConfluenceSettingsService.ServerEntry(host, token));
        }
        return servers;
    }

    private void configureTokenColumn() {
        // Hide token value in plain text in table view.
        serversTable.getColumnModel().getColumn(TOKEN_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                String token = Objects.toString(value, "");
                super.setValue(token.isEmpty() ? "" : "********");
            }
        });

        serversTable.getColumnModel().getColumn(TOKEN_COLUMN).setCellEditor(new DefaultCellEditor(new JPasswordField()) {
            @Override
            public Object getCellEditorValue() {
                Object value = super.getCellEditorValue();
                return Objects.toString(value, "");
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
            if (!Objects.equals(a.getHost(), b.getHost()) || !Objects.equals(a.getToken(), b.getToken())) {
                return false;
            }
        }
        return true;
    }

    private record VerificationResult(boolean success, String message) {
    }
}
