# How to use the Confluence plugin

Create, list, and update Confluence pages from Kestra flows using Markdown content.

## Authentication

Set `serverUrl` (e.g. `https://your-domain.atlassian.net`), `username` (your Atlassian account email), and `apiToken` (your Atlassian API token) on each task. For self-hosted Confluence, override `apiPath` — it defaults to `/wiki/api/v2` for Cloud. Store credentials in [secrets](https://kestra.io/docs/concepts/secret) and set them on each task. Use the optional `options` property to configure the HTTP client (timeouts, proxy, SSL).

## Tasks

`pages.Create` creates a new page — set `spaceId`, `title`, and `markdown` for the page body. Use `parentId` to nest it under an existing page, and `status` to create as `current` (published) or `draft`.

`pages.List` retrieves pages as Markdown. Filter by `spaceIds`, `pageIds`, or `title`. Control output with `fetchType`: `FETCH` returns one page of results (pass the `nextCursor` output back as `cursor` for the next one), `FETCH_ONE` returns the first match, and `STORE` follows every page and writes the results to internal storage.

`pages.Update` modifies an existing page — set `pageId`, `title`, `markdown`, `status`, and `versionInfo` (a map with `number` and `message` describing the new version).
