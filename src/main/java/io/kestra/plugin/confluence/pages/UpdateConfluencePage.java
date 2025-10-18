package io.kestra.plugin.confluence.pages;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.ObjectMapper;
import kong.unirest.Unirest;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a Confluence Page",
    description = "Updates an existing page in Confluence by its ID. You can modify its title, content, parent, and other properties."
)
@Plugin(
    examples = {
        @io.kestra.core.models.annotations.Example(
            title = "Update the title and content of a specific Confluence page.",
            code = {
                "serverUrl: \"https://your-domain.atlassian.net\"",
                "username: \"user@example.com\"",
                "apiToken: \"{{ secret('YOUR_SECRET') }}\"",
                "pageId: \"12345678\"",
                "status: \"current\"",
                "title: \"New Page Title\"",
                "markdown: \"# My Updated Content\\nThis is the new content for the page.\"",
                "version:",
                "  number: 2",
                "  message: \"Updated content and title via Kestra.\""
            }
        )
    }
)
public class UpdateConfluencePage extends Task implements RunnableTask<UpdateConfluencePage.Output> {

    static {
        Unirest.config().setObjectMapper(new ObjectMapper() {
            private final com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper
                = new com.fasterxml.jackson.databind.ObjectMapper();

            @Override
            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jacksonObjectMapper.readValue(value, valueType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String writeValue(Object value) {
                try {
                    return jacksonObjectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Schema(
        title = "URL of the Confluence server.",
        description = "Base URL of the Confluence instance (e.g., https://your-domain.atlassian.net/wiki).",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull String> serverUrl;

    @Schema(
        title = "Username (email) for authentication.",
        description = "Confluence account email address used for API authentication.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull String> username;

    @Schema(
        title = "Confluence API Token for authentication.",
        description = "API token generated in Confluence (Atlassian account) used for authentication.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull String> apiToken;

    @Schema(
        title = "Page ID",
        description = "The unique identifier of the page to update. Must match the path parameter `id`.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull String> pageId;

    @Schema(
        title = "Page Status",
        description = "The updated status of the page. Valid values: `current`, `draft`. Changing from `current` to `draft` deletes any existing draft.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull String> status;

    @Schema(
        title = "Page Title",
        description = "The updated title of the page.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull String> title;

    @Schema(
        title = "Space ID",
        description = "The ID of the containing space. Moving a page to another space is not supported."
    )
    private Property<String> spaceId;

    @Schema(
        title = "Parent Page ID",
        description = "The ID of the parent content. Allows moving the page under a different parent within the same space."
    )
    private Property<String> parentId;

    @Schema(
        title = "Owner Account ID",
        description = "The account ID of the page owner. Used to transfer ownership to another user."
    )
    @PluginProperty
    private Property<String> ownerId;

    @Schema(title = "Markdown content to upload")
    private Property<@NotNull String> markdown;

    @Schema(
        title = "Version Information",
        description = "Defines version details for the page update. The `number` represents the version number, and `message` is an optional version comment.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Property<@NotNull VersionInfo> versionInfo;

    @Builder
    @Getter
    public static class VersionInfo {
        @Schema(
            title = "Version Number",
            description = "The number of the new version.",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Property<@NotNull Integer> number;

        @Schema(
            title = "Version Message",
            description = "A message describing the changes made in this version.",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Property<@NotNull String> message;
    }


    @Override
    public UpdateConfluencePage.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render and validate required fields
        String rServerUrl = runContext.render(this.serverUrl).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("serverUrl is required"));
        String rUsername = runContext.render(this.username).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("username is required"));
        String rApiToken = runContext.render(this.apiToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiToken is required"));
        String rPageId = runContext.render(this.pageId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("pageId is required"));
        String rStatus = runContext.render(this.status).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("status is required"));
        String rTitle = runContext.render(this.title).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("title is required"));
        String rMarkdown = runContext.render(this.markdown).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("markdown is required"));
        
        // Render optional fields
        String rSpaceId = runContext.render(this.spaceId).as(String.class).orElse(null);
        String rParentId = runContext.render(this.parentId).as(String.class).orElse(null);
        String rOwnerId = runContext.render(this.ownerId).as(String.class).orElse(null);

        // Render and validate version info
        VersionInfo rVersion = runContext.render(this.versionInfo)
            .as(VersionInfo.class)
            .orElseThrow(() -> new IllegalArgumentException("version is required"));
        Integer rVersionNumber = runContext.render(rVersion.getNumber())
            .as(Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("version.number is required"));
        String rVersionMessage = runContext.render(rVersion.getMessage())
            .as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("version.message is required"));

        // Convert Markdown to HTML
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node document = parser.parse(rMarkdown);
        String htmlBody = renderer.render(document);

        JsonNodeFactory jnf = JsonNodeFactory.instance;
        ObjectNode  payload = jnf.objectNode();
        ObjectNode  versionNode = payload.putObject("version");
        ObjectNode body = payload.putObject("body");
        
        // Build JSON payload
        body.put("representation", "storage");
        body.put("value", htmlBody);

        versionNode.put("number", rVersionNumber);
        versionNode.put("message", rVersionMessage);

        payload.put("id", rPageId);
        payload.put("status", rStatus);
        payload.put("title", rTitle);
        if (rSpaceId != null) {
            payload.put("spaceId", rSpaceId);
        }
        if (rParentId != null) {
            payload.put("parentId", rParentId);
        }
        if (rOwnerId != null) {
            payload.put("ownerId", rOwnerId);
        }

        String base = rServerUrl.endsWith("/") ? rServerUrl.substring(0, rServerUrl.length() - 1) : rServerUrl;
        String url = base + "/wiki/api/v2/pages/" + rPageId;
 
        HttpResponse<JsonNode> response = Unirest.put(url)
            .basicAuth(rUsername, rApiToken)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(payload)
            .asJson();
            
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            logger.error("Confluence update failed: status={} body={}", response.getStatus(), response.getBody());
            throw new IllegalStateException("Confluence API returned " + response.getStatus());
        }

        String responseBody = response.getBody() != null ? response.getBody().toString() : "";
        return Output.builder()
            .child(OutputChild.builder().value(responseBody).build())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Update Page Output",
            description = "Contains the response from the Confluence API after the update operation."
        )
        private final OutputChild child;
    }

    @Builder
    @Getter
    public static class OutputChild implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "API Response Body",
            description = "The full JSON response from the Confluence API as a string."
        )
        private final String value;
    }
}
