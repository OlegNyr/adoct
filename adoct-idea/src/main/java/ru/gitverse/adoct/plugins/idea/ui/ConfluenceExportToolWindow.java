package ru.gitverse.adoct.plugins.idea.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import ru.gitverse.adoct.plugins.idea.service.BugReportService;
import ru.gitverse.adoct.plugins.idea.service.ConvertDocsUrlToAdoc;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static ru.gitverse.adoct.plugins.idea.Bundle.message;

public class ConfluenceExportToolWindow {
    private static final String NOTIFICATION_GROUP_ID = "AsciiDocTools.Notifications";

    private final Project project;
    private final ConfluenceExportUiStateService uiStateService;
    private JPanel contentPanel;
    private JBTextField confluenceUrlField;
    private TextFieldWithBrowseButton exportDirectoryField;
    private JCheckBox exportColorsCheckBox;
    private JCheckBox includeChildrenCheckBox;
    private JCheckBox includeAttachmentsCheckBox;
    private JCheckBox debugCheckBox;
    private JCheckBox reportOnErrorCheckBox;
    private JButton exportButton;

    public ConfluenceExportToolWindow(@NotNull Project project) {
        this.project = project;
        this.uiStateService = project.getService(ConfluenceExportUiStateService.class);
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
        contentPanel.add(new JLabel(message("toolwindow.ConfluenceExport.Url.caption")), gbc);

        confluenceUrlField = new JBTextField();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        contentPanel.add(confluenceUrlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        contentPanel.add(new JLabel(message("toolwindow.ConfluenceExport.TargetDirectory.caption")), gbc);

        exportDirectoryField = new TextFieldWithBrowseButton();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        contentPanel.add(exportDirectoryField, gbc);

        exportColorsCheckBox = new JCheckBox(message("toolwindow.ConfluenceExport.ExportColors.caption"));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        contentPanel.add(exportColorsCheckBox, gbc);

        includeChildrenCheckBox = new JCheckBox(message("toolwindow.ConfluenceExport.IncludeChildren.caption"));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        contentPanel.add(includeChildrenCheckBox, gbc);

        includeAttachmentsCheckBox = new JCheckBox(message("toolwindow.ConfluenceExport.IncludeAttachments.caption"));
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        contentPanel.add(includeAttachmentsCheckBox, gbc);

        debugCheckBox = new JCheckBox(message("toolwindow.ConfluenceExport.Debug.caption"));
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        contentPanel.add(debugCheckBox, gbc);

        reportOnErrorCheckBox = new JCheckBox(message("toolwindow.ConfluenceExport.ReportOnError.caption"));
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        contentPanel.add(reportOnErrorCheckBox, gbc);

        exportButton = new JButton(message("toolwindow.ConfluenceExport.Export.caption"));
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPanel.add(exportButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
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
        exportDirectoryField.getTextField().getDocument().addDocumentListener(stateListener);
        exportColorsCheckBox.addActionListener(e -> persistState());
        includeChildrenCheckBox.addActionListener(e -> persistState());
        includeAttachmentsCheckBox.addActionListener(e -> persistState());
        debugCheckBox.addActionListener(e -> persistState());
        reportOnErrorCheckBox.addActionListener(e -> persistState());

        exportDirectoryField.addActionListener(e -> {
            FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            VirtualFile chosenDir = FileChooser.chooseFile(descriptor, project, null);
            if (chosenDir != null) {
                exportDirectoryField.setText(chosenDir.getPath());
                persistState();
            }
        });

        exportButton.addActionListener(e -> exportAction());
    }

    private void exportAction() {
        String url = confluenceUrlField.getText().trim();
        String targetDirPath = exportDirectoryField.getText().trim();
        boolean exportColors = exportColorsCheckBox.isSelected();
        boolean includeChildren = includeChildrenCheckBox.isSelected();
        boolean includeAttachments = includeAttachmentsCheckBox.isSelected();
        boolean debug = debugCheckBox.isSelected();
        boolean reportOnError = reportOnErrorCheckBox.isSelected();
        persistState(url, targetDirPath);

        if (url.isEmpty()) {
            notifyError("Please enter Confluence URL");
            return;
        }
        if (!isValidHttpUrl(url)) {
            notifyError("Confluence URL must start with http:// or https://");
            return;
        }

        if (targetDirPath.isEmpty()) {
            notifyError("Please select export directory");
            return;
        }
        Path targetDir = Path.of(targetDirPath);
        if (!targetDir.toFile().isDirectory()) {
            notifyError("Export directory does not exist");
            return;
        }

        runExportInBackground(url, targetDir, exportColors, includeChildren, includeAttachments, debug, reportOnError);

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
        String savedDirectory = uiStateService.getLastDirectory();

        if (!savedUrl.isBlank()) {
            confluenceUrlField.setText(savedUrl);
        }

        if (!savedDirectory.isBlank()) {
            exportDirectoryField.setText(savedDirectory);
        } else {
            String basePath = project.getBasePath();
            if (basePath != null && !basePath.isBlank()) {
                exportDirectoryField.setText(basePath);
                uiStateService.setLastDirectory(basePath);
            }
        }

        exportColorsCheckBox.setSelected(uiStateService.isExportColors());
        includeChildrenCheckBox.setSelected(uiStateService.isIncludeChildren());
        includeAttachmentsCheckBox.setSelected(uiStateService.isIncludeAttachments());
        debugCheckBox.setSelected(uiStateService.isDebug());
        reportOnErrorCheckBox.setSelected(uiStateService.isReportOnError());
    }

    private void persistState() {
        persistState(confluenceUrlField.getText(), exportDirectoryField.getText(), exportColorsCheckBox.isSelected());
    }

    private void persistState(String url, String directory) {
        persistState(url, directory, exportColorsCheckBox.isSelected());
    }

    private void persistState(String url, String directory, boolean exportColors) {
        uiStateService.setLastUrl(url == null ? "" : url.trim());
        uiStateService.setLastDirectory(directory == null ? "" : directory.trim());
        uiStateService.setExportColors(exportColors);
        uiStateService.setIncludeChildren(includeChildrenCheckBox.isSelected());
        uiStateService.setIncludeAttachments(includeAttachmentsCheckBox.isSelected());
        uiStateService.setDebug(debugCheckBox.isSelected());
        uiStateService.setReportOnError(reportOnErrorCheckBox.isSelected());
    }

    private void runExportInBackground(String url, Path targetDir, boolean exportColors, boolean includeChildren,
                                       boolean includeAttachments, boolean debug, boolean reportOnError) {
        exportButton.setEnabled(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<String> titleRef = new AtomicReference<>();
        AtomicReference<Path> reportRef = new AtomicReference<>();

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                message("toolwindow.ConfluenceExport.Progress.title"),
                true
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText(message("toolwindow.ConfluenceExport.Progress.running.text"));
                    String title = ConvertDocsUrlToAdoc.getInstance()
                            .convert(url, targetDir, exportColors, debug, includeChildren, includeAttachments, indicator);
                    titleRef.set(title);
                    // Принудительный отчёт по запросу пользователя (тихая ошибка без исключения).
                    if (reportOnError) {
                        reportRef.set(BugReportService.getInstance()
                                .captureExportManual(url, targetDir.resolve(title)));
                    }
                } catch (Throwable ex) {
                    errorRef.set(ex);
                }
            }

            @Override
            public void onSuccess() {
                exportButton.setEnabled(true);
                Throwable error = errorRef.get();
                if (error != null) {
                    notifyError(String.format("Error exporting file: %s", error.getMessage()));
                    return;
                }

                String message = String.format(
                        "Export successful.\nURL: %s\nDirectory: %s\nColor export: %s\nTitle: %s",
                        url,
                        targetDir,
                        exportColors ? "enabled" : "disabled",
                        titleRef.get()
                );
                Path report = reportRef.get();
                if (report != null) {
                    message = message + "\nError report: " + report;
                }
                notifyInfo(message);
            }

            @Override
            public void onCancel() {
                exportButton.setEnabled(true);
                notifyWarning("Export canceled.");
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
