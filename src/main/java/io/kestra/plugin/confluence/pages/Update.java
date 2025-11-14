package io.kestra.plugin.confluence.pages;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.models.annotations.Example;
import io.kestra.plugin.confluence.AbstractConfluenceTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.Base64;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
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
        @Example(
            title = "Update the title and content of a specific Confluence page.",
            code = """
                id: update-confluence-page
                namespace: company.team

                tasks:
                    - id: 1
                      type: io.kestra.plugin.confluence.pages.Update
                      serverUrl: https://your-domain.atlassian.net
                      username: user@example.com
                      apiToken: "{{ secret('CONFLUENCE_API_TOKEN') }}"
                      pageId: 12345678
                      status: current
                      title: New Page Title
                      markdown: # My Updated Content\\nThis is the new content for the page.
                      version:
                        number: 2
                        message: Updated content and title via Kestra.
            """
        )
    }
)
public class Update extends AbstractConfluenceTask implements RunnableTask<Update.Output> {
    @Schema(
        title = "Page ID",
        description = "The unique identifier of the page to update. Must match the path parameter `id`."
    )
    @NotNull
    private Property<String> pageId;

    @Schema(
        title = "Page Status",
        description = "The updated status of the page. Valid values: `current`, `draft`. Changing from `current` to `draft` deletes any existing draft."
    )
    @NotNull
    private Property<String> status;

    @Schema(
        title = "Page Title",
        description = "The updated title of the page."
    )
    @NotNull
    private Property<String> title;

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
    private Property<String> ownerId;

    @Schema(title = "Markdown content to upload",
        description = "The Markdown content to publish on the page."
    )
    @NotNull
    private Property<String> markdown;

    @Schema(
        title = "Version Information",
        description = "Defines version details for the page update. The `number` represents the version number, and `message` is an optional version comment."
    )
    @NotNull
    private Property<Map<String, Object>> versionInfo;

    @Override
    public Update.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

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
        String rSpaceId = runContext.render(this.spaceId).as(String.class).orElse(null);
        String rParentId = runContext.render(this.parentId).as(String.class).orElse(null);
        String rOwnerId = runContext.render(this.ownerId).as(String.class).orElse(null);

        Map<String, Object> rVersionMap = runContext.render(this.versionInfo)
            .asMap(String.class, Object.class);

        Integer numberObj = (Integer) rVersionMap.get("number");
        String messageObj = (String) rVersionMap.get("message");
        if (numberObj == null || messageObj == null) {
            throw new IllegalArgumentException("versionInfo.number and versionInfo.message are required");
        }

        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node document = parser.parse(rMarkdown);
        String htmlBody = renderer.render(document);

        JsonNodeFactory jnf = JsonNodeFactory.instance;
        ObjectNode  payload = jnf.objectNode();
        ObjectNode  versionNode = payload.putObject("version");
        ObjectNode body = payload.putObject("body");

        body.put("representation", "storage");
        body.put("value", htmlBody);

        versionNode.put("number", numberObj);
        versionNode.put("message", messageObj);

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

        String auth = rUsername + ":" + rApiToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        String base = rServerUrl.endsWith("/") ? rServerUrl.substring(0, rServerUrl.length() - 1) : rServerUrl;
        String url = base + "/wiki/api/v2/pages/" + rPageId;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic " + encodedAuth)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body() != null ? response.body() : "";
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.error("Confluence update failed: status={} body={}", response.statusCode(), responseBody);
            throw new IllegalStateException("Confluence API returned " + response.statusCode());
        }

        return Output.builder()
            .value(responseBody)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Update Page Output",
            description = "Contains the response from the Confluence API after the update operation."
        )
        private final String value;
    }
}
