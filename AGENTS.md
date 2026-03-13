# Kestra Confluence Plugin

## What

description = 'Atlassian Confluence plugin for Kestra Exposes 3 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with Atlassian Confluence, allowing orchestration of Atlassian Confluence-based operations as part of data pipelines and automation workflows.

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

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
