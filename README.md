# 🦅 Corvus

*See through the noise. Verify the truth.*

Corvus is a high-fidelity fact-checking Android application that orchestrates a **comprehensive 11-stage verification pipeline** to combat misinformation. It decomposes complex claims into verifiable components, utilizing an **Actor-Critic LLM architecture**, **temporal mismatch detection**, **multi-source credibility fusion**, and **algorithmic grounding verification** for unparalleled precision.

---

## 🚀 The Multi-Stage Pipeline

Corvus treats fact-checking as a rigorous investigative process, moving through several specialized layers:

1.  **🧩 Claim Classification** - Identifies claim type (Scientific, Statistical, Historical, etc.) and language (EN, BM, Mixed).
2.  **🔍 Intelligent Query Rewriting** - Expands atomic claims into multiple search-optimized queries for broader evidence retrieval.
3.  **📡 Multi-Query Retrieval** - Orchestrates concurrent searches via Tavily, Wikipedia, and Google Fact Check with automated deduplication.
4.  **📚 Knowledge Base Lookup** - Deep-dives into Wikidata Sparql and Hansard records for person-facts and historical claims.
5.  **⚖️ Source Quality Gating** - Automatically filters sources based on real-time outlet credibility and bias ratings.
6.  **⏳ Temporal Analysis** - Analyzes the "Temporal Profile" of a claim to detect "zombie hoaxes" and "then vs. now" discrepancies.
7.  **🧠 Actor-Critic Synthesis** - A two-pass analysis where an *Actor* drafts a check and a *Critic* audits it, powered by provider-agnostic routing.
8.  **🛠️ Temporal Override** - Algorithmic intervention that flags claims as "MISLEADING" if evidence is significantly outdated for current events.
9.  **🕵️ Algorithmic Grounding** - A deterministic pass that verifies LLM quotes against raw source text, penalizing fabricated citations.
10. **🔬 RAG Verification** - A final verification pass (Retrieval-Augmented Generation) ensuring every fact in the final explanation is grounded.
11. **📊 Token Stewardship** - Granular usage tracking per provider to manage free-tier quotas and optimize costs.

---

## ✨ Core Features

- **🧠 Intelligent Provider Routing** - Automatically selects the best LLM for the task:
    - **Mistral-Saba** for Bahasa Malaysia and Southeast Asian context.
    - **Cohere Command-R** for citation-heavy scientific and quote verification.
    - **Gemini 2.0 / Groq** for high-speed general reasoning and large context synthesis.
- **⚖️ Multi-Source Credibility Fusion** - Aggregates data from **MBFC**, **AdFontes**, and **NewsGuard** with bespoke **Domain Heuristics** to provide a 0-100 composite credibility score and bias assessment for every source.
- **📡 Real-Time Pipeline Transparency** - A stage-aware loading experience that tracks progress through the 11 verification stages in real-time, featuring educational **Tip Cards** to demystify complex analysis.
- **🔖 Persistent Bookmarking System** - Save fact-check results for later reference, search through your audit history, and add custom **User Notes** for personal research tracking.
- **⏳ Advanced Temporal Context** - Real-time detection of **"Zombie Hoaxes"** (recycled misinformation) with visual mismatch banners and detailed source-age breakdowns.
- **📉 Explainable Confidence Shifts** - Interactive dialogs and timeline indicators that explain *why* verification certainty dropped (e.g., source contradiction or grounding failure).
- **🛡️ Deterministic Grounding** - Algorithmic quote verification that matches LLM claims against raw source text, penalizing confidence for fabricated citations.
- **🕒 Temporal Mismatch Detection** - Identifies when current claims are being supported by outdated sources or vice-versa.
- **👁️ Vision Extraction** - Intelligent OCR and image context analysis powered by PaliGemma and Gemini Nano for on-device multi-modal checks.
- **⏳ Confidence Timeline** - A visual journey of verification certainty, showing how confidence shifts as evidence is retrieved and verified.
- **🛡️ Privacy-First ML** - Leverages the LiteRT Engine for local vision and language tasks, keeping sensitive data on-device.

---

## 🎨 Design & Theming

Corvus is built with a custom **Monochromatic Precise** design system, recently updated for **Above-the-Fold Resolution**:

- **Above-the-Fold Contract** - Refined layout ensuring the verdict and core explanation are visible without scrolling on any device.
- **Sticky Verdict Strips** - Persistent verdict context that slides into view when scrolling through dense evidentiary sources.
- **Dynamic Color Palettes** - Choose from `Monochrome`, `Ocean`, `Forest`, `Sunset`, or `Lavender` themes.
- **autorité Typography** - Uses *DM Serif Display* for authoritative headings and *IBM Plex Mono* for information-dense results.
- **Resilient Backgrounding** - Advanced WorkManager integration allows fact-checks to run in the background with persistent status notifications.
- **Share Utility** - A context-aware share bottom sheet (`ShareBottomSheetActivity`) for seamless integration from any app.

---

## 🛠️ Tech Stack

- **Modern UI**: Jetpack Compose with Material 3.
- **Dependency Injection**: Hilt (Dagger).
- **Networking**: Ktor with concurrent request orchestration and resilient routing.
- **Database**: Room for persistent audit logs, history, and analytical insights.
- **LLM Engine**: Provider-agnostic router supporting **Gemini, Groq, Cerebras, Mistral AI, Cohere,** and **OpenRouter**.
- **Background Tasks**: WorkManager & Foreground Services for robust analysis preservation.

---

## ⚙️ Setup

### API Keys
Create a `local.properties` file in the project root. At least one LLM key is required:

```properties
# LLM Providers
GEMINI_API_KEY=your_key
GROQ_API_KEY=your_key
CEREBRAS_API_KEY=your_key
MISTRAL_API_KEY=your_key
COHERE_API_KEY=your_key
OPENROUTER_API_KEY=your_key

# Search & Specialty Sources
TAVILY_API_KEY=your_key
GOOGLE_FACT_CHECK_API_KEY=your_key
```

### Free API Key Sources
| Provider | Free Tier | Sign Up |
|----------|-----------|---------|
| Gemini | 1,500 requests/day | [AI Studio](https://aistudio.google.com/app/apikey) |
| Groq | ~500k tokens/day | [Groq Console](https://console.groq.com/keys) |
| Cerebras | ~1M tokens/day | [Cerebras Cloud](https://cloud.cerebras.ai) |
| Mistral | ~500k tokens/day | [La Plateforme](https://console.mistral.ai) |
| Cohere | 1,000 calls/month | [Cohere Dashboard](https://dashboard.cohere.com) |
| Tavily | 1,000 searches/month | [Tavily](https://tavily.com) |

### Build Requirements
- Android Studio Ladybug+
- JDK 17
- `.\gradlew assembleDebug` to build the app package.

---

## 📜 License

Personal project. All rights reserved.
