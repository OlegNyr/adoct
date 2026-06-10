package org.tools.asciidoc.plugins.idea.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import ru.gitverse.adoct.ConvertDocsToAdocZip;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.tools.asciidoc.plugins.idea.Bundle.message;

public class FileImporterToolWindow {
    private final Project project;
    private JPanel contentPanel;
    private TextFieldWithBrowseButton sourceFileField;
    private TextFieldWithBrowseButton targetDirectoryField;
    private JButton importButton;

    public FileImporterToolWindow(@NotNull Project project) {
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

        // Source file selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel(message("toolwindow.FileImporter.SourceFile.caption")), gbc);

        sourceFileField = new TextFieldWithBrowseButton();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        contentPanel.add(sourceFileField, gbc);

        // File name (optional to change)
//
//        fileNameField = new JBTextField();
//        gbc.gridx = 1;
//        gbc.gridy = 1;
//        contentPanel.add(fileNameField, gbc);

        // Target directory selection
        gbc.gridx = 0;
        gbc.gridy = 2;
        contentPanel.add(new JLabel(message("toolwindow.FileImporter.TargetDirectory.caption")), gbc);

        targetDirectoryField = new TextFieldWithBrowseButton();
        gbc.gridx = 1;
        gbc.gridy = 2;
        contentPanel.add(targetDirectoryField, gbc);

        // Import button
        importButton = new JButton(message("toolwindow.FileImporter.Import.caption"));
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
        // Source file chooser
        sourceFileField.addActionListener(e -> {
            FileChooserDescriptor descriptor = FileChooserDescriptorFactory.singleFile();
            VirtualFile chosenFile = FileChooser.chooseFile(descriptor, project, null);
            if (chosenFile != null) {
                sourceFileField.setText(chosenFile.getPath());

            }
        });

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
        String sourcePath = sourceFileField.getText();
        String targetDirPath = targetDirectoryField.getText();

        // Validation
        if (sourcePath.isEmpty()) {
            Messages.showErrorDialog("Please select source file", message("toolwindow.FileImporter.Import.error.message.caption"));
            return;
        }

        if (targetDirPath.isEmpty()) {
            Messages.showErrorDialog("Please select target directory", message("toolwindow.FileImporter.Import.error.message.caption"));
            return;
        }


        try {
            Path sourceFile = Path.of(sourcePath);
            Path targetDir = Path.of(targetDirPath);
            ConvertDocsToAdocZip convertDocsToAdocZip = new ConvertDocsToAdocZip();
            Path targetFile = convertDocsToAdocZip.convert(sourceFile, targetDir);

            // Refresh project view
            project.getBaseDir().refresh(false, true);

            Messages.showInfoMessage(
                    String.format("File imported successfully to:\n%s", targetFile),
                    message("toolwindow.FileImporter.Import.successful.message.caption")
            );

        } catch (Exception ex) {
            Messages.showErrorDialog(
                    String.format("Error importing file: %s", ex.getMessage()),
                    message("toolwindow.FileImporter.Import.error.message.caption")
            );
        }
    }

    public JPanel getContent() {
        return contentPanel;
    }
}