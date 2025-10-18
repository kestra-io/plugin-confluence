package io.kestra.plugin.confluence.pages;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;

@KestraTest
class UpdateConfluencePageTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        // serverUrl is required -> should throw
        UpdateConfluencePage task = UpdateConfluencePage.builder()
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(Property.ofValue(UpdateConfluencePage.VersionInfo.builder()
                .number(Property.ofValue(2))
                .message(Property.ofValue("msg"))
                .build()))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void non2xxResponse_throwsIllegalStateException() {
        RunContext runContext = runContextFactory.of(Map.of());

        UpdateConfluencePage task = UpdateConfluencePage.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(Property.ofValue(UpdateConfluencePage.VersionInfo.builder()
                .number(Property.ofValue(2))
                .message(Property.ofValue("msg"))
                .build()))
            .build();

        HttpRequestWithBody requestWithBodyMock = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity requestBodyEntityMock = Mockito.mock(RequestBodyEntity.class); 
        HttpResponse<JsonNode> responseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<Unirest> unirest = Mockito.mockStatic(Unirest.class)) {
        
            unirest.when(Unirest::config).thenCallRealMethod();
            unirest.when(() -> Unirest.put(argThat(url ->
                url.startsWith("https://example.com/wiki/api/v2/pages/123")
            ))).thenReturn(requestWithBodyMock);

            Mockito.when(requestWithBodyMock.basicAuth("user@example.com", "token")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Accept", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Content-Type", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.body(Mockito.any(Object.class))).thenReturn(requestBodyEntityMock);
            Mockito.when(requestBodyEntityMock.asJson()).thenReturn(responseMock);

            Mockito.when(responseMock.getStatus()).thenReturn(500);
            Mockito.when(responseMock.getBody()).thenReturn(new JsonNode("{\"error\":\"boom\"}"));

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
        }
    }

    @Test
    void successfulResponse_returnsBody() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        UpdateConfluencePage task = UpdateConfluencePage.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(Property.ofValue(UpdateConfluencePage.VersionInfo.builder()
                .number(Property.ofValue(2))
                .message(Property.ofValue("msg"))
                .build()))
            .build();

        String json = "{\"id\":\"123\",\"title\":\"Title\"}";

        HttpRequestWithBody requestWithBodyMock = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity requestBodyEntityMock = Mockito.mock(RequestBodyEntity.class); 
        HttpResponse<JsonNode> responseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<Unirest> unirest = Mockito.mockStatic(Unirest.class)) {
            unirest.when(Unirest::config).thenCallRealMethod();

            unirest.when(Unirest::config).thenCallRealMethod();
            unirest.when(() -> Unirest.put(argThat(url ->
                url.startsWith("https://example.com/wiki/api/v2/pages/123")
            ))).thenReturn(requestWithBodyMock);

            Mockito.when(requestWithBodyMock.basicAuth("user@example.com", "token")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Accept", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.header("Content-Type", "application/json")).thenReturn(requestWithBodyMock);
            Mockito.when(requestWithBodyMock.body(Mockito.any(Object.class))).thenReturn(requestBodyEntityMock);
            Mockito.when(requestBodyEntityMock.asJson()).thenReturn(responseMock);


            Mockito.when(responseMock.getStatus()).thenReturn(200);
            Mockito.when(responseMock.getBody()).thenReturn(new JsonNode(json));

            UpdateConfluencePage.Output output = task.run(runContext);
            assertThat(output, is(notNullValue()));
            assertThat(output.getChild(), is(notNullValue()));
            assertThat(output.getChild().getValue(), containsString("\"id\":\"123\""));
        }
    }
}