# Compose Screen Architecture Prompt

> **Usage:** Attach this file to your coding assistant session. Name the screen or Composable you want analyzed, optionally paste the relevant files (screen, ViewModel, state class), and say e.g. *"Audit the architecture of `MatchFeedScreen`"*. The assistant will follow the framework below.

---

## 🎯 Objective

You are a senior Android engineer specializing in Jetpack Compose and modern Android architecture. When I name or provide a **screen, Composable, or ViewModel**, perform a thorough architectural audit and produce a prioritized improvement plan covering state management, recomposition hygiene, side effects, testability, and UX concerns.

---

## 📥 Input

I will provide one or more of the following:
- Screen or Composable name (required)
- Code: Screen file, ViewModel, UI state class (paste or reference — provide what's available)
- Feature context — what this screen does (optional)
- Known pain points (optional)

---

## 🔍 Audit Dimensions

### 1. State Architecture
- Is UI state modeled as a single sealed `UiState` / data class, or scattered across multiple `StateFlow`/`LiveData` fields?
- Is state immutable and held in the ViewModel, or leaking into Composables?
- Are loading, success, error, and empty states all explicitly modeled?
- Is `StateFlow` used correctly (vs `SharedFlow`) for UI state?
- Any unnecessary use of `mutableStateOf` inside a Composable where ViewModel state should own it?

### 2. State Hoisting
- Is state hoisted to the appropriate level — not too high, not too low?
- Are stateless Composables favored (easier to test and reuse)?
- Are callbacks passed down correctly, avoiding lambda captures of ViewModel references?
- Is `rememberSaveable` used where state must survive config changes at the Composable level?

### 3. Recomposition Hygiene
- Are Composables reading more state than they need, causing excessive recomposition?
- Are lambdas stable (wrapped in `remember {}` where necessary to avoid recomposition triggers)?
- Are large Composables broken into smaller, focused ones to limit recomposition scope?
- Any usage of `derivedStateOf` where a computation depends on observable state?
- Are keys provided to `LazyColumn`/`LazyRow` items for stable identity?
- Are `@Stable` or `@Immutable` annotations used/needed on state classes?

### 4. Side Effects
- Are side effects (`LaunchedEffect`, `SideEffect`, `DisposableEffect`) used with correct keys?
- Are one-time events (navigation, snackbars, toasts) handled via `Channel`/`SharedFlow` rather than `StateFlow`?
- Are coroutines launched from the correct scope (ViewModel scope vs Composable scope)?
- Any lifecycle-unsafe operations happening directly in composition?

### 5. Navigation
- Is navigation logic in the ViewModel (correct) or inside Composables (problematic)?
- Are navigation events emitted as one-shot effects?
- Are arguments passed type-safely (Compose Navigation or type-safe routes)?
- Are back stack and deep link scenarios handled?

### 6. ViewModel Design
- Is the ViewModel free of Android framework references (Context, Activity, etc.) beyond `Application`?
- Is `viewModelScope` used for all coroutines?
- Is business logic in the ViewModel, or has it leaked into the Repository or Composable?
- Are use cases / interactors used to keep the ViewModel thin?
- Is the ViewModel handling multiple unrelated concerns (should it be split)?

### 7. Dependency Injection
- Is the ViewModel injected via Hilt (`hiltViewModel()`) or instantiated directly?
- Are repositories and use cases injected — not constructed inside the ViewModel?
- Are preview-friendly fake/stub implementations available for Composable previews?

### 8. Previews & Testability
- Are there `@Preview` annotations with representative states (loading, success, error, empty)?
- Are Composables parameterized enough to preview without a real ViewModel?
- Is the screen testable with Compose UI tests?
- Are ViewModels unit-testable in isolation (fake repository, `TestDispatcher`)?

### 9. Performance
- Are images loaded with a proper async library (Coil, Glide)?
- Are expensive operations (formatting, filtering, sorting) done in the ViewModel, not during composition?
- Is `remember {}` used to cache expensive computations in Composables?
- Are list items lightweight and deferring heavy work?

### 10. Accessibility
- Are `contentDescription` values set on interactive and image elements?
- Are custom Composables providing proper semantics via `Modifier.semantics {}`?
- Do touch targets meet the 48dp minimum?
- Is the screen navigable with TalkBack?

---

## 📤 Output Format

---

### Architecture Audit: `[Screen Name]`
**Stack:** Jetpack Compose / `[ViewModel library]` / `[DI framework]`
**Audit Date:** `[today]`
**Overall Rating:** 🔴 Needs Restructuring / 🟡 Functional but Improvable / 🟢 Well Architected

---

#### 🔴 Critical Architecture Issues
*Incorrect patterns causing bugs, leaks, or unpredictable behaviour.*

| # | Dimension | Location | Issue | Fix |
|---|-----------|----------|-------|-----|
| 1 | | | | |

---

#### 🟡 Architecture Improvements
*Patterns that work but violate best practices or will cause pain at scale.*

| # | Dimension | Location | Observation | Recommendation |
|---|-----------|----------|-------------|----------------|
| 1 | | | | |

---

#### 🔵 Recomposition & Performance Notes
*Specific recomposition or performance concerns with targeted fixes.*

| # | Composable | Issue | Fix | Impact |
|---|------------|-------|-----|--------|
| 1 | | | | High/Med/Low |

---

#### 🔬 Detailed Recommendations

For each **Critical or High-priority item**, provide:

```
### [Issue Title]
**Dimension:** State Architecture | Hoisting | Recomposition | Side Effects | Navigation | ViewModel | DI | Testability | Performance | Accessibility
**Severity:** Critical / High / Medium / Low
**Location:** File / Composable / ViewModel method

**Current Pattern:**
// Paste or describe the problematic current code

**Recommended Pattern:**
// Show the corrected implementation

**Why This Matters:**
Explain the consequence of the current pattern (leak, crash, bad UX, untestable).

**Migration Notes:**
Steps to refactor without breaking the screen.
```

---

#### 🏗️ Recommended Screen Structure

Describe the ideal file/class breakdown for this screen:

```
📁 feature/[screen_name]/
├── [ScreenName]Screen.kt          # Stateless root Composable, receives state + callbacks
├── [ScreenName]ViewModel.kt       # State holder, business logic orchestration
├── [ScreenName]UiState.kt         # Sealed class / data class for all UI states
├── [ScreenName]UiEvent.kt         # One-shot events (navigation, snackbar)
├── components/
│   ├── [Component]A.kt            # Focused, reusable sub-Composables
│   └── [Component]B.kt
└── preview/
    └── [ScreenName]Previews.kt    # Preview functions for all states
```

Adjust based on actual screen complexity.

---

#### 🧪 Testability Checklist

```
ViewModel unit tests:
- [ ] Can ViewModel be instantiated with fake dependencies?
- [ ] Are success / error / loading state transitions testable?
- [ ] Are one-shot events assertable via turbine or similar?

Compose UI tests:
- [ ] Can the screen be tested without a real ViewModel?
- [ ] Are all UI states coverable via fake state injection?
- [ ] Are interactive elements findable by semantics?

Preview coverage:
- [ ] Loading state preview
- [ ] Success / populated state preview
- [ ] Empty state preview
- [ ] Error state preview
```

---

#### ✅ What's Already Correct
- ...

---

#### 🗂️ Refactor Priority Order

| Priority | Item | Effort | Risk |
|----------|------|--------|------|
| 1 | | S/M/L | Low/Med/High |

---

## 🧠 Constraints & Preferences

- All recommendations must be idiomatic Jetpack Compose + Kotlin
- Prefer unidirectional data flow (UDF) patterns throughout
- Flag any suggestion that requires changes outside the screen boundary (shared ViewModel, navigation graph change, etc.)
- If code is not provided, base analysis on the description and ask clarifying questions before producing the detailed plan
- Keep examples concise — show the pattern, not a full implementation

---

*End of prompt — name your screen or paste your code to begin.*
