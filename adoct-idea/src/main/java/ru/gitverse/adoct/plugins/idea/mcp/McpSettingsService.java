package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки встроенного MCP-сервера: включён ли он, адрес и порт привязки. Хранится в конфиге IDE,
 * читается {@link McpServerService} на старте. UI-редактор — на будущее; пока правится в XML конфига.
 */
@Service(Service.Level.APP)
@State(name = "AsciiDocToolsMcpSettings", storages = @Storage("AsciiDocToolsMcpSettings.xml"))
public final class McpSettingsService implements PersistentStateComponent<McpSettingsService.StateData> {

    private StateData state = new StateData();

    public static McpSettingsService getInstance() {
        return ApplicationManager.getApplication().getService(McpSettingsService.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public String getBindHost() {
        return state.bindHost == null || state.bindHost.isBlank() ? "127.0.0.1" : state.bindHost;
    }

    public int getPort() {
        return state.port <= 0 ? 7337 : state.port;
    }

    /** Проект Jira по умолчанию (однопроектная инсталляция); пусто = не задан. */
    public String getDefaultJiraProject() {
        return state.defaultJiraProject == null ? "" : state.defaultJiraProject.trim();
    }

    /** Пространство Confluence по умолчанию; пусто = не задано. */
    public String getDefaultConfluenceSpace() {
        return state.defaultConfluenceSpace == null ? "" : state.defaultConfluenceSpace.trim();
    }

    /** Ростер команды (копия). */
    public List<TeamMemberState> getTeam() {
        return state.team == null ? new ArrayList<>() : new ArrayList<>(state.team);
    }

    /** Шаблоны задач (копия). */
    public List<TemplateState> getTemplates() {
        return state.templates == null ? new ArrayList<>() : new ArrayList<>(state.templates);
    }

    /** Диаграмма состояний задач (PlantUML); пусто = не задана. */
    public String getWorkflowDiagram() {
        return state.workflowDiagram == null ? "" : state.workflowDiagram;
    }

    public static final class StateData {
        public boolean enabled = true;
        public String bindHost = "127.0.0.1";
        public int port = 7337;
        public String defaultJiraProject = "";
        public String defaultConfluenceSpace = "";
        public List<TeamMemberState> team = new ArrayList<>();
        public List<TemplateState> templates = new ArrayList<>();
        public String workflowDiagram = "";
    }

    /** Участник команды для XML-сериализации (нужен no-arg конструктор). */
    public static final class TeamMemberState {
        public String username = "";
        public String displayName = "";
        public String role = "";

        public TeamMemberState() {
        }

        public TeamMemberState(String username, String displayName, String role) {
            this.username = username;
            this.displayName = displayName;
            this.role = role;
        }
    }

    /** Шаблон задачи (имя + свободный текст) для XML-сериализации. */
    public static final class TemplateState {
        public String name = "";
        public String body = "";

        public TemplateState() {
        }

        public TemplateState(String name, String body) {
            this.name = name;
            this.body = body;
        }
    }
}
