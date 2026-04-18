# Kestra Confluence Plugin

## What

- Provides plugin components under `io.kestra.plugin.confluence`.
- Includes classes such as `List`, `Create`, `Update`.

## Why

- This plugin integrates Kestra with Atlassian Confluence.
- It provides tasks that connect Kestra flows to Atlassian Confluence for creating, updating, and retrieving page content.

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
