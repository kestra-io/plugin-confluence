package io.kestra.plugin.confluence.pages;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import org.slf4j.Logger;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.json.JSONArray;
import org.json.JSONObject; 
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch a Confluence Page",
    description = "Retrieves the content of one or more pages from Confluence as Markdown."
)
@Plugin(
    examples = {
        @io.kestra.core.models.annotations.Example(
            title = "Fetch a Confluence page as Markdown",
            code = {
                "serverUrl: \"https://your-domain.atlassian.net\"",
                "username: \"user@example.com\"",
                "apiToken: \"{{ secret('YOUR_SECRET') }}\"",
                "spaceId: \"12345\""
            }
        )
    }
)
public class FetchConfluencePage extends Task implements RunnableTask<FetchConfluencePage.Output> {

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
        title = "Page IDs to filter.",
        description = "Filter results by one or more page IDs. Multiple IDs can be specified as a comma-separated list. Max items: 250."
    )
    private Property<@Max(250) Integer> pageId;

    @Schema(
        title = "Space IDs to filter.",
        description = "Filter results by one or more Confluence space IDs. Multiple IDs can be specified as a comma-separated list. Max items: 100."
    )
    private Property<@Max(100) Integer> spaceId;

    @Schema(
        title = "Sort order of results.",
        description = "Specify sorting of the result set. Valid values: id, -id, created-date, -created-date, modified-date, -modified-date, title, -title."
    )
    private Property<String> sort;

    @Schema(
        title = "Page title filter.",
        description = "Filter the results to pages that exactly match this title."
    )
    private Property<String> title;

    @Schema(
        title = "Page subtype filter.",
        description = "Filter the results based on page subtype. Valid values: live (collaborative draft/live page), page (regular page)."
    )
    private Property<String> subType;

    @Schema(
        title = "Pagination cursor.",
        description = "Used for pagination. This opaque cursor is returned in the Link response header. Use it to fetch the next set of results."
    )
    private Property<String> cursor;

    @Schema(
        title = "Maximum number of results per page.",
        description = "Limit the number of results returned per request. Default: 25, Min: 1, Max: 250."
    )
    private Property<@Min(1) @Max(250) Integer> limit;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        List<OutputChild> markdownPages = new ArrayList<>();

        // Render and validate required fields
        String rServerUrl = runContext.render(this.serverUrl).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("serverUrl is required"));
        String rUsername = runContext.render(this.username).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("username is required"));
        String rApiToken = runContext.render(this.apiToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiToken is required"));

        // Render optional fields
        Integer rSpaceId = runContext.render(this.spaceId).as(Integer.class).orElse(null);
        Integer rPageId = runContext.render(this.pageId).as(Integer.class).orElse(null);
        String rTitle = runContext.render(this.title).as(String.class).orElse(null);
        String rSubType = runContext.render(this.subType).as(String.class).orElse(null);
        String rSort = runContext.render(this.sort).as(String.class).orElse(null);
        String rCursor = runContext.render(this.cursor).as(String.class).orElse(null);
        Integer rLimit = runContext.render(this.limit).as(Integer.class).orElse(null);
        String bodyFormat = "storage";

        // Build query parameters
        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", rPageId.toString());
        params.put("space-id", rSpaceId.toString());
        params.put("title", rTitle);
        params.put("subtype", rSubType);
        params.put("sort", rSort);
        params.put("cursor", rCursor);
        params.put("limit", rLimit.toString());
        params.put("body-format", bodyFormat);

        String query = params.entrySet().stream()
            .filter(e -> StringUtils.isNotBlank(e.getValue()))
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

        String url = rServerUrl + "/wiki/api/v2/pages";
        if (!query.isEmpty()) {
            url += "?" + query;
        }

        HttpResponse<JsonNode> response = Unirest.get(url)
            .basicAuth(rUsername, rApiToken)
            .header("Accept", "application/json")
            .asJson();

        if (response.getStatus() != 200) {
            logger.error("Confluence request failed: status={} body={}", response.getStatus(), response.getBody());
            throw new IllegalStateException("Confluence API returned " + response.getStatus());
        }

        JSONObject responseJson = new JSONObject(response.getBody().toString());
        JSONArray results = responseJson.getJSONArray("results");

        for (int i = 0; i < results.length(); i++) {
            JSONObject pageInfo = results.getJSONObject(i);
            String pageTitle = pageInfo.optString("title", "Untitled");

            if (pageInfo.has("body")) {
                JSONObject body = pageInfo.getJSONObject("body");
                if (body.has(bodyFormat)) {
                    JSONObject storage = body.getJSONObject(bodyFormat);
                    if (storage.has("value")) {
                        String html = storage.getString("value");
                        String markdown = converter.convert(html);
                        markdownPages.add(new OutputChild(pageTitle, markdown));
                    }
                }
            }
        }

        return Output.builder()
            .children(markdownPages)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of Confluence pages in Markdown format")
        private final List<OutputChild> children;
    }

    @Builder
    @Getter
    public static class OutputChild implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Page title")
        private final String title;

        @Schema(title = "Markdown content")
        private final String markdown;
    }
}