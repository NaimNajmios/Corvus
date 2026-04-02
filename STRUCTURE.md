# Corvus Project Structure

This document provides a high-level index of the Corvus codebase to assist in navigation and contributing.

## 📂 Core Structure

- [app/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/app) - Android application module
  - [src/main/java/com/najmi/corvus/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/app/src/main/java/com/najmi/corvus)
    - [data/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/app/src/main/java/com/najmi/corvus/data) - Repositories, local Room DB, and remote Ktor clients
    - [domain/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/app/src/main/java/com/najmi/corvus/domain) - Models and Use Cases (The "Brain")
      - [usecase/HolisticClaimVerifier.kt](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/app/src/main/java/com/najmi/corvus/domain/usecase/HolisticClaimVerifier.kt) - Narrative shift detection
    - [ui/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/app/src/main/java/com/najmi/corvus/ui) - Jetpack Compose screens and ViewModels
- [md/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/md) - Project metadata and strategic documentation
  - [branding/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/md/branding) - Visual identity and voice guidelines
  - [plans/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/md/plans) - Development roadmaps and feature specs
  - [prompts/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/md/prompts) - System instructions and LLM behavioral prompts
- [icon/](file:///c:/Users/NAIM/AndroidStudioProjects/Corvus/icon) - Original project assets

### 🧱 Domain Layer (`domain/`)
The core logic and business rules of Corvus.
- `model/` - Data classes and Enums (e.g., `LlmProvider`, `Verdict`, `TemporalModels`).
- `usecase/` - The functional brains of the app:
    - `ActorCriticPipeline.kt` - Orchestrates the two-pass analysis.
    - `GeneralFactCheckPipeline.kt` - The unified 11-stage verification engine.
    - `TemporalClaimAnalyser.kt` - Analyzes the timing constraints of a claim.
    - `AlgorithmicGroundingVerifier.kt` - Deterministic quote verification.
    - `QueryRewriterUseCase.kt` - Search query optimization.
- `router/` - LLM health tracking and load-spreading logic.

### 💾 Data Layer (`data/`)
Implementation of repositories and remote API clients.
- `remote/` - API clients for LLMs (Gemini, Groq, Mistral, Cohere) and search (Tavily, Google).
- `repository/` - Unified data access points (e.g., `OutletRatingRepository`, `TokenUsageRepository`).
- `local/` - Room database and DataStore preferences.

### 🍱 UI Layer (`ui/`)
Jetpack Compose screens and components.
- `components/` - Reusable UI widgets (e.g., `PipelineStepIndicator`, `SourceCard`).
- `input/` - Primary claim entry screen.
- `result/` - Rich fact-check visualization screen.
- `history/` - Audit logs and past results.
- `theme/` - Custom design system implementation.

### 👷 Worker Layer (`worker/`)
- `FactCheckWorker.kt` - Background execution management via WorkManager.

---

## 🛠️ Key Files
- `domain/model/LlmProvider.kt` - Registry of all supported LLMs.
- `domain/usecase/ActorCriticProviderSelector.kt` - Intelligent routing logic.
- `domain/model/CorvusResult.kt` - The unified data structure for fact-check results.
