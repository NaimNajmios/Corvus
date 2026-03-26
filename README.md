# 🦅 Corvus

*See through the noise. Verify the truth.*

Corvus is a high-fidelity fact-checking Android application that orchestrates multi-stage verification pipelines to combat misinformation. It decomposes complex claims into verifiable components, utilizing an **Actor-Critic LLM architecture** and **deterministic grounding verification** for unparalleled precision.

---

## 🚀 Overview

Corvus moves beyond simple database lookups. It treats fact-checking as an investigative process, utilizing a suite of specialized pipelines:

- **🌍 General Pipeline** - Hybrid web search and LLM-driven reasoning for broad social and political claims.
- **🧬 Scientific Pipeline** - Technical verification powered by PubMed and World Bank health datasets.
- **📊 Statistical Pipeline** - Direct integration with DOSM (Department of Statistics Malaysia) and World Bank Open Data.
- **🔥 Current Events** - Real-time monitoring of breaking news via GDELT and viral hoax tracking via Junkipedia.
- **🎙️ Quote Verification** - Cross-referencing against Hansard (Parliamentary records) and Wikiquote.

---

## ✨ Core Features

- **🧩 Claim Decomposition** - Automatically breaks compound or "slanted" statements into atomic units for individual multi-pipeline testing.
- **🧠 Actor-Critic Reasoning** - A robust two-pass analysis where an *Actor* drafts a preliminary fact-check and a *Critic* rigorously audits it for citations and accuracy.
- **⚖️ Deterministic Grounding** - Algorithmic quote verification that matches LLM claims against raw source text, penalizing confidence for fabricated citations.
- **👁️ Vision Extraction** - Intelligent OCR and image context analysis powered by PaliGemma and Gemini Nano for on-device multi-modal checks.
- **⏳ Confidence Timeline** - A visual journey of verification certainty, showing how confidence shifts as evidence is retrieved and verified.
- **🛡️ Privacy-First ML** - Leverages the LiteRT Engine (formerly TFLite) for local vision and language tasks, keeping your data on-device.

---

## 🎨 Premium Design & Theming

Corvus is built with a custom **Monochromatic Precise** design system, now expanded with dynamic palettes to match your style:

- **Dynamic Color Palettes** - Choose from `Monochrome`, `Ocean`, `Forest`, `Sunset`, or `Lavender` themes.
- **autorité Typography** - Uses *DM Serif Display* for authoritative headings and *IBM Plex Mono* for information-dense results.
- **Interactive Results** - Rich, expandable cards with micro-animations, glassmorphism, and smooth transitions.
- **Resilient Backgrounding** - Advanced WorkManager integration allows fact-checks to run in the background with persistent status notifications.
- **Share Utility** - A context-aware share bottom sheet (`ShareBottomSheetActivity`) for seamless integration with other apps.
- **Thread-Safe Architecture** - Fully asynchronous orchestration using Kotlin Coroutines and Ktor, ensuring zero UI jank during heavy analysis.

---

## 🛠️ Tech Stack

- **Modern UI**: Jetpack Compose with Material 3 integration.
- **Dependency Injection**: Hilt (Dagger) for scalable architecture.
- **Networking**: Ktor for concurrent, high-performance API orchestration.
- **Database**: Room for persistent audit logs, history, and analytical insights.
- **Background Tasks**: WorkManager and Foreground Services for robust, backgrounded analysis and persistent status notifications.
- **Analytics**: Custom dashboard for visualizing fact-check distribution and trends over time.

---

## ⚙️ Setup

### API Keys
Create a `local.properties` file in the project root. While most providers are optional, at least one LLM key is required for analysis:

```properties
# Primary LLM Providers
GEMINI_API_KEY=your_key
GROQ_API_KEY=your_key
CEREBRAS_API_KEY=your_key
OPENROUTER_API_KEY=your_key
MISTRAL_API_KEY=your_key
COHERE_API_KEY=your_key

# Search & Specialty Sources
TAVILY_API_KEY=your_key
GOOGLE_FACT_CHECK_API_KEY=your_key
HF_TOKEN=your_huggingface_token_for_gated_models
```

### Free API Key Sources
| Provider | Free Tier | Sign Up |
|----------|-----------|---------|
| Gemini | 1,500 requests/day | https://aistudio.google.com/app/apikey |
| Groq | ~500k tokens/day | https://console.groq.com/keys |
| Cerebras | ~1M tokens/day | https://cloud.cerebras.ai |
| OpenRouter | Model-dependent | https://openrouter.ai/keys |
| Mistral | ~500k tokens/day | https://console.mistral.ai |
| Cohere | 1,000 calls/month | https://dashboard.cohere.com |
| Tavily | 1,000 searches/month | https://tavily.com |
| Google Fact Check | Varies | https://developers.google.com/fact-check/tools-api |

### Build Requirements
- Android Studio Ladybug+
- JDK 17
- `.\gradlew assembleDebug` to build the app package.

---

## 📜 License

Personal project. All rights reserved.
