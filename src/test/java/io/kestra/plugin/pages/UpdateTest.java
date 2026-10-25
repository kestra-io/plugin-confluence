package io.kestra.plugin.pages;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.confluence.pages.Update;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class UpdateTest extends WireMockTest {
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
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", 2,
                        "message", "msg"
                    )
                )
            )
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void missingVersionNumber_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "message", "msg"
                    )
                )
            )
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void missingVersionMessage_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", 2
                    )
                )
            )
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void blankVersionMessage_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", 2,
                        "message", "   "
                    )
                )
            )
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void invalidVersionNumberString_throws() {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", "four",
                        "message", "msg"
                    )
                )
            )
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
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", 2,
                        "message", "msg"
                    )
                )
            )
            .build();

        wireMock.stubFor(put(urlEqualTo(URL)).willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

        var e = assertThrows(IllegalStateException.class, () -> task.run(runContext));

        assertThat(e.getMessage(), is("Confluence API returned 500"));
        wireMock.verify(1, putRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void successfulResponse_returnsBody() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", 2,
                        "message", "msg"
                    )
                )
            )
            .build();

        wireMock.stubFor(put(urlEqualTo(URL)).willReturn(aResponse().withStatus(200).withBody("{\"id\":\"123\",\"title\":\"Title\"}")));

        var output = task.run(runContext);

        assertThat(output.getValue(), containsString("\"id\":\"123\""));
        wireMock.verify(
            1,
            putRequestedFor(urlEqualTo(URL))
                .withHeader("Authorization", equalTo(AUTH))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(
                    equalToJson(
                        "{\"id\":\"123\",\"status\":\"current\",\"title\":\"Title\",\"version\":{\"number\":2,\"message\":\"msg\"},\"body\":{\"representation\":\"storage\",\"value\":\"<h1>Hello</h1>\\n\"}}"
                    )
                )
        );
    }

    private void assertVersionNumberIsRenderedAsJsonNumber(Object rawNumber) throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Update task = Update.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .pageId(Property.ofValue("123"))
            .status(Property.ofValue("current"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .versionInfo(
                Property.ofValue(
                    Map.of(
                        "number", rawNumber,
                        "message", "msg"
                    )
                )
            )
            .build();

        wireMock.stubFor(put(urlEqualTo(URL)).willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        task.run(runContext);

        var body = wireMock.findAll(putRequestedFor(urlEqualTo(URL))).getFirst().getBodyAsString();
        assertThat(body, containsString("\"version\""));
        assertThat(body, containsString("\"number\":4"));
        assertThat(body, not(containsString("\"number\":\"4\"")));
        assertThat(body, containsString("\"message\":\"msg\""));
    }

    private static final String URL = "/wiki/api/v2/pages/123";
}
