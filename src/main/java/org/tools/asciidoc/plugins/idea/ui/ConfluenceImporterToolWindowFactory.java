package org.tools.asciidoc.plugins.idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class ConfluenceImporterToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ConfluenceImporterToolWindow confluenceImporterToolWindow = new ConfluenceImporterToolWindow(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(
                confluenceImporterToolWindow.getContent(),
                "Confluence Importer",
                false
        );
        toolWindow.getContentManager().addContent(content);
    }
}
