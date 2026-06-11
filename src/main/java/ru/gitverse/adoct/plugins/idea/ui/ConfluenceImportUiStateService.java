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
        name = "AsciiDocToolsConfluenceImportUiState",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class ConfluenceImportUiStateService
        implements PersistentStateComponent<ConfluenceImportUiStateService.StateData> {
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

    public void setLastSource(String lastSource) {
        state.lastSource = lastSource;
    }

    public String getLastUrl() {
        return state.lastUrl == null ? "" : state.lastUrl;
    }

    public String getLastSource() {
        return state.lastSource == null ? "" : state.lastSource;
    }

    public static final class StateData {
        public String lastUrl = "";
        public String lastSource = "";
    }
}
