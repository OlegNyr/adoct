package org.tools.asciidoc.plugins.idea.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.tools.asciidoc.plugins.idea.Bundle.message;

public class ConfluenceImporterToolWindow {
    private final Project project;
    private JPanel contentPanel;
    private JBTextField urlField;
    private TextFieldWithBrowseButton targetDirectoryField;
    private JBCheckBox useColorsCheckbox;
    private JButton importButton;

    public ConfluenceImporterToolWindow(@NotNull Project project) {
        this.project = project;
        initComponents();
        setupListeners();
    }

    private void initComponents() {
        contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // URL field
        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel(message("toolwindow.ConfluenceImporter.URL.caption")), gbc);

        urlField = new JBTextField();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        contentPanel.add(urlField, gbc);

        // Target directory selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        contentPanel.add(new JLabel(message("toolwindow.ConfluenceImporter.Directory.caption")), gbc);

        targetDirectoryField = new TextFieldWithBrowseButton();
        gbc.gridx = 1;
        gbc.gridy = 1;
        contentPanel.add(targetDirectoryField, gbc);

        // Use colors checkbox
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        useColorsCheckbox = new JBCheckBox(message("toolwindow.ConfluenceImporter.UseColors.caption"));
        contentPanel.add(useColorsCheckbox, gbc);

        // Import button
        importButton = new JButton(message("toolwindow.ConfluenceImporter.Import.caption"));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPanel.add(importButton, gbc);

        // Spacer to push everything up
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        contentPanel.add(Box.createVerticalGlue(), gbc);
    }

    private void setupListeners() {
        // Target directory chooser
        targetDirectoryField.addActionListener(e -> {
            FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            VirtualFile chosenDir = FileChooser.chooseFile(descriptor, project, null);
            if (chosenDir != null) {
                targetDirectoryField.setText(chosenDir.getPath());
            }
        });

        // Import button action
        importButton.addActionListener(e -> importFile());
    }

    private void importFile() {
        String url = urlField.getText();
        String targetDirPath = targetDirectoryField.getText();
        boolean useColors = useColorsCheckbox.isSelected();

        // Validation
        if (url.isEmpty()) {
            Messages.showErrorDialog("Please enter Confluence URL", message("toolwindow.ConfluenceImporter.Import.error.message.caption"));
            return;
        }

        if (targetDirPath.isEmpty()) {
            Messages.showErrorDialog("Please select target directory", message("toolwindow.ConfluenceImporter.Import.error.message.caption"));
            return;
        }

        try {
            if (url == null || url.isEmpty()) {
                Messages.showErrorDialog("Invalid Confluence URL", message("toolwindow.ConfluenceImporter.Import.error.message.caption"));
                return;
            }

            // Create target directory
            Path targetDir = Path.of(targetDirPath);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Refresh project view
            project.getBaseDir().refresh(false, true);

            Messages.showInfoMessage(
                    String.format("Page imported successfully to:\n%s", targetDir),
                    message("toolwindow.ConfluenceImporter.Import.successful.message.caption")
            );

        } catch (Exception ex) {
            Messages.showErrorDialog(
                    String.format("Error importing page: %s", ex.getMessage()),
                    message("toolwindow.ConfluenceImporter.Import.error.message.caption")
            );
        }
    }


    public JPanel getContent() {
        return contentPanel;
    }
}
