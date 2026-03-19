# Corvus

*See through the noise.*

A high-fidelity fact-checking Android application that orchestrates multi-stage verification pipelines to combat misinformation.

## Overview

Corvus moves beyond simple lookups by decomposing complex claims into verifiable components and routing them through specialized pipelines:

- **General Pipeline** - Hybrid search and LLM reasoning for broad claims.
- **Scientific Pipeline** - Powered by PubMed and World Bank data for health and technical claims.
- **Statistical Pipeline** - Direct integration with DOSM and World Bank Open Data.
- **Current Event Pipeline** - Real-time monitoring via GDELT and Junkipedia.
- **Quote Verification** - Cross-referencing Hansard (Parliamentary) and Wikiquote records.

## Core Features

- **Claim Decomposition** - Breaks down compound statements into atomic units for individual testing.
- **Vision Extraction** - Intelligent OCR and image context analysis via PaliGemma and Gemini Nano.
- **Confidence Timeline** - Visual representation of verification certainty over time.
- **Multi-Provider LLM** - Seamless switching between Cerebras, Groq, Gemini, and OpenRouter.
- **On-Device Intelligence** - LiteRT Engine (formerly TFLite) for privacy-preserving local inference.

## Tech Stack

- **Kotlin** & **Jetpack Compose** - Reactive, modern UI with custom monochromatic design system.
- **LiteRT (TFLite)** - On-device model execution for vision and language tasks.
- **Ktor** - Asynchronous networking for high-concurrency pipeline execution.
- **Hilt** - Scalable dependency injection.
- **Room** - Local persistence for fact-check history and analytics.

## Project Structure

```
app/src/main/java/com/najmi/corvus/
├── data/
│   ├── remote/        # 16+ API Clients (Cerebras, DOSM, GDELT, PubMed, etc.)
│   └── repository/    # Unified data access for LLMs and verification sources
├── domain/
│   ├── model/         # Domain-driven models for claims and verdicts
│   └── usecase/       # Orchestration logic for verification pipelines
└── ui/
    ├── compare/       # Multi-source comparison view
    ├── history/       # Persistent audit log of past checks
    └── settings/      # Provider API management (HuggingFace, Groq, etc.)
```

## Setup

### API Keys

Create `local.properties` in the project root. Most providers are optional, but at least one LLM provider is recommended:

```properties
# Primary LLM Providers
GEMINI_API_KEY=your_key
GROQ_API_KEY=your_key
CEREBRAS_API_KEY=your_key
OPENROUTER_API_KEY=your_key

# Search & Specialty Sources
TAVILY_API_KEY=your_key
GOOGLE_FACT_CHECK_API_KEY=your_key
HF_TOKEN=your_huggingface_token_for_gated_models
```

### Build

```bash
./gradlew assembleDebug   # Requires API keys for full functionality
```

## Design

Corvus features a custom **Monochromatic Precise** design system:
- **Primary Color:** High-contrast Black/White/Gray palette.
- **Typography:** DM Serif Display (Authority) + IBM Plex Mono (Information).
- **Aesthetic:** Brutalist edges, glassmorphism, and micro-animations for pipeline state.

## License

Personal project. All rights reserved.
