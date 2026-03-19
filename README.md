# Corvus

*See through the noise.*

A fact-checking Android application that cuts through misinformation with precision and authority.

## Overview

Corvus analyzes claims, tweets, and statements using multiple verification pipelines:
- **Google Fact Check API** - Direct fact-check database lookup
- **Tavily Search** - Web search aggregation for corroborating sources
- **LLM Analysis** (Gemini/Groq) - Deep reasoning on claim verification

## Tech Stack

- **Kotlin** with **Jetpack Compose** for modern declarative UI
- **Hilt** for dependency injection
- **Ktor** for network requests
- **Kotlin Serialization** for JSON parsing
- **MVVM** architecture with clean separation of concerns

## Project Structure

```
app/src/main/java/com/najmi/corvus/
├── data/
│   ├── remote/        # API clients (Google, Tavily, Gemini, Groq)
│   └── repository/   # Data repositories
├── di/                # Hilt dependency injection modules
├── domain/
│   ├── model/         # Domain models (Verdict, Source, etc.)
│   └── usecase/       # Business logic use cases
└── ui/
    ├── components/    # Reusable Compose components
    ├── input/         # Input screen
    ├── navigation/    # Navigation graph
    ├── result/        # Result/verdict screens
    ├── theme/         # Material3 theming
    └── viewmodel/     # ViewModels
```

## Setup

### API Keys

Create `local.properties` in the project root with your API keys:

```properties
GOOGLE_FACT_CHECK_API_KEY=your_key
TAVILY_API_KEY=your_key
GEMINI_API_KEY=your_key
GROQ_API_KEY=your_key
```

Or copy `local.properties.example` and fill in the values.

### Build

```bash
./gradlew assembleDebug   # Debug APK
./gradlew assembleRelease # Release APK
```

## Design

Corvus follows a distinct visual identity:
- Dark theme by default with electric chartreuse accent (#C8FF00)
- Typography: DM Serif Display (headlines) + IBM Plex Mono (UI)
- Minimal rounding, high contrast, sharp precision

See `md/corvus-brand-brief.md` for full design specifications.

## License

Personal project. All rights reserved.
