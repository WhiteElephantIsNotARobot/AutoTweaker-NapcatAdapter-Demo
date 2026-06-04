# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NapCat adapter plugin for AutoTweaker — bridges NapCat QQ chat bot (OneBot 11 protocol) with the AutoTweaker Core API (LLM agent platform). Users interact with an LLM agent via QQ private/group messages.

**This is a plugin, not a standalone application.** It is loaded by AutoTweaker core at runtime via ServiceLoader (SPI). Place the built JAR in `~/.config/autotweaker/plugins/`.

## Build Commands

```bash
./gradlew build          # Build (requires GitHub Packages credentials)
./gradlew distTar        # Create tarball distribution
./gradlew distZip        # Create zip distribution
```

GitHub Packages auth: set `GITHUB_ACTOR` and `GITHUB_TOKEN` env vars, or `gpr.user` / `gpr.key` Gradle properties.

**No tests exist.** There is no `src/test/` directory.

## Architecture

**Lifecycle**: `NapCatAdapter.load()` → `start()` → `stop()`. The adapter is auto-discovered via `@AutoService(Adapter::class)`.

**Core data flow**:
```
QQ → NapCatWsClient (Ktor WebSocket) → MessageBridge → CommandRegistry or SessionManager → AutoTweaker Core API → LLM → response → QQ
```

**Key classes**:

| Class | Role |
|---|---|
| `NapCatAdapter` | Plugin entry point, lifecycle |
| `MessageBridge` | Central message router, context injection, output forwarding |
| `SessionManager` | Per-user session/model/workspace isolation (ConcurrentHashMap keyed by QQ userId) |
| `CommandRegistry` | Parses `/command args`, checks permissions, dispatches to Command implementations |
| `NapCatWsClientImpl` | Ktor WebSocket client — echo-based request/response matching for API calls |
| `PermissionManager` | 4-tier RBAC (ADMIN > OPERATOR > USER) with JSON persistence |
| `QqTool` | LLM-callable tool exposing 21 QQ bot operations |

**Package structure** under `src/main/kotlin/.../napcat/`:
- `api/` — OneBot 11 API interface
- `bridge/` — MessageBridge + SessionManager
- `command/` + `command/commands/` — Command interface, registry, and 12 command implementations
- `config/` — Setting definitions (Host, Port, Token, AdminQQ)
- `model/` — OneBot data classes (events, messages, data) with kotlinx.serialization
- `permission/` — Role enum + PermissionManager
- `tool/` — QqTool (LLM tool)
- `ws/` — WebSocket client interface + Ktor implementation

## Key Patterns

- **SPI discovery**: `@AutoService` + kapt generates `META-INF/services/` files for ServiceLoader
- **Shared classloader**: All plugins share the same URLClassLoader (since v0.1.0-alpha.13), Adapter and Tool directly access each other's types and state
- **Echo-based WS matching**: Incrementing echo counter correlates API requests with responses via `Channel`
- **Context injection**: `MessageBridge.buildMessageWithContext()` wraps messages with XML-like tags (`<session-info>`, `<context>`, `<environment>`) for LLM context
- **Custom serialization**: `MessageSegmentSerializer` handles OneBot's `{"type":"xxx","data":{...}}` envelope format
- **Per-user isolation**: Model/workspace/thinking settings stored per QQ userId in ConcurrentHashMap
- **Tool approval workflow**: LLM tool calls require manual user approval via `/approve` and `/reject` commands

## Configuration

Runtime settings (via AutoTweaker `SettingService`): `Host` (default `localhost`), `Port` (default `3001`), `Token`, `AdminQQ`.

Runtime persistence uses AutoTweaker `JsonStore` API for sessions, user preferences, and permission lists.

## Dependencies

Core dependency: `io.github.autotweaker:api:0.1.0-alpha.20` from GitHub Packages. API documentation is in `doc/`.
