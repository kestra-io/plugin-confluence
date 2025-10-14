package io.kestra.plugin.confluence.pages;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import org.slf4j.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.ObjectMapper;
import java.util.Map;
import java.util.LinkedHashMap;
import java.io.IOException;
import jakarta.validation.constraints.NotNull;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a Confluence Page",
    description = "This task creates a new page in a Confluence space. It uses Markdown for the page content, which is automatically converted to Confluence's storage format. You can specify the page title, space, parent page, and more. This task utilizes the Confluence REST API v2."
)
@Plugin(
    examples = {
        @io.kestra.core.models.annotations.Example(
            title = "Create a basic page in a Confluence space.",
            code = {
                "id: createConfluencePage",
                "type: io.kestra.plugin.confluence.CreateConfluencePage",
                "serverUrl: \"https://your-domain.atlassian.net\"",
                "username: \"your-email@example.com\"",
                "apiToken: \"{{ secret('YOUR_SECRET') }}\"",
                "spaceId: \"SPACEKEY\"",
                "title: \"My New Page from Kestra\"",
                "markdown: |",
                "  # Kestra-Generated Page",
                "  This page was created automatically from a Kestra flow.",
                "  - List item 1",
                "  - List item 2"
            }
        )
    }
)
public class CreateConfluencePage extends Task implements RunnableTask<CreateConfluencePage.Output> {
    static{
        Unirest.config().setObjectMapper(new ObjectMapper() {
            private com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper
                    = new com.fasterxml.jackson.databind.ObjectMapper();

            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jacksonObjectMapper.readValue(value, valueType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
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
        title = "Embedded Content",
        description = "Tags the content as embedded, which will cause it to be created in NCS. Default: false")
    private Property<Boolean> embedded;

    @Schema(
        title = "Make Page Private",
        description = "If true, the page will be private. Only the user who creates the page will have permission to view and edit it. Default: false")
    private Property<Boolean> makePrivate;

    @Schema(
        title = "Create at Root Level",
        description = "If true, the page will be created at the root level of the space (outside the space homepage tree). A value may not be supplied for the parentId parameter when this is true. Default: false")
    private Property<Boolean> rootLevel;
    
    @Schema(title = "Space ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Property<@NotNull String> spaceId;

    @Schema(
        title = "Page Status",
        description = "The updated status of the page. Valid values: `current`, `draft`."
    )
    private Property<String> status;

    @Schema(
        title = "Page title filter.",
        description = "Filter the results to pages that exactly match this title."
    )
    private Property<String> title;

    @Schema(
        title = "Parent Page ID",
        description = "The parent content ID of the page. If the 'root-level' query parameter is set to false and a value is not supplied, the space homepage's ID will be used. If the 'root-level' query parameter is set to true, a value cannot be supplied for this parameter."
    )
    private Property<String> parentId;

    @Schema(
        title = "Markdown Content to Upload",
        description = "The Markdown content to publish on the page."
    )
    private Property<String> markdown;

    @Schema(
        title = "Page Subtype",
        description = "The subtype of the page. Provide 'live' to create a live doc, or no subtype to create a regular page. Valid values: live"
    )
    private Property<String> subtype;

    @Override
    public CreateConfluencePage.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render and validate required fields
        String rServerUrl = runContext.render(this.serverUrl).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("serverUrl is required"));
        String rUsername = runContext.render(this.username).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("username is required"));
        String rApiToken = runContext.render(this.apiToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiToken is required"));
        String rSpaceId = runContext.render(this.spaceId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("spaceId is required"));
        String rTitle = runContext.render(this.title).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("title is required"));
        String rMarkdown = runContext.render(this.markdown).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("markdown is required"));
        
        // Render optional fields
        String rParentId = runContext.render(this.parentId).as(String.class).orElse(null);
        String rSubtype = runContext.render(this.subtype).as(String.class).orElse(null);
        String rStatus = runContext.render(this.status).as(String.class).orElse(null);
        Boolean rEmbedded = runContext.render(this.embedded).as(Boolean.class).orElse(false);
        Boolean rMakePrivate = runContext.render(this.makePrivate).as(Boolean.class).orElse(false);
        Boolean rRootLevel = runContext.render(this.rootLevel).as(Boolean.class).orElse(false);

        // Convert Markdown to HTML
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node document = parser.parse(rMarkdown);
        String htmlBody = renderer.render(document);

        JsonNodeFactory jnf = JsonNodeFactory.instance;
        ObjectNode payload = jnf.objectNode();

        // Build JSON payload
        payload.put("spaceId", rSpaceId);
        payload.put("status", rStatus);
        payload.put("title", rTitle);
        payload.put("parentId", rParentId);
        payload.put("subtype", rSubtype);

        ObjectNode body = payload.putObject("body");
        body.put("representation", "storage");
        body.put("value", htmlBody);

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("embedded", rEmbedded);
        queryParams.put("private", rMakePrivate);
        queryParams.put("root-level", rRootLevel);
        

        String base = rServerUrl.endsWith("/") ? rServerUrl.substring(0, rServerUrl.length() - 1) : rServerUrl;
        String url = base + "/wiki/api/v2/pages";

        HttpResponse<JsonNode> response = Unirest.post(url)
            .queryString(queryParams)
            .basicAuth(rUsername, rApiToken)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(payload)
            .asJson();

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            logger.error("Confluence create failed: status={} body={}", response.getStatus(), response.getBody());
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
            title = "The API response from Confluence.",
            description = "Contains the full JSON response from the Confluence API after creating the page."
        )
        private final OutputChild child;
    }

    @Builder
    @Getter
    public static class OutputChild implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The response body as a string.",
            description = "The JSON response body from the Confluence 'Create page' API endpoint, returned as a string."
        )
        private final String value;
    }
}
