package io.github.adoct.parser.confluence;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import io.github.adoct.parser.confluence.content.Attachment;
import io.github.adoct.parser.confluence.content.Body;
import io.github.adoct.parser.confluence.content.ContentMainPage;
import io.github.adoct.parser.confluence.content.History;
import io.github.adoct.parser.confluence.content.Label;
import io.github.adoct.parser.confluence.content.Labels;
import io.github.adoct.parser.confluence.content.LinksDto;
import io.github.adoct.parser.confluence.content.Metadata;
import io.github.adoct.parser.confluence.content.Person;
import io.github.adoct.parser.confluence.content.ResultDto;
import io.github.adoct.parser.confluence.content.Storage;
import io.github.adoct.parser.confluence.content.Version;
import io.github.adoct.parser.confluence.content.View;
import io.github.adoct.parser.confluence.searche.ConfluenceSearchResult;
import io.github.adoct.parser.confluence.user.UserDto;

import javax.net.ssl.SSLContext;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * REST-клиент Confluence для чтения/экспорта.
 *
 * <p>Держит <b>один</b> {@link CloseableHttpClient} с пулом соединений на всё время жизни: выгрузка
 * пространства — это тысячи запросов, и поднимать TLS-соединение заново на каждый из них дороже, чем
 * сам запрос. Поэтому клиент {@link Closeable} — владелец обязан его закрыть (обычно
 * try-with-resources вокруг всей выгрузки).
 */
@Slf4j
public class ConfluenceClient implements ConfluenceGateway, Closeable {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    /** Размер пула соединений: с запасом под будущую параллельную выгрузку страниц и вложений. */
    private static final int MAX_CONNECTIONS = 32;
    /** Через сколько выбрасывать простаивающие соединения (Confluence за балансировщиком рвёт их сам). */
    private static final long IDLE_EVICT_SECONDS = 30L;
    /** Размер страницы при постраничном чтении вложений (дефолт Confluence — 25). */
    private static final int ATTACHMENT_PAGE_SIZE = 200;

    private final PoolingHttpClientConnectionManager connectionManager;
    private final CloseableHttpClient httpClient;
    private final String host;
    private String urlBase;

    public ConfluenceClient(String host, String token) {
        SSLContext sslContext = ConfluenceSsl.newInstance(ConfluenceSsl.NOT_VERIFY_SSL);
        // Настройки SSL с билдера игнорируются, если задан свой connection manager, — поэтому
        // самоподписанные сертификаты приходится разрешать в самой фабрике сокетов.
        Registry<ConnectionSocketFactory> socketFactories = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", new SSLConnectionSocketFactory(sslContext, ConfluenceSsl.NOT_VERIFY_HOST))
                .build();
        connectionManager = new PoolingHttpClientConnectionManager(socketFactories);
        connectionManager.setMaxTotal(MAX_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS);

        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(IDLE_EVICT_SECONDS, TimeUnit.SECONDS)
                .setDefaultHeaders(List.of(new BasicHeader(AUTHORIZATION_HEADER, "Bearer " + token)))
                .build();

        this.host = host;
        this.urlBase = this.host + "/rest/api";
    }

    /**
     * Закрывает пул соединений. После вызова клиент непригоден.
     * <p>
     * Не бросает: закрытие пула — уборка, реагировать вызывающему всё равно не на что, а
     * {@code throws IOException} заставлял бы каждый try-with-resources городить лишний catch.
     */
    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("Не удалось закрыть HTTP-клиент Confluence: {}", e.getMessage());
        }
        connectionManager.close();
    }

    @SneakyThrows
    public int verifyToken() {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/user/current")
                .build();

        return httpClient.execute(httpRequest, httpResponse -> {
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                EntityUtils.consume(httpResponse.getEntity());
                throw new HttpResponseException(statusCode, httpResponse.getStatusLine().getReasonPhrase());
            } else if (statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_PARTIAL_CONTENT) {
                EntityUtils.consume(httpResponse.getEntity());
                log.error("Status code {}", statusCode);
                return statusCode;
            } else {
                return statusCode;
            }
        });
    }

    @SneakyThrows
    public ContentPage getMainPage(String id) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/%s".formatted(id))
                // metadata.labels — чтобы не ходить за метками отдельным запросом на каждую страницу
                .addParameter("expand", "version,body.storage,body.view,space,history,metadata.labels")
                .build();

        String body = doRequestAndFailIfNot20x(httpRequest);
        ContentMainPage contentMainPage = ObjectMapperExt.INSTANT.readValue(body, ContentMainPage.class);
        Optional<Body> optionalBody = Optional.of(contentMainPage)
                .map(ContentMainPage::getBody);
        String content = optionalBody
                .map(Body::getStorage)
                .map(Storage::getValue)
                .orElse(null);

        String view = optionalBody
                .map(Body::getView)
                .map(View::getValue)
                .orElse(null);


        String date = Optional.of(contentMainPage)
                .map(ContentMainPage::getVersion)
                .map(Version::getWhen)
                .orElse(null);

        String space = Optional.of(contentMainPage).map(ContentMainPage::getSpace).map(s -> s.getKey()).orElse(null);
        Optional<History> history = Optional.of(contentMainPage).map(ContentMainPage::getHistory);
        String createdDate = history.map(History::getCreatedDate).orElse(null);
        String author = history.map(History::getCreatedBy).map(Person::getDisplayName).orElse(null);

        Map<String, LinkResult> attachment = getAttachments(id);

        return new ContentPage(contentMainPage.getTitle(), host + contentMainPage.getLinks().getWebui(),
                date, content, view, attachment, space, createdDate, author, labelsOf(contentMainPage));
    }

    /**
     * Метки из уже разобранного ответа ({@code expand=metadata.labels}). {@code null}, если Confluence
     * секцию не отдал — тогда вызывающий сходит за метками отдельным запросом ({@link #labels(String)}).
     */
    private static List<String> labelsOf(ContentMainPage page) {
        // Metadata — legacy-DTO без @Data, поля читаем напрямую.
        Labels labels = Optional.ofNullable(page.getMetadata()).map(metadata -> metadata.labels).orElse(null);
        if (labels == null || labels.getResults() == null) {
            return null;
        }
        return labels.getResults().stream()
                .map(Label::displayName)
                .filter(name -> !name.isBlank())
                .toList();
    }

    /**
     * Находит ID страницы по ключу пространства и точному заголовку — для «человеческих» URL
     * {@code /display/SPACE/Title}, где номера страницы нет.
     *
     * @return ID первой подходящей страницы, либо пусто, если такой страницы нет
     */
    @SneakyThrows
    public Optional<String> findPageId(String spaceKey, String title) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content")
                .addParameter("type", "page")
                .addParameter("spaceKey", spaceKey)
                .addParameter("title", title)
                .addParameter("limit", "1")
                .build();

        String body = doRequestAndFailIfNot20x(httpRequest);
        if (body == null) {
            return Optional.empty();
        }
        ConfluenceSearchResult result = ObjectMapperExt.INSTANT.readValue(body, ConfluenceSearchResult.class);
        return Optional.ofNullable(result.getResults())
                .filter(results -> !results.isEmpty())
                .map(results -> results.get(0).getId());
    }

    public Map<String, LinkResult> getAttachments(String id) {
        Map<String, LinkResult> res = new HashMap<>();
        getAttachments(res, id, 0);
        return res;
    }

    /** ID прямых дочерних страниц (с пагинацией по 100). */
    @SneakyThrows
    @Override
    public List<String> getChildPageIds(String id) {
        return childPages(id).stream().map(PageRef::id).toList();
    }

    @Override
    public List<PageRef> childPages(String id) {
        return pagedRefs(urlBase + "/content/%s/child/page".formatted(id));
    }

    @Override
    public List<PageRef> spaceRootPages(String spaceKey) {
        return pagedRefs(urlBase + "/space/%s/content/page".formatted(spaceKey), "depth", "root");
    }

    /**
     * Все страницы пространства одним пакетом: {@code /space/{key}/content/page} без {@code depth=root}
     * отдаёт всё дерево, а {@code expand=ancestors} — цепочку предков каждой страницы, из которой
     * восстанавливается родитель. Пагинация по 100 вместо запроса на каждую страницу.
     */
    @Override
    public List<PageNode> spaceTree(String spaceKey) {
        return pagedNodes(urlBase + "/space/%s/content/page".formatted(spaceKey), "expand", "ancestors,version");
    }

    /**
     * Все потомки страницы одним пакетом через CQL {@code ancestor = <id>}. Сама страница в выдачу
     * не входит — родитель для её прямых детей вычисляется по {@code ancestors}.
     */
    @Override
    public List<PageNode> subtree(String rootId) {
        return pagedNodes(urlBase + "/content/search",
                "cql", "ancestor = %s and type = page".formatted(rootId),
                "expand", "ancestors,version");
    }

    /**
     * Постранично собирает {@link PageNode} по URL, отдающему список контента с {@code ancestors}.
     * <p>
     * Best-effort: любая ошибка (нет прав, эндпоинт недоступен, упёрлись в лимит глубины пагинации
     * CQL) — пустой список, чтобы вызывающий откатился на обход дерева по одной странице.
     */
    private List<PageNode> pagedNodes(String uri, String... extraParams) {
        List<PageNode> nodes = new ArrayList<>();
        int start = 0;
        int limit = 100;
        try {
            while (true) {
                RequestBuilder request = RequestBuilder.get()
                        .setUri(uri)
                        .addParameter("start", String.valueOf(start))
                        .addParameter("limit", String.valueOf(limit));
                for (int i = 0; i + 1 < extraParams.length; i += 2) {
                    request.addParameter(extraParams[i], extraParams[i + 1]);
                }
                String body = doRequestAndFailIfNot20x(request.build());
                if (body == null) {
                    break;
                }
                int before = nodes.size();
                for (com.fasterxml.jackson.databind.JsonNode r : ObjectMapperExt.INSTANT.readTree(body).path("results")) {
                    String id = r.path("id").asText(null);
                    String title = r.path("title").asText(null);
                    if (id != null && title != null) {
                        nodes.add(new PageNode(id, title, lastAncestorId(r),
                                r.path("version").path("when").asText(null)));
                    }
                }
                int page = nodes.size() - before;
                if (page < limit) {
                    break;
                }
                start += page;
            }
        } catch (Exception e) {
            log.warn("Не удалось загрузить дерево страниц пакетом ({}): {} — откатываемся на обход по одной",
                    uri, e.getMessage());
            return List.of();
        }
        return nodes;
    }

    /** Id непосредственного родителя — последний элемент {@code ancestors}; {@code null} для верхнего уровня. */
    private static String lastAncestorId(com.fasterxml.jackson.databind.JsonNode result) {
        com.fasterxml.jackson.databind.JsonNode ancestors = result.path("ancestors");
        if (!ancestors.isArray() || ancestors.isEmpty()) {
            return null;
        }
        return ancestors.get(ancestors.size() - 1).path("id").asText(null);
    }

    /** Постранично собирает {@code (id, title)} по URL, отдающему {@link ConfluenceSearchResult}. */
    @SneakyThrows
    private List<PageRef> pagedRefs(String uri, String... extraParams) {
        List<PageRef> refs = new ArrayList<>();
        int start = 0;
        int limit = 100;
        while (true) {
            RequestBuilder request = RequestBuilder.get()
                    .setUri(uri)
                    .addParameter("start", String.valueOf(start))
                    .addParameter("limit", String.valueOf(limit));
            for (int i = 0; i + 1 < extraParams.length; i += 2) {
                request.addParameter(extraParams[i], extraParams[i + 1]);
            }
            String body = doRequestAndFailIfNot20x(request.build());
            if (body == null) {
                break;
            }
            ConfluenceSearchResult result = ObjectMapperExt.INSTANT.readValue(body, ConfluenceSearchResult.class);
            List<ResultDto> results = Optional.ofNullable(result.getResults()).orElse(List.of());
            results.forEach(r -> refs.add(new PageRef(r.getId(), r.getTitle())));
            if (results.size() < limit) {
                break;
            }
            start += results.size();
        }
        return refs;
    }

    /**
     * Метки страницы ({@code /content/{id}/label}). Best-effort: любая ошибка REST/парсинга → пустой
     * список, чтобы отсутствие меток не роняло экспорт. Имя метки берётся из {@code name} (fallback {@code label}).
     */
    @Override
    public List<String> labels(String id) {
        try {
            HttpUriRequest httpRequest = RequestBuilder.get()
                    .setUri(urlBase + "/content/%s/label".formatted(id))
                    .addParameter("limit", "200")
                    .build();
            String body = doRequestAndFailIfNot20x(httpRequest);
            if (body == null) {
                return List.of();
            }
            List<String> labels = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node : ObjectMapperExt.INSTANT.readTree(body).path("results")) {
                String name = node.path("name").asText(node.path("label").asText(""));
                if (!name.isBlank()) {
                    labels.add(name);
                }
            }
            return labels;
        } catch (Exception e) {
            log.warn("Не удалось получить метки страницы {}: {}", id, e.getMessage());
            return List.of();
        }
    }

    /**
     * Вложения <b>самой</b> страницы ({@code /child/attachment}), а не всего её поддерева.
     * <p>
     * Раньше здесь был {@code /descendant/attachment}: для корневой страницы он отдаёт вложения всего
     * пространства, из-за чего каждый файл скачивался в {@code attache/} каждого своего предка, а
     * пагинация по 25 давала сотни лишних запросов на страницу.
     */
    @SneakyThrows
    public void getAttachments(Map<String, LinkResult> res, String id, int start) {

        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/%s/child/attachment".formatted(id))
                .addParameter("start", String.valueOf(start))
                .addParameter("limit", String.valueOf(ATTACHMENT_PAGE_SIZE))
                .build();

        String body = doRequestAndFailIfNot20x(httpRequest);
        Attachment attachment = ObjectMapperExt.INSTANT.readValue(body, Attachment.class);

        List<ResultDto> resultAttachments = Optional.of(attachment)
                .map(Attachment::getResults)
                .orElse(List.of());

        for (ResultDto resultAttachment : resultAttachments) {
            String downloadUrl = Optional.of(resultAttachment)
                    .map(ResultDto::getLinks)
                    .map(LinksDto::getDownload)
                    .orElse(null);
            res.put(resultAttachment.getTitle(), new LinkResult(resultAttachment.getTitle(), downloadUrl));
        }
        Optional<String> isNext = Optional.of(attachment).map(Attachment::getLinks).map(LinksDto::getNext);
        if (isNext.isPresent()) {
            getAttachments(res, id, attachment.getStart() + attachment.getSize());
        }
    }

    @SneakyThrows
    byte[] downLoad(String link) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(host + link)
                .build();

        return httpClient.execute(httpRequest, httpResponse -> {
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                EntityUtils.consume(httpResponse.getEntity());
                throw new HttpResponseException(statusCode, httpResponse.getStatusLine().getReasonPhrase());
            } else if (statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_PARTIAL_CONTENT) {
                EntityUtils.consume(httpResponse.getEntity());
                log.error("Status code {}", statusCode);
                return null;
            } else {
                return EntityUtils.toByteArray(httpResponse.getEntity());
            }
        });
    }

    @SneakyThrows
    private String doRequestAndFailIfNot20x(HttpUriRequest httpRequest) {
        return httpClient.execute(httpRequest, httpResponse -> {
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                EntityUtils.consume(httpResponse.getEntity());
                throw new HttpResponseException(statusCode, httpResponse.getStatusLine().getReasonPhrase());
            } else if (statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_PARTIAL_CONTENT) {
                EntityUtils.consume(httpResponse.getEntity());
                log.error("Status code {}", statusCode);
                return null;
            } else {
                return EntityUtils.toString(httpResponse.getEntity());
            }
        });
    }

    /**
     * Поиск страниц по заголовку: сначала точное совпадение ({@code title = }), затем нечёткое
     * ({@code title ~ }). Используется и резолюцией внутренних ссылок — поэтому по телу страниц
     * не ищет (иначе ссылка могла бы «зарезолвиться» в произвольную страницу с похожим текстом).
     */
    public List<LinkResult> search(String title, String key) {
        List<LinkResult> exact = searchCql(cqlTitle(title, "=", key));
        return exact.isEmpty() ? searchCql(cqlTitle(title, "~", key)) : exact;
    }

    @Override
    public String baseUrl() {
        return host;
    }

    /**
     * Поиск по заголовку и тексту: каскад {@code title = } → {@code title ~ } → {@code text ~ }.
     * Возвращает первую непустую выдачу (заголовочные совпадения приоритетнее текстовых). Подходит
     * для свободного поиска по содержимому (в отличие от {@link #search(String, String)}). Если
     * {@code key} пуст — поиск **по всем пространствам** (не ограничивается). В результате — ключ
     * пространства каждой найденной страницы.
     */
    public List<PageHit> searchText(String query, String key) {
        List<PageHit> exact = searchPages(cqlTitle(query, "=", key));
        if (!exact.isEmpty()) {
            return exact;
        }
        List<PageHit> fuzzy = searchPages(cqlTitle(query, "~", key));
        return fuzzy.isEmpty() ? searchPages(cqlField("text", query, key)) : fuzzy;
    }

    @SneakyThrows
    private List<PageHit> searchPages(String cql) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/search")
                .addParameter("cql", cql)
                .addParameter("expand", "space")
                .build();
        log.debug("Confluence search: {}", cql);
        String body = doRequestAndFailIfNot20x(httpRequest);
        if (body == null) {
            return List.of();
        }
        List<PageHit> hits = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode r : ObjectMapperExt.INSTANT.readTree(body).path("results")) {
            String webui = r.path("_links").path("webui").asText(null);
            if (webui != null) {
                hits.add(new PageHit(r.path("title").asText(null),
                        r.path("space").path("key").asText(null), host + webui));
            }
        }
        return hits;
    }

    /** Найденная страница: заголовок, ключ пространства и веб-ссылка. */
    public record PageHit(String title, String space, String url) {
    }

    private String cqlTitle(String query, String op, String key) {
        return cqlField("title", op, query, key);
    }

    private String cqlField(String field, String query, String key) {
        return cqlField(field, "~", query, key);
    }

    private String cqlField(String field, String op, String query, String key) {
        String escaped = query.replace("\"", "\\\"");
        String cql = "%s %s \"%s\"".formatted(field, op, escaped);
        if (StringUtils.isNotEmpty(key)) {
            cql = cql + " and space.key = \"%s\"".formatted(key);
        }
        return cql;
    }

    @SneakyThrows
    private List<LinkResult> searchCql(String cql) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/search")
                .addParameter("cql", cql)
                .build();
        log.debug("Confluence search: {}", cql);
        String body = doRequestAndFailIfNot20x(httpRequest);
        if (body == null) {
            return List.of();
        }
        ConfluenceSearchResult searchResult = ObjectMapperExt.INSTANT.readValue(body, ConfluenceSearchResult.class);
        return Optional.ofNullable(searchResult.getResults()).orElse(List.of()).stream()
                .map(r -> Optional.of(r)
                        .map(ResultDto::getLinks)
                        .map(LinksDto::getWebui)
                        .map(ui -> host + ui)
                        .map(url -> new LinkResult(r.getTitle(), url))
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /** Список пространств Confluence ({@code key}, {@code name}) — для подсказки «где искать». */
    @SneakyThrows
    public List<Space> listSpaces(int start, int limit) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/space")
                .addParameter("start", Integer.toString(Math.max(start, 0)))
                .addParameter("limit", Integer.toString(limit))
                .build();
        String body = doRequestAndFailIfNot20x(httpRequest);
        if (body == null) {
            return List.of();
        }
        List<Space> spaces = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode node : ObjectMapperExt.INSTANT.readTree(body).path("results")) {
            String key = node.path("key").asText(null);
            if (key != null) {
                spaces.add(new Space(key, node.path("name").asText(null)));
            }
        }
        return spaces;
    }

    /** Краткое описание пространства Confluence. */
    public record Space(String key, String name) {
    }

    @SneakyThrows
    public LinkResult user(String userKey) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/user")
                .addParameter("key", userKey)
                .build();

        String body = doRequestAndFailIfNot20x(httpRequest);
        if (body == null) {
            return null;
        }
        UserDto userDto = ObjectMapperExt.INSTANT.readValue(body, UserDto.class);
        return new LinkResult(userDto.getDisplayName(), host + "/display/~%s".formatted(userDto.getUsername()));
    }

    /**
     * Скачивает вложения в каталог страницы.
     *
     * <p>Уже лежащий на диске файл <b>перекачивается</b>: сюда попадают только страницы, которые
     * выгружаются заново (неизменившиеся отсекаются раньше, по версии), а значит вложение могло
     * измениться — по имени файла этого не видно. Дублируется только защита от повторного скачивания
     * одного и того же имени в пределах одного вызова (свои вложения + картинки с чужих страниц).
     */
    @SneakyThrows
    public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
        Set<String> loaded = new HashSet<>();
        for (LinkResult value : values) {
            if (!loaded.add(value.title())) {
                log.debug("Вложение {} уже скачано в этом проходе — пропускаем", value.title());
                continue;
            }
            Path file = attachmentFolder.resolve(value.title());
            byte[] bytes = downLoad(value.url());
            if (bytes == null) {
                // нет прав/файл удалён — пропускаем, но экспорт не роняем
                log.warn("Не удалось скачать вложение {} ({})", value.title(), value.url());
                continue;
            }
            Files.write(file, bytes);
            if (progress != null) {
                progress.accept(value.title());
            }
        }
    }
}
