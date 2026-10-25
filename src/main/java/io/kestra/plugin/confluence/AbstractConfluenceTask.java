package io.kestra.plugin.confluence;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractConfluenceTask extends Task {
    @Schema(
        title = "Set Confluence site URL",
        description = "Base Confluence site URL (e.g., `https://your-domain.atlassian.net`) without a trailing slash."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> serverUrl;

    @Schema(
        title = "API base path",
        description = "Base path appended to the server URL before the resource endpoint. Defaults to `/wiki/api/v2` for Confluence Cloud. Override for On-Premise instances (e.g., `/rest/api` or a custom context root)."
    )
    @PluginProperty(group = "main")
    @Builder.Default
    protected Property<String> apiPath = Property.ofValue("/wiki/api/v2");

    @Schema(
        title = "Authentication username (email)",
        description = "Confluence account email used for Basic authentication; render from secrets where possible."
    )
    @NotNull
    @PluginProperty(secret = true, group = "main")
    @ToString.Exclude
    protected Property<String> username;

    @Schema(
        title = "API token for Basic auth",
        description = "Atlassian API token associated with the username; keep in a secret and avoid logging."
    )
    @NotNull
    @PluginProperty(secret = true, group = "main")
    @ToString.Exclude
    protected Property<String> apiToken;

    @Schema(
        title = "HTTP client configuration",
        description = "Optional timeout, proxy, SSL and logging settings of the HTTP client used to call Confluence. Authentication comes from `username` and `apiToken`, any non-2xx response fails the task, and redirects are never followed."
    )
    @PluginProperty(group = "advanced")
    protected HttpConfiguration options;

    protected String buildApiBaseUrl(RunContext runContext) throws Exception {
        String server = runContext.render(this.serverUrl).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("serverUrl is required"));
        String path = runContext.render(this.apiPath).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiPath is required"));
        return stripTrailingSlash(server) + stripTrailingSlash(path);
    }

    protected HttpResponse<String> send(RunContext runContext, String method, URI uri, String body) throws Exception {
        var rUsername = runContext.render(this.username).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("username is required"));
        var rApiToken = runContext.render(this.apiToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiToken is required"));
        var encodedAuth = Base64.getEncoder().encodeToString("%s:%s".formatted(rUsername, rApiToken).getBytes(StandardCharsets.UTF_8));

        var request = HttpRequest.builder()
            .uri(uri)
            .method(method)
            .addHeader("Authorization", "Basic %s".formatted(encodedAuth))
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0");
        if (body != null) {
            request.addHeader("Content-Type", "application/json")
                .body(HttpRequest.StringRequestBody.builder().content(body).build());
        }

        try (var client = new HttpClient(runContext, withoutRedirects(this.options))) {
            var response = client.request(request.build(), String.class);
            if (response.getStatus().getCode() >= 300 && response.getStatus().getCode() < 400) {
                throw new IllegalStateException("Confluence API returned %s".formatted(response.getStatus().getCode()));
            }
            return response;
        } catch (HttpClientResponseException e) {
            var response = e.getResponse();
            if (response == null) {
                throw e;
            }
            var status = response.getStatus().getCode();
            // the core client hands over the body of a failed response as raw bytes
            var failedBody = response.getBody() instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : "";
            runContext.logger().error("Confluence request failed: status={} body={}", status, failedBody);
            throw new IllegalStateException("Confluence API returned %s".formatted(status));
        }
    }

    // transport settings only, the plugin owns auth and status handling, and toBuilder() would wipe them with deprecated null setters
    private static HttpConfiguration withoutRedirects(HttpConfiguration options) {
        var builder = HttpConfiguration.builder();
        if (options != null) {
            builder.timeout(options.getTimeout())
                .proxy(options.getProxy())
                .ssl(options.getSsl())
                .enabledTcpExtendedKeepAlive(options.getEnabledTcpExtendedKeepAlive())
                .logs(options.getLogs());
        }
        // a followed redirect turns a POST into a GET and reports success, so a 3xx fails the task
        return builder.followRedirects(Property.ofValue(false)).build();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
