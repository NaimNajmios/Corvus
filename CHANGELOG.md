# Changelog

All notable changes to the **Corvus** project will be documented in this file.

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
