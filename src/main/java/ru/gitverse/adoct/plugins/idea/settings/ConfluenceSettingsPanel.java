package ru.gitverse.adoct.plugins.idea.settings;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import ru.gitverse.adoct.client.ConfluenceClient;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;

public class ConfluenceSettingsPanel {
    private JBPanel<?> panel;
    private JBList<ConfluenceSettings.ServerConfig> serverList;
    private DefaultListModel<ConfluenceSettings.ServerConfig> serverListModel;
    private JBTextField nameField;
    private JBTextField urlField;
    private JPasswordField tokenField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton testButton;

    private boolean isModified = false;

    public ConfluenceSettingsPanel() {
        initComponents();
        loadServers();
        setupListeners();
    }

    private void initComponents() {
        panel = new JBPanel<>(new BorderLayout());

        // Server list panel
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("Confluence Servers"));

        serverListModel = new DefaultListModel<>();
        serverList = new JBList<>(serverListModel);
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane serverScrollPane = new JScrollPane(serverList);
        listPanel.add(serverScrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        addButton = new JButton("Add");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        testButton = new JButton("Test Connection");
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(testButton);
        listPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Server Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Name field
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JBLabel("Name:"), gbc);

        nameField = new JBTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        formPanel.add(nameField, gbc);

        // URL field
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JBLabel("URL:"), gbc);

        urlField = new JBTextField(30);
        gbc.gridx = 1;
        gbc.gridy = 1;
        formPanel.add(urlField, gbc);

        // Token field
        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JBLabel("Token:"), gbc);

        tokenField = new JPasswordField(20);
        gbc.gridx = 1;
        gbc.gridy = 2;
        formPanel.add(tokenField, gbc);

        // Main layout
        panel.add(listPanel, BorderLayout.CENTER);
        panel.add(formPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        // Server list selection listener
        serverList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ConfluenceSettings.ServerConfig selected = serverList.getSelectedValue();
                if (selected != null) {
                    nameField.setText(selected.name);
                    urlField.setText(selected.url);
                    tokenField.setText(selected.token);
                } else {
                    nameField.setText("");
                    urlField.setText("");
                    tokenField.setText("");
                }
            }
        });

        // Text field listeners for modification tracking
        nameField.getDocument().addDocumentListener(new EnabledModifiedDocumentListener());

        urlField.getDocument().addDocumentListener(new EnabledModifiedDocumentListener());

        tokenField.getDocument().addDocumentListener(new EnabledModifiedDocumentListener());

        // Add button
        addButton.addActionListener(e -> showServerDialog(null));

        // Edit button
        editButton.addActionListener(e -> {
            ConfluenceSettings.ServerConfig selected = serverList.getSelectedValue();
            if (selected != null) {
                showServerDialog(selected);
            } else {
                Messages.showWarningDialog("Please select a server to edit", "Edit Server");
            }
        });

        // Delete button
        deleteButton.addActionListener(e -> {
            ConfluenceSettings.ServerConfig selected = serverList.getSelectedValue();
            if (selected != null) {
                int result = Messages.showYesNoDialog(
                        "Are you sure you want to delete server: " + selected.name + "?",
                        "Delete Server",
                        Messages.getQuestionIcon()
                );
                if (result == Messages.YES) {
                    deleteServer(selected);
                }
            } else {
                Messages.showWarningDialog("Please select a server to delete", "Delete Server");
            }
        });

        // Test button
        testButton.addActionListener(e -> testConnection());
    }

    private void loadServers() {
        ConfluenceSettings settings = ConfluenceSettings.getInstance();
        for (ConfluenceSettings.ServerConfig server : settings.getServers()) {
            serverListModel.addElement(server);
        }
    }

    private void showServerDialog(ConfluenceSettings.ServerConfig existing) {
        // If editing, pre-fill the form
//        if (existing != null) {
//            nameField.setText(existing.name);
//            urlField.setText(existing.url);
//            tokenField.setText(existing.token);
//        } else {
//            nameField.setText("");
//            urlField.setText("");
//            tokenField.setText("");
//        }

        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        dialogPanel.add(new JBLabel("Name:"), gbc);

        JTextField nameTextField = new JTextField(nameField.getText(), 20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        dialogPanel.add(nameTextField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        dialogPanel.add(new JBLabel("URL:"), gbc);

        JTextField urlTextField = new JTextField(urlField.getText(), 30);
        gbc.gridx = 1;
        gbc.gridy = 1;
        dialogPanel.add(urlTextField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        dialogPanel.add(new JBLabel("Token:"), gbc);

        JPasswordField tokenPasswordField = new JPasswordField(tokenField.getText(), 20);
        gbc.gridx = 1;
        gbc.gridy = 2;
        dialogPanel.add(tokenPasswordField, gbc);

        int result = Messages.showYesNoCancelDialog(
                dialogPanel,
                "Применить?",
                existing != null ? "Edit Server" : "Add Server",
                Messages.getQuestionIcon()
        );

        if (result == Messages.OK) {
            String name = nameTextField.getText().trim();
            String url = urlTextField.getText().trim();
            String token = new String(tokenPasswordField.getPassword()).trim();

            if (name.isEmpty() || url.isEmpty()) {
                Messages.showErrorDialog("Name and URL are required", "Validation Error");
                return;
            }

            if (existing != null) {
                updateServer(existing, name, url, token);
            } else {
                addServer(name, url, token);
            }
        }
    }

    private void addServer(String name, String url, String token) {
        ConfluenceSettings settings = ConfluenceSettings.getInstance();
        settings.addServer(name, url, token);
        serverListModel.addElement(new ConfluenceSettings.ServerConfig(name, url, token));
        isModified = true;
    }

    private void updateServer(ConfluenceSettings.ServerConfig existing, String name, String url, String token) {
        ConfluenceSettings settings = ConfluenceSettings.getInstance();
        int index = serverListModel.indexOf(existing);
        if (index >= 0) {
            settings.updateServer(index, name, url, token);
            serverListModel.set(index, new ConfluenceSettings.ServerConfig(name, url, token));
            isModified = true;
        }
    }

    private void deleteServer(ConfluenceSettings.ServerConfig server) {
        ConfluenceSettings settings = ConfluenceSettings.getInstance();
        int index = serverListModel.indexOf(server);
        if (index >= 0) {
            settings.deleteServer(index);
            serverListModel.remove(index);
            isModified = true;
        }
    }

    private void testConnection() {
        String url = urlField.getText().trim();
        String token = new String(tokenField.getPassword()).trim();

        if (url.isEmpty()) {
            Messages.showErrorDialog("Please enter a URL", "Test Connection");
            return;
        }

        try {
            ConfluenceClient client = new ConfluenceClient(url, token);
            int result = client.verifyToken();
            if (result != 200) {
                Messages.showInfoMessage("Connection successful!", "Test Connection");
            } else {
                Messages.showErrorDialog("Failed to verify token", "Test Connection");
            }
        } catch (Exception ex) {
            Messages.showErrorDialog("Connection failed: " + ex.getMessage(), "Test Connection");
        }
    }

    public boolean isModified() {
        return isModified;
    }

    public void apply() {
        isModified = false;
    }

    public void reset() {
        isModified = false;
        loadServers();
    }

    @NotNull
    public JComponent getPanel() {
        return panel;
    }

    @NotNull
    public JComponent getPreferredFocusedComponent() {
        return nameField;
    }

    private class EnabledModifiedDocumentListener implements javax.swing.event.DocumentListener {
        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) { isModified = true; }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) { isModified = true; }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) { isModified = true; }
    }
}
