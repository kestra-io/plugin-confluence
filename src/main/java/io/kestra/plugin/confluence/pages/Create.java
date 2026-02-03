package io.kestra.plugin.confluence.pages;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.confluence.AbstractConfluenceTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create Confluence page from Markdown",
    description = "Creates a page in a Confluence space using REST API v2. Renders Markdown to Confluence storage HTML, supports optional status/parent/subtype flags, and passes embedded/private/root-level query parameters. Requires spaceId, title, markdown, and Basic authentication."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a basic page in a Confluence space.",
            code = """
                id: create-confluence-page
                namespace: company.team

                tasks:
                  - id: create_page
                    type: io.kestra.plugin.confluence.pages.Create
                    serverUrl: https://your-domain.atlassian.net
                    username: your-email@example.com
                    apiToken: {{ secret('CONFLUENCE_API_TOKEN') }}
                    spaceId: "123456"
                    title: My New Page from Kestra
                    markdown: |
                      # Kestra-Generated Page
                      This page was created automatically from a Kestra flow.
                      - List item 1
                      - List item 2
                """
        )
    }
)
public class Create extends AbstractConfluenceTask implements RunnableTask<Create.Output> {
    @Schema(
        title = "Create content as embedded",
        description = "Sets `embedded=true` so Confluence stores the page in the new content service. Default: false")
    private Property<Boolean> embedded;

    @Schema(
        title = "Make page private",
        description = "If true, only the creator can view and edit the page until permissions are changed. Default: false")
    private Property<Boolean> makePrivate;

    @Schema(
        title = "Create page at space root",
        description = "Creates the page at the space root (outside the homepage tree) and forbids parentId. Default: false")
    private Property<Boolean> rootLevel;

    @Schema(title = "Target space ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Property<String> spaceId;

    @Schema(
        title = "Page status",
        description = "Optional page status. Valid values: `current`, `draft`. Confluence applies its default when omitted."
    )
    private Property<String> status;

    @Schema(
        title = "Page title",
        description = "Display title for the new Confluence page."
    )
    private Property<String> title;

    @Schema(
        title = "Parent page ID",
        description = "Parent content ID. When rootLevel is false and omitted, Confluence uses the space homepage; ignored when rootLevel is true."
    )
    private Property<String> parentId;

    @Schema(
        title = "Markdown content to upload",
        description = "Markdown rendered to Confluence storage HTML before sending to the API."
    )
    private Property<String> markdown;

    @Schema(
        title = "Page subtype",
        description = "Optional subtype. Use `live` to create a collaborative live doc; omit for a regular page."
    )
    private Property<String> subtype;

    @Override
    public Create.Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

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
        String rParentId = runContext.render(this.parentId).as(String.class).orElse(null);
        String rSubtype = runContext.render(this.subtype).as(String.class).orElse(null);
        String rStatus = runContext.render(this.status).as(String.class).orElse(null);
        Boolean rEmbedded = runContext.render(this.embedded).as(Boolean.class).orElse(false);
        Boolean rMakePrivate = runContext.render(this.makePrivate).as(Boolean.class).orElse(false);
        Boolean rRootLevel = runContext.render(this.rootLevel).as(Boolean.class).orElse(false);

        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node document = parser.parse(rMarkdown);
        String htmlBody = renderer.render(document);

        JsonNodeFactory jnf = JsonNodeFactory.instance;
        ObjectNode payload = jnf.objectNode();

        payload.put("spaceId", rSpaceId);
        payload.put("title", rTitle);

        if (rStatus != null) payload.put("status", rStatus);
        if (rParentId != null) payload.put("parentId", rParentId);
        if (rSubtype != null) payload.put("subtype", rSubtype);

        ObjectNode body = payload.putObject("body");
        body.put("representation", "storage");
        body.put("value", htmlBody);

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("embedded", rEmbedded);
        queryParams.put("private", rMakePrivate);
        queryParams.put("root-level", rRootLevel);

        String authString = rUsername + ":" + rApiToken;
        String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));

        String base = rServerUrl.endsWith("/") ? rServerUrl.substring(0, rServerUrl.length() - 1) : rServerUrl;
        String url = base + "/wiki/api/v2/pages";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic " + encodedAuth)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body() != null ? response.body() : "";
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.error("Confluence create failed: status={} body={}", response.statusCode(), responseBody);
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
            title = "Confluence API response",
            description = "Full JSON response returned by Confluence after the page is created."
        )
        private final String value;
    }

}
