package io.kestra.plugin.pages;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceIds(Property.ofValue(java.util.List.of(123456)))
            .limit(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String firstPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "next": "/wiki/api/v2/pages?space-ids=123456&cursor=abc%3D%3D123&limit=1" }
            }
            """;
        String lastPage = """
            {
              "results": [ { "title": "Page 2", "body": { "storage": { "value": "<p>two</p>" } } } ],
              "_links": {}
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> firstResponse = mockResponse(firstPage, null);
        HttpResponse<String> lastResponse = mockResponse(lastPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(firstResponse, lastResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(httpClientMock, Mockito.times(2)).send(requests.capture(), any());
            assertThat(requests.getAllValues().get(0).uri().getQuery(), not(containsString("cursor")));
            assertThat(requests.getAllValues().get(1).uri().getQuery(), containsString("cursor=abc==123"));
            assertThat(requests.getAllValues().get(1).uri().getQuery(), containsString("space-ids=123456"));

            assertThat(storedTitles(runContext, output.getUri()), contains("Page 1", "Page 2"));
            assertThat(output.getNextCursor(), is(nullValue()));
        }
    }

    @Test
    void store_followsLinkHeaderCursor() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String firstPage = """
            { "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ] }
            """;
        String lastPage = """
            { "results": [ { "title": "Page 2", "body": { "storage": { "value": "<p>two</p>" } } } ] }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> firstResponse = mockResponse(firstPage, "</wiki/api/v2/pages?cursor=xyz789&limit=25>; rel=\"next\", </wiki>; rel=\"base\"");
        HttpResponse<String> lastResponse = mockResponse(lastPage, "</wiki>; rel=\"base\"");

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(firstResponse, lastResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(httpClientMock, Mockito.times(2)).send(requests.capture(), any());
            assertThat(requests.getAllValues().get(1).uri().getQuery(), containsString("cursor=xyz789"));

            assertThat(storedTitles(runContext, output.getUri()), contains("Page 1", "Page 2"));
        }
    }

    @Test
    void fetch_returnsNextCursorWithoutFollowingIt() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .build();

        String firstPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "next": "/wiki/api/v2/pages?cursor=abc123&limit=25" }
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> firstResponse = mockResponse(firstPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(firstResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            Mockito.verify(httpClientMock, Mockito.times(1)).send(any(HttpRequest.class), any());
            assertThat(output.getChildren(), hasSize(1));
            assertThat(output.getNextCursor(), is("abc123"));
        }
    }

    @Test
    void store_followsCursorFromComplexLinkHeader() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String firstPage = """
            { "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ] }
            """;
        String lastPage = """
            { "results": [ { "title": "Page 2", "body": { "storage": { "value": "<p>two</p>" } } } ] }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        // next is not the first link, carries extra params, a multi-value rel, and a cursor with a literal '+'
        HttpResponse<String> firstResponse = mockResponse(
            firstPage,
            "</wiki/api/v2/pages?cursor=old>; rel=\"prev\", </wiki/api/v2/pages?limit=25&cursor=ab+c%2Fd>; title=\"more\"; REL=\"first next\""
        );
        HttpResponse<String> lastResponse = mockResponse(lastPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(firstResponse, lastResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(httpClientMock, Mockito.times(2)).send(requests.capture(), any());
            assertThat(requests.getAllValues().get(1).uri().getRawQuery(), containsString("cursor=ab%2Bc%2Fd"));

            assertThat(storedTitles(runContext, output.getUri()), contains("Page 1", "Page 2"));
        }
    }

    @Test
    void store_startsFromGivenCursor() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .cursor(Property.ofValue("start1"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String lastPage = """
            { "results": [ { "title": "Page 2", "body": { "storage": { "value": "<p>two</p>" } } } ], "_links": {} }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> lastResponse = mockResponse(lastPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(lastResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(httpClientMock, Mockito.times(1)).send(requests.capture(), any());
            assertThat(requests.getValue().uri().getQuery(), containsString("cursor=start1"));
            assertThat(storedTitles(runContext, output.getUri()), contains("Page 2"));
        }
    }

    @Test
    void store_failsWhenALaterPageFails() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String firstPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "next": "/wiki/api/v2/pages?cursor=abc123" }
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> firstResponse = mockResponse(firstPage, null);
        HttpResponse<String> errorResponse = mockResponse(429, "{\"message\":\"rate limited\"}", null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(firstResponse, errorResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> task.run(runContext));
            assertThat(e.getMessage(), containsString("429"));
            Mockito.verify(httpClientMock, Mockito.times(2)).send(any(HttpRequest.class), any());

            // the temp Ion file must have been closed (an open file cannot be deleted on Windows)
            try (Stream<Path> files = Files.walk(runContext.workingDir().path())) {
                java.util.List<Path> ionFiles = files.filter(p -> p.toString().endsWith(".ion")).toList();
                assertThat(ionFiles, hasSize(1));
                Files.delete(ionFiles.getFirst());
            }
        }
    }

    @Test
    void store_stopsWhenCursorRepeats() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String loopingPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "next": "/wiki/api/v2/pages?cursor=same" }
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> loopingResponse = mockResponse(loopingPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            // bounded so a regression fails the test instead of looping forever
            AtomicInteger calls = new AtomicInteger();
            Mockito.doAnswer(invocation ->
            {
                if (calls.incrementAndGet() > 5) {
                    throw new AssertionError("List kept requesting the same cursor");
                }
                return loopingResponse;
            })
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> task.run(runContext));
            assertThat(e.getMessage(), containsString("'same' twice"));
            Mockito.verify(httpClientMock, Mockito.times(2)).send(any(HttpRequest.class), any());
        }
    }

    @Test
    void store_stopsWhenResponseRepeatsGivenCursor() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .cursor(Property.ofValue("same"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        String loopingPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "next": "/wiki/api/v2/pages?cursor=same" }
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> loopingResponse = mockResponse(loopingPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            AtomicInteger calls = new AtomicInteger();
            Mockito.doAnswer(invocation ->
            {
                if (calls.incrementAndGet() > 5) {
                    throw new AssertionError("List kept requesting the same cursor");
                }
                return loopingResponse;
            })
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
            Mockito.verify(httpClientMock, Mockito.times(1)).send(any(HttpRequest.class), any());
        }
    }

    @Test
    void fetchOne_returnsNextCursorWithoutFollowingIt() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        String firstPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "next": "/wiki/api/v2/pages?cursor=abc123&limit=1" }
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> firstResponse = mockResponse(firstPage, null);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(firstResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.verify(httpClientMock, Mockito.times(1)).send(requests.capture(), any());
            assertThat(requests.getValue().uri().getQuery(), containsString("limit=1"));
            assertThat(output.getChildren(), hasSize(1));
            assertThat(output.getNextCursor(), is("abc123"));
        }
    }

    @Test
    void fetch_lastPageHasNoNextCursor() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .build();

        String lastPage = """
            {
              "results": [ { "title": "Page 1", "body": { "storage": { "value": "<p>one</p>" } } } ],
              "_links": { "base": "https://example.com/wiki" }
            }
            """;

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> lastResponse = mockResponse(lastPage, "</wiki>; rel=\"base\"");

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            Mockito.doReturn(lastResponse)
                .when(httpClientMock)
                .send(any(HttpRequest.class), any());

            List.Output output = task.run(runContext);

            assertThat(output.getChildren(), hasSize(1));
            assertThat(output.getNextCursor(), is(nullValue()));
        }
    }

    private static HttpResponse<String> mockResponse(String body, String linkHeader) {
        return mockResponse(200, body, linkHeader);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int status, String body, String linkHeader) {
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        Map<String, java.util.List<String>> headers = linkHeader == null ? Map.of() : Map.of("Link", java.util.List.of(linkHeader));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }

    private static java.util.List<String> storedTitles(RunContext runContext, URI uri) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(uri)))) {
            return FileSerde.readAll(reader)
                .map(row -> (String) ((Map<?, ?>) row).get("title"))
                .collectList()
                .block();
        }
    }
}
