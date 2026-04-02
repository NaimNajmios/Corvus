# README & Developer Onboarding Prompt

> **Usage:** Attach this file to your coding assistant session. Name the project or module and provide available context (tech stack, purpose, setup steps, architecture notes) and say e.g. *"Write a README and onboarding guide for Vulgaris"*. The assistant will follow the framework below.

---

## 🎯 Objective

You are a senior engineer and technical writer. When I describe or provide context about a **project, module, or library**, produce a thorough, opinionated README and developer onboarding guide — covering setup, architecture, key decisions, contribution workflow, and everything a new developer (or future-you) needs to be productive from day one.

---

## 📥 Input

I will provide one or more of the following:
- Project or module name (required)
- Purpose / what it does (required — brief is fine)
- Tech stack (optional — infer where possible)
- Architecture overview or key modules (optional)
- Setup or build steps (optional — generate best-guess if omitted, flag assumptions)
- Environment variables or secrets needed (optional)
- Known gotchas or non-obvious setup steps (optional)
- Target audience: solo/internal team/public open source (optional — default: internal team)

---

## 🔍 What Makes a Good README & Onboarding Doc

Before writing, plan for:

### 1. First Impression (top of README)
- Clear one-line description — what it is and who it's for
- Status badge(s) if applicable (build, version, license)
- A screenshot, demo GIF, or architecture diagram if visual context helps
- The single most important thing a reader needs to know upfront

### 2. Getting Started — Zero to Running
- Prerequisites listed explicitly (versions matter)
- Clone, configure, and run steps that actually work
- Environment variable setup with `.env.example` scaffold
- Common first-run errors and how to resolve them

### 3. Architecture Overview
- What the major modules/packages are and what each owns
- Data flow: how a request/action moves through the system
- Key design decisions summarized (link to ADRs if they exist)
- Dependency diagram or layered architecture description

### 4. Module / Feature Reference
- What each significant module, screen, or service does
- Entry points for reading the code
- Where to find things (routing, state, models, etc.)

### 5. Configuration & Environment
- All environment variables documented with type, default, and description
- Feature flags, build variants, or flavors explained
- External service dependencies (APIs, databases) and how to configure them for local dev

### 6. Development Workflow
- Branch strategy and naming convention
- How to run tests (unit, integration, UI)
- Linting and code style enforcement
- How to build a release / generate an artifact

### 7. Contribution Guide (for team or open source)
- How to pick up work (issue labels, project board)
- PR process and review expectations
- Commit message convention
- How to add a new feature / screen / module (step-by-step)

### 8. Troubleshooting & Known Issues
- Common errors with solutions
- Non-obvious environment issues
- Links to relevant issues or workarounds

---

## 📤 Output Format

Produce two documents:

---

### Document 1: `README.md`

```markdown
# [Project Name]

> [One-line description — what it is, what it does, who it's for.]

[Optional: build/version/license badges]

[Optional: screenshot or architecture diagram placeholder]

---

## Overview

[2–3 paragraphs. What problem does this solve? What is the core user value or
developer value? What makes this project's approach notable or different?]

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| [e.g., UI] | [e.g., Jetpack Compose] |
| [e.g., State] | [e.g., ViewModel + StateFlow] |
| [e.g., Networking] | [e.g., Ktor Client] |
| [e.g., DI] | [e.g., Hilt] |
| [e.g., AI] | [e.g., Gemini API, AICore] |

---

## Getting Started

### Prerequisites

- [e.g., Android Studio Hedgehog or later]
- [e.g., JDK 17]
- [e.g., Android SDK 35]
- [e.g., API keys for X, Y]

### Setup

1. Clone the repository
   ```bash
   git clone https://github.com/[username]/[repo].git
   cd [repo]
   ```

2. Configure environment
   ```bash
   cp .env.example local.properties
   # Edit local.properties and fill in required values
   ```

3. Build and run
   ```bash
   # Android
   ./gradlew assembleDebug
   # or open in Android Studio and run
   ```

### Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `API_KEY_X` | ✅ | [what it's for] | `sk-...` |
| `BASE_URL` | ✅ | [service URL] | `https://api.example.com` |
| `FEATURE_FLAG_Y` | ❌ | [what it enables] | `true` |

---

## Architecture

[2–3 sentence summary of the architectural approach — e.g., MVVM + UDF, clean
architecture layers, on-device vs cloud split.]

```
[ASCII or text diagram of layers/modules]

┌─────────────────────────┐
│         UI Layer        │  Jetpack Compose screens
├─────────────────────────┤
│      ViewModel Layer    │  State holders, UDF
├─────────────────────────┤
│       Domain Layer      │  Use cases, business logic
├─────────────────────────┤
│        Data Layer       │  Repositories, API clients, local DB
└─────────────────────────┘
```

### Module Overview

| Module / Package | Responsibility |
|-----------------|----------------|
| `:feature/[x]` | |
| `:core/network` | |
| `:core/data` | |

[Link to docs/adr/ if ADRs exist]

---

## Development

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Specific module
./gradlew :[module]:test
```

### Code Style

[Describe linting setup — ktlint, detekt, etc. and how to run it]

```bash
./gradlew ktlintCheck
./gradlew detekt
```

### Build Variants

| Variant | Description |
|---------|-------------|
| `debug` | [local dev, logging enabled] |
| `release` | [optimized, ProGuard enabled] |

---

## Project Structure

```
📁 [project-root]/
├── 📁 app/                    [entry point, DI setup]
├── 📁 feature/                [feature modules]
│   └── 📁 [feature-name]/
├── 📁 core/                   [shared utilities]
│   ├── 📁 network/
│   ├── 📁 data/
│   └── 📁 ui/
├── 📁 docs/
│   └── 📁 adr/
└── 📁 buildSrc/ or gradle/    [build logic]
```

---

## Troubleshooting

**[Common error message or symptom]**
[Cause and fix]

**[Another common issue]**
[Cause and fix]

---

## License

[License name] — see [LICENSE](LICENSE) for details.
```

---

### Document 2: `docs/ONBOARDING.md`

```markdown
# Developer Onboarding Guide — [Project Name]

> This guide gets you from zero to productive contributor.
> Estimated time to complete: [X hours / X days]

---

## Before You Start

Read these first (in order):
1. [`README.md`](../README.md) — project overview and setup
2. This document — architecture deep-dive and workflow
3. [`docs/adr/`](adr/) — key technical decisions (if exists)

---

## Understanding the Codebase

### How a [core user action] works end-to-end

Walk through the most important or representative flow in the app, tracing it
from the UI trigger all the way through to the data layer and back.

```
[Action] (e.g., user taps "Generate Post")
  │
  ▼
[Screen Composable] triggers event via callback
  │
  ▼
[ViewModel] receives event, calls use case
  │
  ▼
[UseCase] orchestrates repository calls
  │
  ▼
[Repository] fetches from API or local DB
  │
  ▼
State flows back up → ViewModel → UiState → Composable recompose
```

---

## Key Concepts & Patterns

### [Pattern 1 — e.g., UiState modeling]
[Explain how and why this pattern is used in this project specifically.
Include a short code example or point to a reference implementation.]

### [Pattern 2 — e.g., one-shot events via Channel]
[...]

### [Pattern 3 — e.g., on-device vs cloud AI routing]
[...]

---

## Where to Find Things

| I want to... | Look in... |
|-------------|-----------|
| Add a new screen | `feature/[name]/` — copy from `feature/[reference-screen]/` |
| Add a new API call | `core/network/` → add to relevant service interface |
| Add a local DB table | `core/data/db/` → add Entity + DAO + migration |
| Change app navigation | `app/navigation/` |
| Add a new AI prompt | `core/ai/prompts/` (or equivalent) |
| Change build config | `build.gradle.kts` + `gradle/libs.versions.toml` |

---

## How to Add a New Feature

Step-by-step guide for the most common contribution type:

1. **Create the feature module** (if multi-module)
   ```bash
   # [specific command or manual steps]
   ```

2. **Add the UI state model** in `[FeatureName]UiState.kt`

3. **Create the ViewModel** following `[ReferenceViewModel]` as a template

4. **Create the Composable screen** — start stateless, wire ViewModel last

5. **Add navigation** in `[NavigationFile]`

6. **Write unit tests** for the ViewModel

7. **Add Compose previews** for all UI states

8. **Open a PR** following the PR checklist below

---

## PR Checklist

Before requesting review, confirm:

- [ ] Feature works end-to-end in debug build
- [ ] Unit tests added or updated
- [ ] No new lint or detekt warnings
- [ ] Compose previews added for new screens
- [ ] No hardcoded strings (use string resources)
- [ ] No sensitive data in logs
- [ ] `CHANGELOG.md` updated (if user-facing change)
- [ ] ADR written if a significant architectural decision was made

---

## Runbooks

### How to cut a release
[Steps]

### How to update an API key or secret
[Steps]

### How to add a new build flavor or variant
[Steps]

---

## Useful Commands Cheatsheet

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Test
./gradlew test
./gradlew connectedAndroidTest

# Lint
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew detekt

# Clean
./gradlew clean

# Dependency updates check
./gradlew dependencyUpdates
```

---

## Who to Ask

| Area | Contact / Resource |
|------|-------------------|
| Architecture questions | [name or link to ADRs] |
| Design / UX | [name or Figma link] |
| API access / keys | [name or doc link] |
| CI / build issues | [name or CI dashboard link] |

---

## Glossary

| Term | Definition |
|------|-----------|
| [Project-specific term] | [What it means in this codebase] |

---

*Last updated: [today] — update this doc when setup steps change.*
```

---

## 🧠 Constraints & Preferences

- Assume the reader is a competent developer but has **zero context** about this project
- Prefer showing actual commands and paths over describing them in prose
- Flag any section where I haven't provided enough information — generate a best-guess placeholder and mark it with `[TODO: verify this]`
- Match depth to audience: internal solo project needs less ceremony than a public library or team project
- Keep README scannable — headers, tables, and code blocks over prose paragraphs
- Onboarding doc should prioritize the "how does this actually work" walk-through over setup (README handles setup)
- Both documents should be Markdown-ready for GitHub rendering

---

*End of prompt — describe your project to begin.*
