package ru.gitverse.adoct.plugins.idea.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(
        name = "AsciiDocToolsConfluenceExportUiState",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class ConfluenceExportUiStateService implements PersistentStateComponent<ConfluenceExportUiStateService.StateData> {
    private StateData state = new StateData();

    @Override
    public @Nullable StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public void setLastUrl(String lastUrl) {
        state.lastUrl = lastUrl;
    }

    public void setLastDirectory(String lastDirectory) {
        state.lastDirectory = lastDirectory;
    }

    public void setExportColors(boolean exportColors) {
        state.exportColors = exportColors;
    }

    public void setReportOnError(boolean reportOnError) {
        state.reportOnError = reportOnError;
    }

    public void setDebug(boolean debug) {
        state.debug = debug;
    }

    public void setIncludeChildren(boolean includeChildren) {
        state.includeChildren = includeChildren;
    }

    public void setIncludeAttachments(boolean includeAttachments) {
        state.includeAttachments = includeAttachments;
    }

    public String getLastUrl() {
        return state.lastUrl == null ? "" : state.lastUrl;
    }

    public String getLastDirectory() {
        return state.lastDirectory == null ? "" : state.lastDirectory;
    }

    public boolean isExportColors() {
        return state.exportColors;
    }

    public boolean isReportOnError() {
        return state.reportOnError;
    }

    public boolean isDebug() {
        return state.debug;
    }

    public boolean isIncludeChildren() {
        return state.includeChildren;
    }

    public boolean isIncludeAttachments() {
        return state.includeAttachments;
    }

    public static final class StateData {
        public String lastUrl = "";
        public String lastDirectory = "";
        public boolean exportColors = false;
        public boolean reportOnError = false;
        public boolean debug = false;
        public boolean includeChildren = true;
        public boolean includeAttachments = true;
    }
}
