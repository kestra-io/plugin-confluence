package io.kestra.plugin.confluence;

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
import io.kestra.core.models.annotations.PluginProperty;

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
    @PluginProperty(group = "main")
    protected Property<String> username;

    @Schema(
        title = "API token for Basic auth",
        description = "Atlassian API token associated with the username; keep in a secret and avoid logging."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> apiToken;

    protected String buildApiBaseUrl(RunContext runContext) throws Exception {
        String server = runContext.render(this.serverUrl).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("serverUrl is required"));
        String path = runContext.render(this.apiPath).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiPath is required"));
        return stripTrailingSlash(server) + stripTrailingSlash(path);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
