package io.kestra.plugin.confluence;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
        description = "Base Confluence site URL (e.g., https://your-domain.atlassian.net) without a trailing slash; /wiki/api/v2 is appended automatically."
    )
    @NotNull
    protected Property<String> serverUrl;

    @Schema(
        title = "Authentication username (email)",
        description = "Confluence account email used for Basic authentication; render from secrets where possible."
    )
    @NotNull
    protected Property<String> username;

    @Schema(
        title = "API token for Basic auth",
        description = "Atlassian API token associated with the username; keep in a secret and avoid logging."
    )
    @NotNull
    protected Property<String> apiToken;
}
