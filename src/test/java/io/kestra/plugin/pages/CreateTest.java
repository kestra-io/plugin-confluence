package io.kestra.plugin.pages;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.confluence.pages.Create;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@KestraTest
class CreateTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        // serverUrl is required -> should throw
        Create task = Create.builder()
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("SPACEKEY"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void non2xxResponse_throwsIllegalStateException() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Create task = Create.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("SPACEKEY"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .build();

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {

            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            when(httpResponseMock.statusCode()).thenReturn(500);
            when(httpResponseMock.body()).thenReturn("{\"error\":\"boom\"}");

            when(
                httpClientMock.send(
                    any(HttpRequest.class),
                    eq(HttpResponse.BodyHandlers.ofString())
                )
            ).thenReturn(httpResponseMock);

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
        }
    }

    @Test
    void successfulResponse_returnsBody() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Create task = Create.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("SPACEKEY"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .build();

        String json = "{\"id\":\"123\",\"title\":\"Title\"}";

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {

            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            when(httpResponseMock.statusCode()).thenReturn(201);
            when(httpResponseMock.body()).thenReturn(json);

            when(
                httpClientMock.send(
                    any(HttpRequest.class),
                    eq(HttpResponse.BodyHandlers.ofString())
                )
            ).thenReturn(httpResponseMock);

            Create.Output output = task.run(runContext);
            assertThat(output, is(notNullValue()));
            assertThat(output.getValue(), is(notNullValue()));
            assertThat(output.getValue(), containsString("\"id\":\"123\""));
        }
    }

    @Test
    void shouldSendEmbeddedPrivateAndRootLevelOnTheCreateUrl() throws Exception {
        var runContext = runContextFactory.of(Map.of());

        var task = Create.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("123456"))
            .title(Property.ofValue("My New Page from Kestra"))
            .markdown(Property.ofValue("# Hello"))
            .embedded(Property.ofValue(true))
            .makePrivate(Property.ofValue(true))
            .rootLevel(Property.ofValue(true))
            .build();

        var httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (var httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            when(httpResponseMock.statusCode()).thenReturn(201);
            when(httpResponseMock.body()).thenReturn("{\"id\":\"123\"}");
            when(httpClientMock.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    assertThat(request.method(), is("POST"));
                    assertThat(request.uri().getPath(), is("/wiki/api/v2/pages"));
                    assertThat(request.uri().getQuery(), is("embedded=true&private=true&root-level=true"));
                    return httpResponseMock;
                });

            var output = task.run(runContext);
            assertThat(output.getValue(), containsString("\"id\":\"123\""));
        }
    }
}
