package io.kestra.plugin.confluence.pages;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.confluence.AbstractConfluenceTask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List Confluence pages as Markdown",
    description = "Retrieves pages via REST API v2, converts storage HTML to Markdown, and supports filtering by space, page, status, subtype, or title. Default limit is 25 with statuses current and archived; FETCH returns one page, FETCH_ONE forces limit 1, and STORE follows every page and writes the results to internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch Confluence pages from a space as Markdown",
            full = true,
            code = """
                id: fetch-confluence-pages
                namespace: company.team

                tasks:
                  - id: list_pages
                    type: io.kestra.plugin.confluence.pages.List
                    serverUrl: https://your-domain.atlassian.net
                    username: user@example.com
                    apiToken: "{{ secret('CONFLUENCE_API_TOKEN') }}"
                    spaceIds: [294916]
                """
        )
    }
)
public class List extends AbstractConfluenceTask implements RunnableTask<List.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    @Schema(
        title = "Page IDs to filter",
        description = "Filter results by one or more page IDs (comma-separated). Max items: 250."
    )
    private Property<java.util.@Size(max = 250) List<Integer>> pageIds;

    @Schema(
        title = "Space IDs to filter",
        description = "Filter results by one or more Confluence space IDs (comma-separated). Max items: 100."
    )
    private Property<java.util.@Size(max = 100) List<Integer>> spaceIds;

    @Schema(
        title = "Sort order of results",
        description = "Specify sorting of the result set. Valid values: id, -id, created-date, -created-date, modified-date, -modified-date, title, -title."
    )
    @PluginProperty(group = "processing")
    private Property<String> sort;

    @Schema(
        title = "Page status filter",
        description = "Filter pages by status. Defaults to current and archived. Valid values: current, archived, deleted, trashed."
    )
    @Builder.Default
    private Property<java.util.List<String>> status = Property.ofValue(Arrays.asList("current", "archived"));

    @Schema(
        title = "Page title filter",
        description = "Filter results to pages that exactly match this title."
    )
    @PluginProperty(group = "advanced")
    private Property<String> title;

    @Schema(
        title = "Page subtype filter",
        description = "Filter by page subtype. Valid values: live (collaborative draft/live page), page (regular page)."
    )
    @PluginProperty(group = "advanced")
    private Property<String> subType;

    @Schema(
        title = "Pagination cursor",
        description = "Opaque cursor to start from, typically the `nextCursor` output of a previous run. With FETCH or FETCH_ONE one page is returned starting at this cursor; with STORE every page from this cursor onwards is stored."
    )
    @PluginProperty(group = "advanced")
    private Property<String> cursor;

    @Schema(
        title = "Maximum number of results per page",
        description = "Maximum results per request. Default 25; Min 1; Max 250. Overridden to 1 when fetchType is FETCH_ONE."
    )
    @Builder.Default
    private Property<@Min(1) @Max(250) Integer> limit = Property.ofValue(25);

    @Schema(
        title = "Fetch type",
        description = "Determines how results are returned. FETCH returns one page of results as a list, FETCH_ONE returns the first result, STORE follows every page and writes them to internal storage, returning a URI. Default: FETCH."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        java.util.List<OutputChild> markdownPages = new ArrayList<>();

        String apiBaseUrl = buildApiBaseUrl(runContext);

        java.util.List<Integer> rSpaceIds = runContext.render(this.spaceIds).asList(Integer.class);
        java.util.List<Integer> rPageIds = runContext.render(this.pageIds).asList(Integer.class);
        String rTitle = runContext.render(this.title).as(String.class).orElse(null);
        String rSubType = runContext.render(this.subType).as(String.class).orElse(null);
        java.util.List<String> rStatus = runContext.render(this.status).asList(String.class);
        String rSort = runContext.render(this.sort).as(String.class).orElse(null);
        String rCursor = runContext.render(this.cursor).as(String.class).orElse(null);
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        String bodyFormat = "storage";
        Integer rLimit;
        if (rFetchType == FetchType.FETCH_ONE) {
            rLimit = 1;
        } else {
            rLimit = runContext.render(this.limit).as(Integer.class).orElse(25);
        }

        Map<String, String> params = new LinkedHashMap<>();
        if (rSpaceIds != null) {
            String spaceIdsParam = rSpaceIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            params.put("space-id", spaceIdsParam);
        }
        if (rPageIds != null) {
            String pageIdsParam = rPageIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            params.put("id", pageIdsParam);
        }
        if (rTitle != null) {
            params.put("title", rTitle);
        }
        if (rSubType != null) {
            params.put("subtype", rSubType);
        }
        if (rSort != null) {
            params.put("sort", rSort);
        }
        params.put("status", String.join(",", rStatus));
        params.put("limit", rLimit.toString());
        params.put("body-format", bodyFormat);

        ObjectMapper mapper = JacksonMapper.ofJson();

        File tempFile = null;
        FileOutputStream fileWriter = null;

        if (rFetchType == FetchType.STORE) {
            tempFile = runContext.workingDir().createTempFile(".ion").toFile();
            fileWriter = new FileOutputStream(tempFile);
        }

        var nextCursor = rCursor;
        try {
            do {
                if (nextCursor != null) {
                    params.put("cursor", nextCursor);
                }

                var query = params.entrySet().stream()
                    .filter(e -> StringUtils.isNotBlank(e.getValue()))
                    .map(e -> "%s=%s".formatted(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8), URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)))
                    .collect(Collectors.joining("&"));
                var url = query.isEmpty() ? "%s/pages".formatted(apiBaseUrl) : "%s/pages?%s".formatted(apiBaseUrl, query);

                var response = send(runContext, "GET", URI.create(url), null);

                var responseJson = mapper.readTree(response.getBody());
                var results = responseJson.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode pageInfo : results) {
                        var page = convertPage(pageInfo, bodyFormat, converter);
                        if (page != null) {
                            switch (rFetchType) {
                                case FETCH, FETCH_ONE:
                                    markdownPages.add(page);
                                    break;
                                case STORE:
                                    FileSerde.write(fileWriter, page);
                                    break;
                            }
                        }
                    }
                }

                var previousCursor = nextCursor;
                nextCursor = nextCursor(responseJson);
                if (rFetchType == FetchType.STORE && nextCursor != null && nextCursor.equals(previousCursor)) {
                    throw new IllegalStateException("Confluence returned the pagination cursor '%s' twice, stopping to avoid an infinite loop".formatted(nextCursor));
                }
            } while (rFetchType == FetchType.STORE && nextCursor != null);
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }

        var outputBuilder = Output.builder().nextCursor(nextCursor);
        if (rFetchType == FetchType.STORE) {
            URI storageUri = runContext.storage().putFile(tempFile);
            outputBuilder.uri(storageUri);
        } else {
            outputBuilder.children(markdownPages);
        }

        return outputBuilder.build();
    }

    // `_links.next` is a site-relative URL, so only its cursor is kept and a custom `apiPath` is still used
    private static String nextCursor(JsonNode responseJson) {
        var next = responseJson.path("_links").path("next").asText(null);
        var query = next == null ? null : URI.create(next).getQuery();
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
            .filter(pair -> pair.startsWith("cursor="))
            .map(pair -> StringUtils.trimToNull(pair.substring("cursor=".length())))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private OutputChild convertPage(JsonNode pageInfo, String bodyFormat, FlexmarkHtmlConverter converter) {
        JsonNode titleNode = pageInfo.get("title");

        Map<String, Object> rawMap = MAPPER.convertValue(pageInfo, Map.class);
        Map<String, Object> versionInfo = (Map<String, Object>) rawMap.get("version");

        String pageTitle = (titleNode != null && !titleNode.isNull()) ? titleNode.asText() : "Untitled";

        JsonNode valueNode = pageInfo.path("body").path(bodyFormat).path("value");

        if (!valueNode.isMissingNode() && valueNode.isTextual()) {
            String html = valueNode.asText();
            String markdown = converter.convert(html);
            return new OutputChild(pageTitle, markdown, versionInfo, rawMap);
        }
        return null;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Confluence pages",
            description = "List of fetched pages with title, Markdown body, version info, and raw payload."
        )
        private final java.util.List<OutputChild> children;

        @Schema(
            title = "Stored results URI",
            description = "Kestra internal storage URI holding serialized pages when fetchType is STORE."
        )
        private final URI uri;

        @Schema(
            title = "Next page cursor",
            description = "Cursor for the next page of results, to pass back as `cursor`. Null when there are no more pages. Always null for STORE, which follows every page."
        )
        private final String nextCursor;
    }

    @Builder
    @Getter
    public static class OutputChild implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Page title")
        @PluginProperty(group = "advanced")
        private final String title;

        @Schema(title = "Markdown content")
        private final String markdown;

        @Schema(
            title = "Version information",
            description = "Version map returned by Confluence, including version number and message."
        )
        private final Map<String, Object> versionInfo;

        @Schema(
            title = "Raw response from Confluence",
            description = "Full page payload returned by Confluence, including metadata and body."
        )
        private final Map<String, Object> rawResponse;
    }
}
