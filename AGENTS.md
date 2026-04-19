# Kestra Confluence Plugin

## What

- Provides plugin components under `io.kestra.plugin.confluence`.
- Includes classes such as `List`, `Create`, `Update`.

## Why

- What user problem does this solve? Teams need to connect Kestra flows to Atlassian Confluence for creating, updating, and retrieving page content from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Atlassian Confluence steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Atlassian Confluence.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `confluence`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.confluence.pages.Create`
- `io.kestra.plugin.confluence.pages.List`
- `io.kestra.plugin.confluence.pages.Update`

### Project Structure

```
plugin-confluence/
├── src/main/java/io/kestra/plugin/confluence/pages/
├── src/test/java/io/kestra/plugin/confluence/pages/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
