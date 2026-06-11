package org.tools.asciidoc.plugins.idea.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.tools.asciidoc.plugins.idea.service.PublishDocsToConfluence;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.tools.asciidoc.plugins.idea.Bundle.message;

public class ConfluenceImportToolWindow {
    private static final String NOTIFICATION_GROUP_ID = "AsciiDocTools.Notifications";

    private final Project project;
    private final ConfluenceImportUiStateService uiStateService;
    private JPanel contentPanel;
    private JBTextField confluenceUrlField;
    private TextFieldWithBrowseButton sourceField;
    private JButton importButton;

    public ConfluenceImportToolWindow(@NotNull Project project) {
        this.project = project;
        this.uiStateService = project.getService(ConfluenceImportUiStateService.class);
        initComponents();
        applyInitialValues();
        setupListeners();
    }

    private void initComponents() {
        contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel(message("toolwindow.ConfluenceImport.Url.caption")), gbc);

        confluenceUrlField = new JBTextField();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        contentPanel.add(confluenceUrlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        contentPanel.add(new JLabel(message("toolwindow.ConfluenceImport.Source.caption")), gbc);

        sourceField = new TextFieldWithBrowseButton();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        contentPanel.add(sourceField, gbc);

        importButton = new JButton(message("toolwindow.ConfluenceImport.Import.caption"));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPanel.add(importButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(Box.createVerticalGlue(), gbc);
    }

    private void setupListeners() {
        DocumentListener stateListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                persistState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                persistState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                persistState();
            }
        };

        confluenceUrlField.getDocument().addDocumentListener(stateListener);
        sourceField.getTextField().getDocument().addDocumentListener(stateListener);

        sourceField.addActionListener(e -> {
            // Источник — одиночный .adoc или папка с .adoc.
            FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, false)
                    .withFileFilter(file -> file.isDirectory() || "adoc".equalsIgnoreCase(file.getExtension()));
            VirtualFile chosen = FileChooser.chooseFile(descriptor, project, null);
            if (chosen != null) {
                sourceField.setText(chosen.getPath());
                persistState();
            }
        });

        importButton.addActionListener(e -> importAction());
    }

    private void importAction() {
        String url = confluenceUrlField.getText().trim();
        String sourcePath = sourceField.getText().trim();
        persistState(url, sourcePath);

        if (url.isEmpty()) {
            notifyError("Please enter Confluence URL");
            return;
        }
        if (!isValidHttpUrl(url)) {
            notifyError("Confluence URL must start with http:// or https://");
            return;
        }
        if (sourcePath.isEmpty()) {
            notifyError("Please select a source .adoc file or folder");
            return;
        }
        Path source = Path.of(sourcePath);
        if (!Files.exists(source)) {
            notifyError("Source does not exist: " + sourcePath);
            return;
        }

        runImportInBackground(url, source);
    }

    private static boolean isValidHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public JPanel getContent() {
        return contentPanel;
    }

    private void applyInitialValues() {
        String savedUrl = uiStateService.getLastUrl();
        String savedSource = uiStateService.getLastSource();

        if (!savedUrl.isBlank()) {
            confluenceUrlField.setText(savedUrl);
        }
        if (!savedSource.isBlank()) {
            sourceField.setText(savedSource);
        } else {
            String basePath = project.getBasePath();
            if (basePath != null && !basePath.isBlank()) {
                sourceField.setText(basePath);
                uiStateService.setLastSource(basePath);
            }
        }
    }

    private void persistState() {
        persistState(confluenceUrlField.getText(), sourceField.getText());
    }

    private void persistState(String url, String source) {
        uiStateService.setLastUrl(url == null ? "" : url.trim());
        uiStateService.setLastSource(source == null ? "" : source.trim());
    }

    private void runImportInBackground(String url, Path source) {
        importButton.setEnabled(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<String> summaryRef = new AtomicReference<>();

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                message("toolwindow.ConfluenceImport.Progress.title"),
                true
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText(message("toolwindow.ConfluenceImport.Progress.running.text"));
                    summaryRef.set(PublishDocsToConfluence.getInstance().publish(url, source, indicator));
                } catch (Throwable ex) {
                    errorRef.set(ex);
                }
            }

            @Override
            public void onSuccess() {
                importButton.setEnabled(true);
                Throwable error = errorRef.get();
                if (error != null) {
                    notifyError(String.format("Error importing to Confluence: %s", error.getMessage()));
                    return;
                }
                notifyInfo(String.format("Import successful.%nURL: %s%nSource: %s%n%s",
                        url, source, summaryRef.get()));
            }

            @Override
            public void onCancel() {
                importButton.setEnabled(true);
                notifyWarning("Import canceled.");
            }
        });
    }

    private void notifyInfo(String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification("AsciiDocTools", content, NotificationType.INFORMATION)
                .notify(project);
    }

    private void notifyWarning(String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification("AsciiDocTools", content, NotificationType.WARNING)
                .notify(project);
    }

    private void notifyError(String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification("AsciiDocTools", content, NotificationType.ERROR)
                .notify(project);
    }
}
