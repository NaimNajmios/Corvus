# Changelog

All notable changes to the **Corvus** project will be documented in this file.

## [1.5.0] - 2026-04-02

### Added
- **Holistic Verification System**: Senior-editor pass for compound claims to catch narrative shifts and cherry-picking.
- **Comparative Audit History**: Multi-selection in History screen to enable claim comparison and delta analysis.
- **History Analytics**: Visual breakdown of belief distributions and check frequency.
- **Harm & Plausibility Indicators**: Immediate visual feedback on potential harm levels (High/Moderate) and claim plausibility in history lists.

### Changed
- **Documentation Restructure**: Migrated all strategic plans, branding, and system prompts into `md/plans/`, `md/branding/`, and `md/prompts/` for better organization.
- **Model Refactor**: Updated `CompositeResult` and `SubClaim` models to support holistic data and harm assessments.
- **History UI**: Integrated `CompareSelectionBar` and swipe-to-undo deletion.

## [1.4.0] - 2026-03-31

### Added
- **Persistent Bookmarking**: Save, search, and manage fact-check results with local persistence and user notes.
- **Advanced Temporal Context UI**: `TemporalContextBanner` for detecting "Zombie Hoaxes" and breaking news signals.
- **Explainable Confidence**: `ConfidenceDropDialog` providing technical rationale for shifts in verification certainty.
- **Haptic Feedback**: Integrated tactile responses for critical UI interactions (Bookmarks, Loading).

### Changed
- **Navigation Architecture**: Expanded `CorvusNavHost` to support Bookmark management and detail views.
- **Result Screen Polish**: Integrated bookmarking triggers and human-review entry points.

## [1.3.0] - 2026-03-30

### Added
- **Multi-Source Credibility Fusion**: Real-time aggregation of MBFC, AdFontes, and NewsGuard data.
- **Educational Loading Experience**: New loading screen with real-time pipeline progress tracking and instructional "Tip Cards".
- **Above-The-Fold Layout**: Refined `ResultScreen` UI guaranteeing visibility of the verdict on entry.
- **Sticky Verdict Context**: Persistent status strip that slides into view during scrolling.
- **MBFC Ratings Integration**: Local asset `mbfc_ratings.csv` for high-speed credibility lookups.

### Changed
- **Source Card UI**: Left-border encoding for credibility tiers (Gold for Primary sources).
- **Confidence Visualization**: Animated confidence bar directly integrated with the verdict word.

## [1.2.0] - 2026-03-26

### Added
- **Comprehensive 11-Stage Pipeline**: Transitioned to a sophisticated orchestration layer including Query Rewriting, Multi-Query Retrieval, and RAG Verification.
- **Temporal Analysis Engine**: Added `TemporalClaimAnalyser` and `SourceTemporalMismatchDetector` to identify outdated evidence and "zombie" hoaxes.
- **Mistral AI & Cohere Integration**: Full support for `mistral-saba` (BM language specialist) and `command-r` (citation specialist).
- **Intelligent Provider Routing**: Claim-type-aware routing logic to select the most suitable LLM for actor/critic roles.
- **Quota Management**: Implemented `CohereQuotaGuard` and token tracking to manage free-tier limits.
- **Query Rewriting**: Added `QueryRewriterUseCase` to decompose claims into optimized search queries.

### Changed
- **Pipeline Orchestration**: Moved from simple pipeline switching to a unified `GeneralFactCheckPipeline` with internal sub-stages.
- **UI Enhancements**: Updated `ResultScreen` and methodology cards to display temporal warnings and routing rationales.

## [1.1.0] - 2026-03-25

### Added
- **Actor-Critic Architecture**: Robust two-pass verification system.
- **Deterministic Grounding**: Algorithmic quote verification to penalize hallucinations.
- **Cerebras Integration**: High-speed inference provider added.
- **Background Persistence**: Enhanced WorkManager integration for long-running checks.

## [1.0.0] - 2026-03-24

### Added
- Initial Corvus MVP.
- Basic pipelines for General, Scientific, and Statistical claims.
- Integrated Tavily and Google Fact Check APIs.
- Custom Monochromatic Precise design system.
- History and Analytics dashboard.
