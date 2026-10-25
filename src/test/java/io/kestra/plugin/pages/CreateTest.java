package io.kestra.plugin.pages;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.confluence.pages.Create;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CreateTest extends WireMockTest {
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
        wireMock.stubFor(post(urlEqualTo(URL)).willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

        var e = assertThrows(IllegalStateException.class, () -> task().run(runContextFactory.of(Map.of())));

        assertThat(e.getMessage(), is("Confluence API returned 500"));
        wireMock.verify(1, postRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void redirectResponse_throwsIllegalStateException() throws Exception {
        wireMock.stubFor(post(urlEqualTo(URL)).willReturn(aResponse().withStatus(301).withHeader("Location", "%s/moved".formatted(serverUrl()))));
        wireMock.stubFor(get(urlEqualTo("/moved")).willReturn(aResponse().withStatus(200).withBody("{\"results\":[]}")));

        var e = assertThrows(IllegalStateException.class, () -> task().run(runContextFactory.of(Map.of())));

        assertThat(e.getMessage(), is("Confluence API returned 301"));
        wireMock.verify(1, postRequestedFor(urlEqualTo(URL)));
        wireMock.verify(0, getRequestedFor(urlEqualTo("/moved")));
    }

    @Test
    void successfulResponse_returnsBody() throws Exception {
        wireMock.stubFor(post(urlEqualTo(URL)).willReturn(aResponse().withStatus(201).withBody("{\"id\":\"123\",\"title\":\"Title\"}")));

        var output = task().run(runContextFactory.of(Map.of()));

        assertThat(output.getValue(), containsString("\"id\":\"123\""));
        wireMock.verify(
            1,
            postRequestedFor(urlEqualTo(URL))
                .withHeader("Authorization", equalTo(AUTH))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"spaceId\":\"SPACEKEY\",\"title\":\"Title\",\"body\":{\"representation\":\"storage\",\"value\":\"<h1>Hello</h1>\\n\"}}"))
        );
    }

    @Test
    void shouldSendEmbeddedPrivateAndRootLevelOnTheCreateUrl() throws Exception {
        var url = "/wiki/api/v2/pages?embedded=true&private=true&root-level=true";
        wireMock.stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201).withBody("{\"id\":\"123\"}")));

        var task = Create.builder()
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("123456"))
            .title(Property.ofValue("My New Page from Kestra"))
            .markdown(Property.ofValue("# Hello"))
            .embedded(Property.ofValue(true))
            .makePrivate(Property.ofValue(true))
            .rootLevel(Property.ofValue(true))
            .build();

        var output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getValue(), containsString("\"id\":\"123\""));
        wireMock.verify(1, postRequestedFor(urlEqualTo(url)));
    }

    @Test
    void userHttpOptions_keepPluginAuth() throws Exception {
        wireMock.stubFor(post(urlEqualTo(URL)).willReturn(aResponse().withStatus(201).withBody("{\"id\":\"123\"}")));

        var output = task(timeout(Duration.ofSeconds(30))).run(runContextFactory.of(Map.of()));

        assertThat(output.getValue(), containsString("\"id\":\"123\""));
        wireMock.verify(1, postRequestedFor(urlEqualTo(URL)).withHeader("Authorization", equalTo(AUTH)));
    }

    @Test
    void userHttpOptions_keepTimeout() {
        wireMock.stubFor(post(urlEqualTo(URL)).willReturn(aResponse().withStatus(201).withBody("{}").withFixedDelay(3000)));

        assertThrows(Exception.class, () -> task(timeout(Duration.ofMillis(500))).run(runContextFactory.of(Map.of())));
    }

    @Test
    void userHttpOptions_cannotAllowFailedResponses() {
        wireMock.stubFor(post(urlEqualTo(URL)).willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));
        var options = HttpConfiguration.builder().allowFailed(Property.ofValue(true)).build();

        var e = assertThrows(IllegalStateException.class, () -> task(options).run(runContextFactory.of(Map.of())));

        assertThat(e.getMessage(), is("Confluence API returned 500"));
    }

    private static HttpConfiguration timeout(Duration readIdleTimeout) {
        return HttpConfiguration.builder()
            .timeout(TimeoutConfiguration.builder().readIdleTimeout(Property.ofValue(readIdleTimeout)).build())
            .build();
    }

    private static final String URL = "/wiki/api/v2/pages?embedded=false&private=false&root-level=false";

    private Create task() {
        return task(null);
    }

    private Create task(HttpConfiguration options) {
        return Create.builder()
            .options(options)
            .serverUrl(Property.ofValue(serverUrl()))
            .username(Property.ofValue("user@example.com"))
            .apiToken(Property.ofValue("token"))
            .spaceId(Property.ofValue("SPACEKEY"))
            .title(Property.ofValue("Title"))
            .markdown(Property.ofValue("# Hello"))
            .build();
    }
}
