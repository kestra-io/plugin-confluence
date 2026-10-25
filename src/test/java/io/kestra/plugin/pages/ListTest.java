package io.kestra.plugin.pages;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.confluence.pages.List;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class ListTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());
        // username and apiToken provided but serverUrl missing
        List task = List.builder()
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageIds(Property.ofValue(java.util.List.of(1, 2)))
            .spaceIds(Property.ofValue(java.util.List.of(1)))
            .limit(Property.ofValue(10))
            .build();
        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void non2xxResponse_throwsIllegalStateException() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageIds(Property.ofValue(java.util.List.of(1, 2)))
            .spaceIds(Property.ofValue(java.util.List.of(1)))
            .limit(Property.ofValue(10))
            .build();

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {

            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            when(httpResponseMock.statusCode()).thenReturn(500);
            when(httpResponseMock.body()).thenReturn("{\"error\":\"boom\"}");
            Mockito.doReturn(httpResponseMock)
                .when(httpClientMock)
                .send(
                    any(HttpRequest.class),
                    any()
                );

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
        }
    }

    @Test
    void successfulResponse_parsesMarkdown() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageIds(Property.ofValue(java.util.List.of(1, 2)))
            .spaceIds(Property.ofValue(java.util.List.of(1)))
            .limit(Property.ofValue(10))
            .build();

        String json = "{ \"results\": [ { \"title\": \"Test Page\", \"body\": { \"storage\": { \"value\": \"<h1>Hello World</h1><p>This is a paragraph.</p>\" } } } ] }";
        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {

            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);

            when(httpResponseMock.statusCode()).thenReturn(200);

            when(httpResponseMock.body()).thenReturn(json);

            Mockito.doReturn(httpResponseMock)
                .when(httpClientMock)
                .send(
                    any(HttpRequest.class),
                    any()
                );

            List.Output output = task.run(runContext);
            assertThat(output, is(notNullValue()));
            assertThat(output.getChildren(), hasSize(1));

            List.OutputChild child = output.getChildren().get(0);
            assertThat(child.getTitle(), is("Test Page"));
            assertThat(child.getMarkdown(), containsString("Hello World"));
            assertThat(child.getMarkdown(), containsString("This is a paragraph."));
        }
    }

    @Test
    void successfulResponse_includesVersionInfo() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
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

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);

            when(httpResponseMock.statusCode()).thenReturn(200);
            when(httpResponseMock.body()).thenReturn(json);

            Mockito.doReturn(httpResponseMock)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            assertThat(output.getChildren(), hasSize(1));
            List.OutputChild child = output.getChildren().get(0);

            assertThat(child.getVersionInfo(), is(notNullValue()));
            assertThat(child.getVersionInfo().get("number"), is(19));

            assertThat(child.getRawResponse(), is(notNullValue()));
            assertThat(child.getRawResponse().get("version"), is(notNullValue()));
        }
    }

    @Test
    void store_followsNextCursorUntilLastPage() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, null);

        try (var httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            var client = mockClient(
                httpClientStatic,
                response(200, page("Page 1", "/wiki/api/v2/pages?space-ids=123456&cursor=abc%3D%3D123&limit=1")),
                response(200, page("Page 2", null))
            );

            var output = task.run(runContext);

            var requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(client, Mockito.times(2)).send(requests.capture(), any());
            assertThat(requests.getAllValues().getFirst().uri().getRawQuery(), not(containsString("cursor")));
            assertThat(requests.getAllValues().getLast().uri().getRawQuery(), containsString("cursor=abc%3D%3D123"));
            assertThat(requests.getAllValues().getLast().uri().getRawQuery(), containsString("space-ids=123456"));
            assertThat(storedTitles(runContext, output.getUri()), contains("Page 1", "Page 2"));
            assertThat(output.getNextCursor(), is(nullValue()));
        }
    }

    @Test
    void store_startsFromGivenCursor() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, "start1");

        try (var httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            var client = mockClient(httpClientStatic, response(200, page("Page 2", null)));

            var output = task.run(runContext);

            var requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(client, Mockito.times(1)).send(requests.capture(), any());
            assertThat(requests.getValue().uri().getQuery(), containsString("cursor=start1"));
            assertThat(storedTitles(runContext, output.getUri()), contains("Page 2"));
        }
    }

    @Test
    void store_failsWhenALaterPageFails() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, null);

        try (var httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            var client = mockClient(
                httpClientStatic,
                response(200, page("Page 1", "/wiki/api/v2/pages?cursor=abc123")),
                response(429, "{\"message\":\"rate limited\"}")
            );

            var e = assertThrows(IllegalStateException.class, () -> task.run(runContext));

            assertThat(e.getMessage(), containsString("429"));
            Mockito.verify(client, Mockito.times(2)).send(any(HttpRequest.class), any());
            // an unclosed file cannot be deleted on Windows
            try (var files = Files.walk(runContext.workingDir().path())) {
                var ionFiles = files.filter(p -> p.toString().endsWith(".ion")).toList();
                assertThat(ionFiles, hasSize(1));
                Files.delete(ionFiles.getFirst());
            }
        }
    }

    @Test
    void store_stopsWhenCursorRepeats() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.STORE, null);
        var loopingPage = response(200, page("Page 1", "/wiki/api/v2/pages?cursor=same"));

        try (var httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            var client = mockClient(httpClientStatic, loopingPage, loopingPage, loopingPage);

            var e = assertThrows(IllegalStateException.class, () -> task.run(runContext));

            assertThat(e.getMessage(), containsString("'same' twice"));
            Mockito.verify(client, Mockito.times(2)).send(any(HttpRequest.class), any());
        }
    }

    @Test
    void fetch_returnsNextCursorWithoutFollowingIt() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var task = task(FetchType.FETCH, null);

        try (var httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            var client = mockClient(httpClientStatic, response(200, page("Page 1", "/wiki/api/v2/pages?cursor=abc123&limit=1")));

            var output = task.run(runContext);

            Mockito.verify(client, Mockito.times(1)).send(any(HttpRequest.class), any());
            assertThat(output.getChildren(), hasSize(1));
            assertThat(output.getNextCursor(), is("abc123"));
        }
    }

    private static List task(FetchType fetchType, String cursor) {
        var builder = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceIds(Property.ofValue(java.util.List.of(123456)))
            .limit(Property.ofValue(1))
            .fetchType(Property.ofValue(fetchType));
        if (cursor != null) {
            builder.cursor(Property.ofValue(cursor));
        }
        return builder.build();
    }

    // answers each request with the next response and fails on any extra request, so a pagination loop cannot hang the test
    private static HttpClient mockClient(MockedStatic<HttpClient> httpClientStatic, HttpResponse<?>... responses) throws Exception {
        var client = Mockito.mock(HttpClient.class);
        httpClientStatic.when(HttpClient::newHttpClient).thenReturn(client);
        var calls = new AtomicInteger();
        Mockito.doAnswer(invocation ->
        {
            var call = calls.getAndIncrement();
            if (call >= responses.length) {
                throw new AssertionError("Unexpected request %s".formatted(call + 1));
            }
            return responses[call];
        })
            .when(client)
            .send(any(HttpRequest.class), any());
        return client;
    }

    private static String page(String title, String next) {
        var links = next == null ? "{}" : "{ \"next\": \"%s\" }".formatted(next);
        return """
            { "results": [ { "title": "%s", "body": { "storage": { "value": "<p>%s</p>" } } } ], "_links": %s }
            """.formatted(title, title, links);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        var response = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
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
