package io.kestra.plugin.confluence.pages;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.plugin.confluence.AbstractConfluenceTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch Confluence pages",
    description = "Retrieves the content of one or more pages from Confluence as Markdown."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch Confluence pages as Markdown",
            code = """
                id: fetch-confluence-pages
                namespace: company.team

                tasks:
                    - id: 1
                      type: io.kestra.plugin.confluence.List
                      serverUrl: https://your-domain.atlassian.net
                      username: user@example.com
                      apiToken: "{{ secret('CONFLUENCE_API_TOKEN') }}"
            """
        )
    }
)
public class List extends AbstractConfluenceTask implements RunnableTask<List.Output> {
    @Schema(
        title = "Page IDs to filter.",
        description = "Filter results by one or more page IDs. Multiple IDs can be specified as a comma-separated list. Max items: 250."
    )
    private Property<java.util.@Size(max = 250) List<Integer>> pageIds;

    @Schema(
        title = "Space IDs to filter.",
        description = "Filter results by one or more Confluence space IDs. Multiple IDs can be specified as a comma-separated list. Max items: 100."
    )
    private Property<java.util.@Size(max = 100) List<Integer>> spaceIds;

    @Schema(
        title = "Sort order of results.",
        description = "Specify sorting of the result set. Valid values: id, -id, created-date, -created-date, modified-date, -modified-date, title, -title."
    )
    private Property<String> sort;

    @Schema(
        title = "Page status filter.",
        description = "Filter the results to pages based on their status. By default, 'current' and 'archived' are used. Valid values: current, archived, deleted, trashed."
    )
    @Builder.Default
    private Property<java.util.List<String>> status = Property.ofValue(Arrays.asList("current", "archived"));

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
    @Builder.Default
    private Property<@Min(1) @Max(250) Integer> limit = Property.ofValue(25);

    @Schema(
        title = "Fetch type",
        description = "Type of fetch operation. Valid values: FETCH, FETCH_ONE, STORE."
    )
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        java.util.List<OutputChild> markdownPages = new ArrayList<>();

        String rServerUrl = runContext.render(this.serverUrl).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("serverUrl is required"));
        String rUsername = runContext.render(this.username).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("username is required"));
        String rApiToken = runContext.render(this.apiToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("apiToken is required"));

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
            params.put("space-ids", spaceIdsParam);
        }
        if (rPageIds != null) {
            String pageIdsParam = rPageIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            params.put("page-ids", pageIdsParam);
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
        if (rCursor != null) {
            params.put("cursor", rCursor);
        }
        params.put("status", String.join(",", rStatus));
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

        String auth = rUsername + ":" + rApiToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic " + encodedAuth)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("Confluence request failed: status={} body={}", response.statusCode(), response.body());
            throw new IllegalStateException("Confluence API returned " + response.statusCode());
        }

        ObjectMapper mapper = JacksonMapper.ofJson();
        JsonNode responseJson = mapper.readTree(response.body());
        JsonNode results = responseJson.get("results");

        File tempFile = null;
        FileOutputStream fileWriter = null;

        if (rFetchType == FetchType.STORE) {
            tempFile = runContext.workingDir().createTempFile(".ion").toFile();
            fileWriter = new FileOutputStream(tempFile);
        }

        if (results != null && results.isArray()) {
            for (JsonNode pageInfo : results) {
                OutputChild page = convertPage(pageInfo, bodyFormat, converter);
                if (page != null) {
                    switch (rFetchType) {
                        case FETCH, FETCH_ONE:
                            markdownPages.add(page);
                            break;
                        case STORE:
                            try {
                                FileSerde.write(fileWriter, page);
                            } catch (Exception e) {
                                logger.error("Failed to write page to file: {}", e.getMessage());
                                fileWriter.close();
                            }
                            break;
                    }
                }
            }
        }

        Output.OutputBuilder outputBuilder = Output.builder();
        if (rFetchType == FetchType.STORE) {
            fileWriter.close();
            URI storageUri = runContext.storage().putFile(tempFile);
            outputBuilder.uri(storageUri);
        }
        else{
            outputBuilder.children(markdownPages);
        }

        return outputBuilder.build();
    }

    private OutputChild convertPage(JsonNode pageInfo, String bodyFormat, FlexmarkHtmlConverter converter) {
        JsonNode titleNode = pageInfo.get("title");
        String pageTitle = (titleNode != null && !titleNode.isNull()) ? titleNode.asText() : "Untitled";

        JsonNode valueNode = pageInfo.path("body").path(bodyFormat).path("value");

        if (!valueNode.isMissingNode() && valueNode.isTextual()) {
            String html = valueNode.asText();
            String markdown = converter.convert(html);
            return new OutputChild(pageTitle, markdown);
        }
        return null;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of Confluence pages in Markdown format")
        private final java.util.List<OutputChild> children;

        private final URI uri;
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