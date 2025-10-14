package io.kestra.plugin.pages;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.pages.Update;
import jakarta.inject.Inject;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.Map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@KestraTest
class UpdateTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        // serverUrl is required -> should throw
        Update task = Update.builder()
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(Property.ofValue(Map.of(
                "number", 2,
                "message", "msg"
            )))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void non2xxResponse_throwsIllegalStateException() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(Property.ofValue(Map.of(
                "number", 2,
                "message", "msg"
            )))
            .build();

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpClient.Builder builderMock = Mockito.mock(HttpClient.Builder.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
                    
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            when(httpResponseMock.statusCode()).thenReturn(500);
            when(httpResponseMock.body()).thenReturn("{\"error\":\"boom\"}");

            when(httpClientMock.send(
                any(HttpRequest.class), 
                eq(HttpResponse.BodyHandlers.ofString()) 
            )).thenReturn(httpResponseMock);

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
        }
    }

    @Test
    void successfulResponse_returnsBody() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(Property.ofValue(Map.of(
                "number", 2,
                "message", "msg"
            )))
            .build();

        String json = "{\"id\":\"123\",\"title\":\"Title\"}";

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);
            when(httpResponseMock.statusCode()).thenReturn(200);
            when(httpResponseMock.body()).thenReturn(json);

            when(httpClientMock.send(
                any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofString()) 
            )).thenReturn(httpResponseMock);

            Update.Output output = task.run(runContext);

            assertThat(output, is(notNullValue()));
            assertThat(output.getValue(), is(notNullValue()));
            assertThat(output.getValue(), containsString("\"id\":\"123\""));
        }
    }
}