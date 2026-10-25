package io.kestra.plugin.pages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

abstract class WireMockTest {
    // Basic base64("user@example.com:token")
    protected static final String AUTH = "Basic dXNlckBleGFtcGxlLmNvbTp0b2tlbg==";

    protected WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    protected String serverUrl() {
        return wireMock.baseUrl();
    }
}
