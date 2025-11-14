package io.kestra.plugin.pages;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.confluence.pages.List;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
}
