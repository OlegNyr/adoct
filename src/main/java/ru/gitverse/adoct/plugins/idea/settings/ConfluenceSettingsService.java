package ru.gitverse.adoct.plugins.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Data;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service(Service.Level.APP)
@State(
        name = "AsciiDocToolsConfluenceSettings",
        storages = @Storage("AsciiDocToolsConfluenceSettings.xml")
)
public final class ConfluenceSettingsService implements PersistentStateComponent<ConfluenceSettingsService.StateData> {
    private StateData state = new StateData();

    public static ConfluenceSettingsService getInstance() {
        return ApplicationManager.getApplication().getService(ConfluenceSettingsService.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state.copy();
    }

    public List<ServerEntry> getServers() {
        return state.copy().getServers();
    }

    public void setServers(List<ServerEntry> servers) {
        state.servers = StateData.copyServers(servers);
    }

    public Optional<ServerEntry> getServer(String url) {
        String host = normalHost(url);
        return state.getServers()
                .stream()
                .filter(it -> host.equalsIgnoreCase(it.getHost()))
                .findFirst();

    }

    @Data
    public static final class StateData {
        public List<ServerEntry> servers = new ArrayList<>();


        @SneakyThrows
        public static List<ServerEntry> copyServers(List<ServerEntry> source) {
            List<ServerEntry> copy = new ArrayList<>();
            if (source == null) {
                return copy;
            }
            for (ServerEntry entry : source) {
                if (entry == null) {
                    continue;
                }
                String host = normalHost(entry.host);
                copy.add(new ServerEntry(host, entry.token));
            }
            return copy;
        }

        public StateData copy() {
            StateData copy = new StateData();
            copy.servers = copyServers(this.servers);
            return copy;
        }
    }

    @SneakyThrows
    private static String normalHost(String host) {
        URI uri = new URI(host);
        return new URI(uri.getScheme(), uri.getHost(), null, null).toString();
    }

    @Data
    public static final class ServerEntry {
        public String host = "";
        public String token = "";

        public ServerEntry() {
        }

        public ServerEntry(String host, String token) {
            this.host = host;
            this.token = token;
        }
    }
}
