package ru.gitverse.adoct.plugins.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "ConfluenceSettings",
        storages = @Storage("confluence-settings.xml")
)
@Service(Service.Level.APP)
public final class ConfluenceSettings implements PersistentStateComponent<ConfluenceSettings.State> {
    public static class ServerConfig {
        public String name;
        public String url;
        public String token;

        public ServerConfig() {
        }

        public ServerConfig(String name, String url, String token) {
            this.name = name;
            this.url = url;
            this.token = token;
        }
    }

    public static class State {
        public List<ServerConfig> servers = new ArrayList<>();
    }

    private State myState = new State();

    public static ConfluenceSettings getInstance() {
        return ApplicationManager.getApplication().getService(ConfluenceSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }

    public List<ServerConfig> getServers() {
        return myState.servers;
    }

    public void addServer(String name, String url, String token) {
        myState.servers.add(new ServerConfig(name, url, token));
    }

    public void updateServer(int index, String name, String url, String token) {
        if (index >= 0 && index < myState.servers.size()) {
            myState.servers.set(index, new ServerConfig(name, url, token));
        }
    }

    public void deleteServer(int index) {
        if (index >= 0 && index < myState.servers.size()) {
            myState.servers.remove(index);
        }
    }

    public ServerConfig getServer(int index) {
        if (index >= 0 && index < myState.servers.size()) {
            return myState.servers.get(index);
        }
        return null;
    }

    public int getServerCount() {
        return myState.servers.size();
    }
}