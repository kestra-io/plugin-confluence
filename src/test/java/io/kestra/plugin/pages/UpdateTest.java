package io.kestra.plugin.pages;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.confluence.pages.Update;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

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
    void missingVersionNumber_throws() {
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
                "message", "msg"
            )))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void missingVersionMessage_throws() {
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
                "number", 2
            )))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void blankVersionMessage_throws() {
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
                "message", "   "
            )))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void invalidVersionNumberString_throws() {
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
                "number", "four",
                "message", "msg"
            )))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void versionNumberAsStringInteger_isParsedAndSent() throws Exception {
        assertVersionNumberIsRenderedAsJsonNumber("4");
    }

    @Test
    void versionNumberAsStringDecimal_isParsedAndSent() throws Exception {
        assertVersionNumberIsRenderedAsJsonNumber("4.0");
    }

    @Test
    void versionNumberAsLong_isParsedAndSent() throws Exception {
        assertVersionNumberIsRenderedAsJsonNumber(4L);
    }

    @Test
    void versionNumberAsDouble_isParsedAndSent() throws Exception {
        assertVersionNumberIsRenderedAsJsonNumber(4.0d);
    }

    @Test
    void versionNumberAsInteger_isSent() throws Exception {
        assertVersionNumberIsRenderedAsJsonNumber(4);
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

    private void assertVersionNumberIsRenderedAsJsonNumber(Object rawNumber) throws Exception {
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
                "number", rawNumber,
                "message", "msg"
            )))
            .build();

        HttpClient httpClientMock = Mockito.mock(HttpClient.class);
        HttpResponse<String> httpResponseMock = Mockito.mock(HttpResponse.class);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        try (MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class)) {
            httpClientStatic.when(HttpClient::newHttpClient).thenReturn(httpClientMock);

            when(httpResponseMock.statusCode()).thenReturn(200);
            when(httpResponseMock.body()).thenReturn("{\"ok\":true}");

            when(httpClientMock.send(
                requestCaptor.capture(),
                eq(HttpResponse.BodyHandlers.ofString())
            )).thenReturn(httpResponseMock);

            task.run(runContext);

            HttpRequest sent = requestCaptor.getValue();
            assertThat(sent, is(notNullValue()));

            String body = readBody(sent);
            assertThat(body, is(notNullValue()));

            assertThat(body, containsString("\"version\""));
            assertThat(body, containsString("\"number\":" + 4));
            assertThat(body, not(containsString("\"number\":\"" + 4 + "\"")));
            assertThat(body, containsString("\"message\":\"msg\""));
        }
    }

    private static String readBody(HttpRequest request) throws Exception {
        var publisherOpt = request.bodyPublisher();
        if (publisherOpt.isEmpty()) {
            return "";
        }

        HttpRequest.BodyPublisher publisher = publisherOpt.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] buf = new byte[item.remaining()];
                item.get(buf);
                out.writeBytes(buf);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });

        done.get(); // block until complete
        return out.toString(StandardCharsets.UTF_8);
    }
}
