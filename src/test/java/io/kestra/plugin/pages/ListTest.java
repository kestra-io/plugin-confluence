package io.kestra.plugin.pages;

import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.confluence.pages.List;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class ListTest extends WireMockTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());
        // username and apiToken provided but serverUrl missing
        List task = List.builder()
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageIds(Property.ofValue(java.util.List.of(1L, 2L)))
            .spaceIds(Property.ofValue(java.util.List.of(1L)))
            .limit(Property.ofValue(10))
            .build();
        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void non2xxResponse_throwsIllegalStateException() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = builder()
            .pageIds(Property.ofValue(java.util.List.of(1L, 2L)))
            .spaceIds(Property.ofValue(java.util.List.of(1L)))
            .limit(Property.ofValue(10))
            .build();

        stubPage(PAGES_PATH, 500, "{\"error\":\"boom\"}");

        var e = assertThrows(IllegalStateException.class, () -> task.run(runContext));

        assertThat(e.getMessage(), is("Confluence API returned 500"));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PAGES_PATH)));
    }

    @Test
    void successfulResponse_parsesMarkdown() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = builder()
            .pageIds(Property.ofValue(java.util.List.of(1L, 2L)))
            .spaceIds(Property.ofValue(java.util.List.of(1L)))
            .limit(Property.ofValue(10))
            .build();

        var url = "%s?space-id=1&id=1%%2C2&status=current%%2Carchived&limit=10&body-format=storage".formatted(PAGES_PATH);
        String json = "{ \"results\": [ { \"title\": \"Test Page\", \"body\": { \"storage\": { \"value\": \"<h1>Hello World</h1><p>This is a paragraph.</p>\" } } } ] }";
        stubPage(url, 200, json);

        var output = task.run(runContext);
        assertThat(output, is(notNullValue()));
        assertThat(output.getChildren(), hasSize(1));

        var child = output.getChildren().getFirst();
        assertThat(child.getTitle(), is("Test Page"));
        assertThat(child.getMarkdown(), containsString("Hello World"));
        assertThat(child.getMarkdown(), containsString("This is a paragraph."));
        wireMock.verify(
            1,
            getRequestedFor(urlEqualTo(url))
                .withHeader("Authorization", equalTo(AUTH))
                .withHeader("Accept", equalTo("application/json"))
        );
    }

    @Test
    void successfulResponse_includesVersionInfo() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .limit(Property.ofValue(10))
            .build();

        String json = """
            {
              "results": [
                {
                  "title": "Test Page",
                  "version": {
                    "number": 19,
                    "message": "",
                    "minorEdit": false,
                    "authorId": "abc",
                    "createdAt": "2026-01-21T05:58:44.698Z"
                  },
                  "body": {
                    "storage": {
                      "value": "<h1>Hello</h1>"
                    }
                  }
                }
              ]
            }
            """;
        stubPage(PAGES_PATH, 200, json);

        var output = task.run(runContext);

        assertThat(output.getChildren(), hasSize(1));
        var child = output.getChildren().getFirst();

        assertThat(child.getVersionInfo(), is(notNullValue()));
        assertThat(child.getVersionInfo().get("number"), is(19));

        assertThat(child.getRawResponse(), is(notNullValue()));
        assertThat(child.getRawResponse().get("version"), is(notNullValue()));
    }

    @Test
    void store_followsNextCursorUntilLastPage() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, null);

        stubPage(FIRST_PAGE_URL, 200, page("Page 1", "/wiki/api/v2/pages?space-id=123456&cursor=abc%3D%3D123&limit=1"));
        stubPage("%s&cursor=abc%%3D%%3D123".formatted(FIRST_PAGE_URL), 200, page("Page 2", null));

        var output = task.run(runContext);

        wireMock.verify(2, getRequestedFor(urlPathEqualTo(PAGES_PATH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo(FIRST_PAGE_URL)).withHeader("Authorization", equalTo(AUTH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo("%s&cursor=abc%%3D%%3D123".formatted(FIRST_PAGE_URL))).withHeader("Authorization", equalTo(AUTH)));
        assertThat(storedTitles(runContext, output.getUri()), contains("Page 1", "Page 2"));
        assertThat(output.getNextCursor(), is(nullValue()));
    }

    @Test
    void store_startsFromGivenCursor() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, "start1");

        stubPage("%s&cursor=start1".formatted(FIRST_PAGE_URL), 200, page("Page 2", null));

        var output = task.run(runContext);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PAGES_PATH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo("%s&cursor=start1".formatted(FIRST_PAGE_URL))));
        assertThat(storedTitles(runContext, output.getUri()), contains("Page 2"));
    }

    @Test
    void store_failsWhenALaterPageFails() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, null);

        stubPage(FIRST_PAGE_URL, 200, page("Page 1", "/wiki/api/v2/pages?cursor=abc123"));
        stubPage("%s&cursor=abc123".formatted(FIRST_PAGE_URL), 429, "{\"message\":\"rate limited\"}");

        var e = assertThrows(IllegalStateException.class, () -> task.run(runContext));

        assertThat(e.getMessage(), containsString("429"));
        // the core client retries a GET answered with 429 before giving up
        wireMock.verify(moreThanOrExactly(2), getRequestedFor(urlPathEqualTo(PAGES_PATH)));
        // an unclosed file cannot be deleted on Windows
        try (var files = Files.walk(runContext.workingDir().path())) {
            var ionFiles = files.filter(p -> p.toString().endsWith(".ion")).toList();
            assertThat(ionFiles, hasSize(1));
            Files.delete(ionFiles.getFirst());
        }
    }

    @Test
    void store_stopsWhenCursorRepeats() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, null);

        // any request answers with the same cursor, the guard must stop the loop after the second one
        stubPage(PAGES_PATH, 200, page("Page 1", "/wiki/api/v2/pages?cursor=same"));

        var e = assertThrows(IllegalStateException.class, () -> task.run(runContext));

        assertThat(e.getMessage(), containsString("'same' twice"));
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(PAGES_PATH)));
    }

    @Test
    void fetch_returnsNextCursorWithoutFollowingIt() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.FETCH, null);

        stubPage(FIRST_PAGE_URL, 200, page("Page 1", "/wiki/api/v2/pages?cursor=abc123&limit=1"));

        var output = task.run(runContext);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PAGES_PATH)));
        assertThat(output.getChildren(), hasSize(1));
        assertThat(output.getNextCursor(), is("abc123"));
    }

    @Test
    void spaceIds_filtersSingleSpace() throws Exception {
        var task = builder()
            .spaceIds(Property.ofValue(java.util.List.of(294916L)))
            .build();
        stubSpacePages();

        var output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getChildren(), hasSize(2));
        assertThat(output.getChildren().stream().map(child -> child.getRawResponse().get("spaceId")).toList(), contains("294916", "294916"));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PAGES_PATH)).withQueryParam("space-id", equalTo("294916")));
    }

    @Test
    void spaceIds_filtersMultipleRenderedSpaces() throws Exception {
        var task = builder()
            .spaceIds(Property.ofExpression("{{ spaces }}"))
            .build();
        stubSpacePages();
        wireMock.stubFor(
            get(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("space-id", equalTo("294916,654321"))
                .atPriority(1)
                .willReturn(aResponse().withBody(spacePages("294916", "654321")))
        );

        var output = task.run(runContextFactory.of(Map.of("spaces", java.util.List.of(294916, 654321))));

        assertThat(output.getChildren(), hasSize(2));
        assertThat(output.getChildren().stream().map(child -> child.getRawResponse().get("spaceId")).toList(), contains("294916", "654321"));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PAGES_PATH)).withQueryParam("space-id", equalTo("294916,654321")));
    }

    @Test
    void pageIds_filtersWithoutSpaces() throws Exception {
        var task = builder()
            .pageIds(Property.ofValue(java.util.List.of(10L, 20L)))
            .build();
        wireMock.stubFor(
            get(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("id", equalTo("10,20"))
                .withQueryParam("space-id", absent())
                .willReturn(aResponse().withBody(spacePages("294916", "654321")))
        );

        var output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getChildren(), hasSize(2));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PAGES_PATH)).withQueryParam("id", equalTo("10,20")));
    }

    @Test
    void omittedFilters_returnsPagesFromEverySpace() throws Exception {
        var task = builder()
            .build();
        stubSpacePages();

        var output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getChildren().stream().map(child -> child.getRawResponse().get("spaceId")).toList(), contains("294916", "294916", "654321"));
        wireMock.verify(
            1,
            getRequestedFor(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("space-id", absent())
                .withQueryParam("id", absent())
                .withQueryParam("space-ids", absent())
                .withQueryParam("page-ids", absent())
        );
    }

    @Test
    void ids_supportInt64ValuesFromExpressions() throws Exception {
        var task = builder()
            .spaceIds(Property.ofExpression("{{ spaces }}"))
            .pageIds(Property.ofExpression("{{ pages }}"))
            .build();
        wireMock.stubFor(
            get(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("space-id", equalTo("4294967296,4294967297"))
                .withQueryParam("id", equalTo("8589934592,8589934593"))
                .willReturn(aResponse().withBody(spacePages("4294967296", "4294967297")))
        );

        var output = task.run(
            runContextFactory.of(
                Map.of(
                    "spaces", java.util.List.of(4294967296L, 4294967297L),
                    "pages", java.util.List.of(8589934592L, 8589934593L)
                )
            )
        );

        assertThat(output.getChildren().stream().map(child -> child.getRawResponse().get("spaceId")).toList(), contains("4294967296", "4294967297"));
        wireMock.verify(
            1, getRequestedFor(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("space-id", equalTo("4294967296,4294967297"))
                .withQueryParam("id", equalTo("8589934592,8589934593"))
        );
    }

    @Test
    void ids_supportSmallAndInt64ValuesFromUncachedLists() throws Exception {
        var task = builder()
            .spaceIds(Property.ofValue(java.util.List.of(294916L, 4294967296L)).skipCache())
            .pageIds(Property.ofValue(java.util.List.of(294916L, 4294967296L)).skipCache())
            .build();
        wireMock.stubFor(
            get(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("space-id", equalTo("294916,4294967296"))
                .withQueryParam("id", equalTo("294916,4294967296"))
                .willReturn(aResponse().withBody(spacePages("294916", "4294967296")))
        );

        var output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getChildren().stream().map(child -> child.getRawResponse().get("spaceId")).toList(), contains("294916", "4294967296"));
        wireMock.verify(
            1, getRequestedFor(urlPathEqualTo(PAGES_PATH))
                .withQueryParam("space-id", equalTo("294916,4294967296"))
                .withQueryParam("id", equalTo("294916,4294967296"))
        );
    }

    // Like Confluence, ignore unknown query parameters and return every space unless the supported filter is present.
    private void stubSpacePages() {
        wireMock.stubFor(
            get(urlPathEqualTo(PAGES_PATH)).atPriority(10)
                .willReturn(aResponse().withBody(spacePages("294916", "294916", "654321")))
        );
        wireMock.stubFor(
            get(urlPathEqualTo(PAGES_PATH)).withQueryParam("space-id", equalTo("294916")).atPriority(1)
                .willReturn(aResponse().withBody(spacePages("294916", "294916")))
        );
    }

    private static String spacePages(String... spaces) {
        return "{\"results\":[%s]}".formatted(
            Arrays.stream(spaces)
                .map(space -> "{\"spaceId\":\"%s\",\"title\":\"Page in %s\",\"body\":{\"storage\":{\"value\":\"<p>Content</p>\"}}}".formatted(space, space))
                .collect(Collectors.joining(","))
        );
    }

    private static final String PAGES_PATH = "/wiki/api/v2/pages";
    private static final String FIRST_PAGE_URL = "/wiki/api/v2/pages?space-id=123456&status=current%2Carchived&limit=1&body-format=storage";

    // urlEqualTo matches the raw url (path and query), a bare path matches any query
    private void stubPage(String url, int status, String body) {
        var mapping = url.contains("?") ? get(urlEqualTo(url)) : get(urlPathEqualTo(url));
        wireMock.stubFor(mapping.willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private List.ListBuilder<?, ?> builder() {
        return List.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"));
    }

    private List task(FetchType fetchType, String cursor) {
        var builder = builder()
            .spaceIds(Property.ofValue(java.util.List.of(123456L)))
            .limit(Property.ofValue(1))
            .fetchType(Property.ofValue(fetchType));
        if (cursor != null) {
            builder.cursor(Property.ofValue(cursor));
        }
        return builder.build();
    }

    private static String page(String title, String next) {
        var links = next == null ? "{}" : "{ \"next\": \"%s\" }".formatted(next);
        return """
            { "results": [ { "title": "%s", "body": { "storage": { "value": "<p>%s</p>" } } } ], "_links": %s }
            """.formatted(title, title, links);
    }

    private static java.util.List<String> storedTitles(RunContext runContext, URI uri) throws Exception {
        try (var input = runContext.storage().getFile(uri)) {
            return FileSerde.readAll(input)
                .map(row -> (String) ((Map<?, ?>) row).get("title"))
                .collectList()
                .block();
        }
    }
}
