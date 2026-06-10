package ru.gitverse.adoct.client;

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
import ru.gitverse.adoct.client.content.Attachment;
import ru.gitverse.adoct.client.content.Body;
import ru.gitverse.adoct.client.content.ContentMainPage;
import ru.gitverse.adoct.client.content.LinksDto;
import ru.gitverse.adoct.client.content.ResultDto;
import ru.gitverse.adoct.client.content.Storage;
import ru.gitverse.adoct.client.content.Version;
import ru.gitverse.adoct.client.content.View;
import ru.gitverse.adoct.client.searche.ConfluenceSearchResult;
import ru.gitverse.adoct.client.user.UserDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class ConfluenceClient {
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

    public Map<String, LinkResult> getAttachments(String id) {
        Map<String, LinkResult> res = new HashMap<>();
        getAttachments(res, id, 0);
        return res;
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

    @SneakyThrows
    public List<LinkResult> search(String title, String key) {
        List<LinkResult> search = search(title, key, false);
        if (search.isEmpty()) {
            return search(title, key, true);
        } else {
            return search;
        }
    }

    @SneakyThrows
    private List<LinkResult> search(String title, String key, boolean isLike) {
        String searchTitle = title.replace("\"", "\\\"");
        String cql = "title %s \"%s\"".formatted(isLike ? "~" : "=", searchTitle);
        if (StringUtils.isNotEmpty(key)) {
            cql = cql + " and space.key = \"%s\"".formatted(key);
        }
        HttpUriRequest httpRequest = RequestBuilder.get()
                .setUri(urlBase + "/content/search")
                .addParameter("cql", cql)
                //    .addParameter("expand", "version")
                .build();
        System.out.println("Searche title " + httpRequest);
        String body = doRequestAndFailIfNot20x(httpRequest);
        System.out.println(body);
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
