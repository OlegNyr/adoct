package org.tools.asciidoc.plugins.idea.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class ShowFileImporterAction extends AnAction {
    private static final String TOOL_WINDOW_ID = "File Importer";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow != null) {
                toolWindow.setAvailable(true, null);
                toolWindow.setShowStripeButton(true);
                toolWindow.show(() -> toolWindow.activate(null, true, true));
            }
        }
    }
}
