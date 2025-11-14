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
        title = "URL of the Confluence server.",
        description = "Base URL of the Confluence instance (e.g., https://your-domain.atlassian.net/wiki)."
    )
    @NotNull
    protected Property<String> serverUrl;

    @Schema(
        title = "Username (email) for authentication.",
        description = "Confluence account email address used for API authentication."
    )
    @NotNull
    protected Property<String> username;

    @Schema(
        title = "Confluence API Token for authentication.",
        description = "API token generated in Confluence (Atlassian account) used for authentication."
    )
    @NotNull
    protected Property<String> apiToken;
}
