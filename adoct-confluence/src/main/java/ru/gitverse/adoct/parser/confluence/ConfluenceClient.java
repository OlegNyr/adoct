package ru.gitverse.adoct.parser.confluence;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import ru.gitverse.adoct.parser.confluence.content.Attachment;
import ru.gitverse.adoct.parser.confluence.content.Body;
import ru.gitverse.adoct.parser.confluence.content.ContentMainPage;
import ru.gitverse.adoct.parser.confluence.content.LinksDto;
import ru.gitverse.adoct.parser.confluence.content.ResultDto;
import ru.gitverse.adoct.parser.confluence.content.Storage;
import ru.gitverse.adoct.parser.confluence.content.Version;
import ru.gitverse.adoct.parser.confluence.content.View;
import ru.gitverse.adoct.parser.confluence.searche.ConfluenceSearchResult;
import ru.gitverse.adoct.parser.confluence.user.UserDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class ConfluenceClient implements ConfluenceGateway {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final HttpClientBuilder httpClientBuilder;
    private final String host;
    private String urlBase;

    public ConfluenceClient(String host, String token) {
        httpClientBuilder = HttpClients.custom()
                .setSSLContext(ConfluenceSsl.newInstance(ConfluenceSsl.NOT_VERIFY_SSL))
                .setSSLHostnameVerifier(ConfluenceSsl.NOT_VERIFY_HOST)
                .setDefaultHeaders(List.of(new BasicHeader(AUTHORIZATION_HEADER, "Bearer " + token)));

        this.host = host;
        this.urlBase = this.host + "/rest/api";
    }

    @SneakyThrows
    public int verifyToken() {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/user/current")
                .build();

        try (var client = httpClientBuilder.build()) {
            return client.execute(httpRequest, httpResponse -> {
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

    }

    @SneakyThrows
    public ContentPage getMainPage(String id) {
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/%s".formatted(id))
                .addParameter("expand", "version,body.storage,body.view")
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

        Map<String, LinkResult> attachment = getAttachments(id);

        return new ContentPage(contentMainPage.getTitle(), host + contentMainPage.getLinks().getWebui(),
                date, content, view, attachment);
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
        List<String> ids = new ArrayList<>();
        int start = 0;
        int limit = 100;
        while (true) {
            HttpUriRequest httpRequest = RequestBuilder.get()
                    .setUri(urlBase + "/content/%s/child/page".formatted(id))
                    .addParameter("start", String.valueOf(start))
                    .addParameter("limit", String.valueOf(limit))
                    .build();
            String body = doRequestAndFailIfNot20x(httpRequest);
            if (body == null) {
                break;
            }
            ConfluenceSearchResult result = ObjectMapperExt.INSTANT.readValue(body, ConfluenceSearchResult.class);
            List<ResultDto> results = Optional.ofNullable(result.getResults()).orElse(List.of());
            results.forEach(r -> ids.add(r.getId()));
            if (results.size() < limit) {
                break;
            }
            start += results.size();
        }
        return ids;
    }

    @SneakyThrows
    public void getAttachments(Map<String, LinkResult> res, String id, int start) {

        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/%s/descendant/attachment".formatted(id))
                .addParameter("start", String.valueOf(start))
                //.addParameter("expand", "version,body.storage,body.view,descendants.attachment")
                .build();
        //?limit=25&start=25

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

        try (var client = httpClientBuilder.build()) {
            return client.execute(httpRequest, httpResponse -> {
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
    }

    @SneakyThrows
    private String doRequestAndFailIfNot20x(HttpUriRequest httpRequest) {
        try (var client = httpClientBuilder.build()) {
            return client.execute(httpRequest, httpResponse -> {
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
        ConfluenceSearchResult searchResult = ObjectMapperExt.INSTANT.readValue(body, ConfluenceSearchResult.class);
        return searchResult.getResults().stream()
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

    @SneakyThrows
    public void loadAttach(Collection<LinkResult> values, Path attachmentFolder, Consumer<String> progress) {
        for (LinkResult value : values) {
            Path file = attachmentFolder.resolve(value.title());
            if (Files.exists(file)) {
                log.warn("Ignore load {} file exist", file);
            }
            byte[] bytes = downLoad(value.url());
            Files.write(file, bytes);
            if (progress != null) {
                progress.accept(value.title());
            }
        }
    }
}
