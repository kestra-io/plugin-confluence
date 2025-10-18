package io.kestra.plugin.confluence.pages;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import kong.unirest.GetRequest;
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
import static org.mockito.ArgumentMatchers.argThat;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class FetchConfluencePageTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void missingRequiredProperties_throws() {
        RunContext runContext = runContextFactory.of(Map.of());
        // username and apiToken provided but serverUrl missing
        FetchConfluencePage task = FetchConfluencePage.builder()
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue(1))
            .spaceId(Property.ofValue(1))
            .limit(Property.ofValue(10))
            .build();
        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void non2xxResponse_throwsIllegalStateException() {
        RunContext runContext = runContextFactory.of(Map.of());

        FetchConfluencePage task = FetchConfluencePage.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue(1))
            .spaceId(Property.ofValue(1))
            .limit(Property.ofValue(10))
            .build();

        // prepare mocks for Unirest chain
        GetRequest getRequestMock = Mockito.mock(GetRequest.class);
        HttpResponse<JsonNode> responseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<Unirest> unirest = Mockito.mockStatic(Unirest.class)) {
            unirest.when(() -> Unirest.get(argThat(url ->
                url.startsWith("https://example.com/wiki/api/v2/pages") &&
                url.contains("id=1") &&
                url.contains("space-id=1") &&
                url.contains("limit=10") &&
                url.contains("body-format=storage")
            ))).thenReturn(getRequestMock);
            Mockito.when(getRequestMock.basicAuth("user@example.com", "token")).thenReturn(getRequestMock);
            Mockito.when(getRequestMock.header("Accept", "application/json")).thenReturn(getRequestMock);
            Mockito.when(getRequestMock.asJson()).thenReturn(responseMock);
            Mockito.when(responseMock.getStatus()).thenReturn(500);
            Mockito.when(responseMock.getBody()).thenReturn(new JsonNode("{\"error\":\"boom\"}"));

            assertThrows(IllegalStateException.class, () -> task.run(runContext));
        }
    }

    @Test
    void successfulResponse_parsesMarkdown() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        FetchConfluencePage task = FetchConfluencePage.builder()
            .serverUrl(Property.ofValue("https://example.com"))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue(1))
            .spaceId(Property.ofValue(1))
            .limit(Property.ofValue(10))
            .build();

        String json = "{ \"results\": [ { \"title\": \"Test Page\", \"body\": { \"storage\": { \"value\": \"<h1>Hello World</h1><p>This is a paragraph.</p>\" } } } ] }";

        GetRequest getRequestMock = Mockito.mock(GetRequest.class);
        HttpResponse<JsonNode> responseMock = Mockito.mock(HttpResponse.class);

        try (MockedStatic<Unirest> unirest = Mockito.mockStatic(Unirest.class)) {
            unirest.when(() -> Unirest.get(argThat(url ->
                url.startsWith("https://example.com/wiki/api/v2/pages") &&
                url.contains("id=1") &&
                url.contains("space-id=1") &&
                url.contains("limit=10") &&
                url.contains("body-format=storage")
            ))).thenReturn(getRequestMock);
            Mockito.when(getRequestMock.basicAuth("user@example.com", "token")).thenReturn(getRequestMock);
            Mockito.when(getRequestMock.header("Accept", "application/json")).thenReturn(getRequestMock);
            Mockito.when(getRequestMock.asJson()).thenReturn(responseMock);
            Mockito.when(responseMock.getStatus()).thenReturn(200);
            Mockito.when(responseMock.getBody()).thenReturn(new JsonNode(json));

            FetchConfluencePage.Output output = task.run(runContext);
            assertThat(output, is(notNullValue()));
            assertThat(output.getChildren(), hasSize(1));

            FetchConfluencePage.OutputChild child = output.getChildren().get(0);
            assertThat(child.getTitle(), is("Test Page"));
            assertThat(child.getMarkdown(), containsString("Hello World"));
            assertThat(child.getMarkdown(), containsString("This is a paragraph."));
        }
    }
}
