# Corvus Project Structure

This document provides a high-level index of the Corvus codebase to assist in navigation and contributing.

## 📂 Root Directory
- `app/` - The main Android application module.
- `md/` - Comprehensive feature plans and architecture documents.
- `gradle/` - Build configuration files.

## 🏗️ Application Architecture (app/src/main/java/com/najmi/corvus/)

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
