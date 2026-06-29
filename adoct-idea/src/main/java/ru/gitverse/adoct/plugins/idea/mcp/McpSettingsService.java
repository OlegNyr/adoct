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

    /** Конфигурация типов задач (шаблон + состояния), копия. */
    public List<TemplateState> getTemplates() {
        return state.templates == null ? new ArrayList<>() : new ArrayList<>(state.templates);
    }

    /** Включены ли инструменты Jira. */
    public boolean isToolsJira() {
        return state.toolsJira;
    }

    /** Включены ли инструменты Confluence. */
    public boolean isToolsConfluence() {
        return state.toolsConfluence;
    }

    /** Включены ли инструменты Bitbucket. */
    public boolean isToolsBitbucket() {
        return state.toolsBitbucket;
    }

    /**
     * Снимок текущего состояния — основа для частичного сохранения: страница настроек берёт снимок,
     * переопределяет только свой срез и зовёт {@link #loadState}, не затирая поля других страниц.
     */
    public StateData snapshot() {
        StateData copy = new StateData();
        copy.enabled = state.enabled;
        copy.bindHost = getBindHost();
        copy.port = getPort();
        copy.defaultJiraProject = getDefaultJiraProject();
        copy.defaultConfluenceSpace = getDefaultConfluenceSpace();
        copy.team = getTeam();
        copy.templates = getTemplates();
        copy.toolsJira = state.toolsJira;
        copy.toolsConfluence = state.toolsConfluence;
        copy.toolsBitbucket = state.toolsBitbucket;
        return copy;
    }

    public static final class StateData {
        public boolean enabled = true;
        public String bindHost = "127.0.0.1";
        public int port = 7337;
        public String defaultJiraProject = "";
        public String defaultConfluenceSpace = "";
        public List<TeamMemberState> team = new ArrayList<>();
        public List<TemplateState> templates = new ArrayList<>();
        public boolean toolsJira = true;
        public boolean toolsConfluence = true;
        public boolean toolsBitbucket = true;
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

    /** Конфигурация типа задачи (тип + шаблон + состояния PlantUML) для XML-сериализации. */
    public static final class TemplateState {
        public String issueType = "";
        public String body = "";
        public String workflow = "";

        public TemplateState() {
        }

        public TemplateState(String issueType, String body, String workflow) {
            this.issueType = issueType;
            this.body = body;
            this.workflow = workflow;
        }
    }
}
