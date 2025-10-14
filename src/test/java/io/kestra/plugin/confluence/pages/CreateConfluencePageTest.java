package io.kestra.plugin.confluence.pages;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import kong.unirest.RequestBodyEntity;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@KestraTest
class CreateConfluencePageTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        // serverUrl is required -> should throw
        CreateConfluencePage task = CreateConfluencePage.builder()
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

        CreateConfluencePage task = CreateConfluencePage.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("SPACEKEY"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .build();

        HttpRequestWithBody requestWithBodyMock = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity requestBodyEntityMock = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<JsonNode> responseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<Unirest> unirest = Mockito.mockStatic(Unirest.class)) {
            // allow Unirest.config() to behave normally (setObjectMapper is called in task)
            unirest.when(Unirest::config).thenCallRealMethod();

            unirest.when(() -> Unirest.post(argThat(url ->
                url.startsWith("https://example.com/wiki/api/v2/pages")
            ))).thenReturn(requestWithBodyMock);

            Mockito.when(requestWithBodyMock.queryString(Mockito.any(Map.class))).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.basicAuth("user@example.com", "token")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Accept", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Content-Type", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.body(any(Object.class))).thenReturn(requestBodyEntityMock);
            Mockito.when(requestBodyEntityMock.asJson()).thenReturn(responseMock);

            Mockito.when(responseMock.getStatus()).thenReturn(500);
            Mockito.when(responseMock.getBody()).thenReturn(new JsonNode("{\"error\":\"boom\"}"));

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
        }
    }

    @Test
    void successfulResponse_returnsBody() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        CreateConfluencePage task = CreateConfluencePage.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("SPACEKEY"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .build();

        String json = "{\"id\":\"123\",\"title\":\"Title\"}";

        HttpRequestWithBody requestWithBodyMock = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity requestBodyEntityMock = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<JsonNode> responseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<Unirest> unirest = Mockito.mockStatic(Unirest.class)) {
            unirest.when(Unirest::config).thenCallRealMethod();

            unirest.when(() -> Unirest.post(argThat(url ->
                url.startsWith("https://example.com/wiki/api/v2/pages")
            ))).thenReturn(requestWithBodyMock);

            Mockito.when(requestWithBodyMock.queryString(Mockito.any(Map.class))).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.basicAuth("user@example.com", "token")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Accept", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Content-Type", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.body(any(Object.class))).thenReturn(requestBodyEntityMock);
            Mockito.when(requestBodyEntityMock.asJson()).thenReturn(responseMock);

            Mockito.when(responseMock.getStatus()).thenReturn(201);
            Mockito.when(responseMock.getBody()).thenReturn(new JsonNode(json));

            CreateConfluencePage.Output output = task.run(runContext);
            assertThat(output, is(notNullValue()));
            assertThat(output.getChild(), is(notNullValue()));
            assertThat(output.getChild().getValue(), containsString("\"id\":\"123\""));
        }
    }
}