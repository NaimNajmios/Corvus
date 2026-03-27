# Corvus — UI/UX Enhancement Plan
## Result Screen · Visual & Interaction Improvements

> **Scope:** Nine targeted UI/UX enhancements to the result screen. No pipeline changes. No new API integrations. Pure Compose UI work — layout, animation, typography, and visual hierarchy. All enhancements preserve the Corvus brand identity: DM Serif Display + IBM Plex Mono, `#C8FF00` accent, minimal rounding, high contrast.

---

## Overview

| Enhancement | Problem Solved | Effort |
|---|---|---|
| **1. Above-the-Fold Contract** | Verdict pushed down by banners — user must scroll to see the answer | High |
| **2. Verdict Card Visual Hierarchy** | Verdict word not visually dominant — reads like a label, not a conclusion | Medium |
| **3. Sticky Verdict Strip** | Verdict disappears when scrolling through sources — user loses context | Low |
| **4. Source Credibility Tier Visuals** | PRIMARY and unknown sources look identical at a glance | Low |
| **5. Grounded Facts Auto-Expand** | VERIFIED facts look the same as LOW_CONFIDENCE facts — evidence not surfaced | Low |
| **6. Reveal Animation Sequencing** | All content loads simultaneously — visually noisy, no reading rhythm | Low |
| **7. Attribution Typography** | Evidentiary phrases blend into body text — source-grounded statements not distinguished | Medium |
| **8. Confidence Contextual Label** | 88% means nothing to most users — no reference frame | Low |
| **9. Stage-Aware Loading Skeleton** | Generic spinner — user doesn't understand what they're waiting for | Medium |

---

---

# Enhancement 1 — Above-the-Fold Contract

## Problem

As the result screen accumulates conditional components, the `LazyColumn` item order becomes:

```
RecencyWarningBanner     ← full-width card, ~80dp
ViralWarningBanner       ← full-width card, ~80dp
VerdictCard              ← THE ANSWER
MissingContextCallout
KernelOfTruth
GroundedFacts
...
```

On a 720dp-tall phone, two full-width banners consume ~160dp before the verdict is even visible. The user's first question — *"is this true or false?"* — requires a scroll.

## Solution

Recency and viral signals are demoted to **inline chips inside the VerdictCard** in their weak/moderate forms. They only render as full-width banners when they *are* the primary finding (viral short-circuit returning `KnownHoax`, or `RECENCY_UNVERIFIABLE` verdict).

### Above-Fold Guarantee

```
Max guaranteed layout above fold (720dp phone):
  AppBar (56dp) + StatusBar (24dp) = 80dp overhead
  Remaining: 640dp

  VerdictCard (compact): ~220dp
  MissingContextCallout (when present): ~80dp
  ─────────────────────────────────────
  Total above fold:       ~300dp
  Remaining for facts:    ~340dp
```

The VerdictCard is the only item guaranteed to be **fully visible** without scrolling on any device.

### LazyColumn Item Order — Updated

```kotlin
LazyColumn(
    state          = listState,
    contentPadding = PaddingValues(
        top    = 16.dp,
        bottom = 88.dp     // Space for bottom action bar (future)
    ),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    // 1. ONLY show banners when they ARE the primary finding
    item(key = "recency_banner") {
        // Full banner ONLY for RECENCY_UNVERIFIABLE verdict
        if (result.verdict == Verdict.RECENCY_UNVERIFIABLE) {
            RecencyFullBanner(
                result.recencySignal,
                result.originalClaim,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // BREAKING signal + non-RECENCY_UNVERIFIABLE → chip inside VerdictCard (handled there)
    }

    item(key = "viral_banner") {
        // Full banner ONLY for KnownHoax short-circuit
        if (result.viralDetection is ViralDetectionResult.KnownHoax &&
            result.verdict == Verdict.RECENCY_UNVERIFIABLE) {
            ViralFullBanner(
                result.viralDetection,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // PossiblyRelated → soft chip inside VerdictCard
    }

    // 2. VERDICT CARD — always the first substantive item
    item(key = "verdict_card") {
        VerdictCard(
            result   = result,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    // 3. Missing context — directly below verdict, before explanation
    result.missingContext?.let { ctx ->
        item(key = "missing_context") {
            MissingContextCallout(
                ctx      = ctx,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    // 4. Rest of content — below fold, user scrolls deliberately
    result.kernelOfTruth?.let { kernel ->
        item(key = "kernel") {
            KernelOfTruthCard(
                kernel        = kernel,
                sources       = result.sources,
                onSourceClick = { scrollToSource(it) },
                modifier      = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    item(key = "key_facts") {
        GroundedFactsList(
            facts         = result.keyFacts,
            sources       = result.sources,
            onSourceClick = { scrollToSource(it) },
            modifier      = Modifier.padding(horizontal = 16.dp)
        )
    }

    result.confidenceTimeline?.takeIf { it.entries.size >= 2 }?.let { timeline ->
        item(key = "timeline") {
            ConfidenceTimelineCard(
                timeline = timeline,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    item(key = "entity_context") { EntityContextSection(result, listState) }

    item(key = "sources_header") { SourcesSectionHeader(result.sources) }

    itemsIndexed(
        items = result.sources,
        key   = { _, s -> s.url ?: s.title }
    ) { index, source ->
        SourceCard(
            source      = source,
            numberLabel = index + 1,
            modifier    = Modifier.padding(horizontal = 16.dp)
        )
    }

    item(key = "methodology") {
        result.methodologyMetadata?.let {
            MethodologyCard(
                metadata = it,
                report   = result.tokenReport,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
```

### VerdictCard — Inline Signal Chips

Recency and viral signals as chips *inside* the VerdictCard when they are not the primary finding:

```kotlin
@Composable
fun VerdictSignalChips(
    recencySignal  : RecencySignal?,
    viralDetection : ViralDetectionResult?
) {
    val chips = buildList {
        // Recency chip
        when (recencySignal) {
            RecencySignal.BREAKING -> add(SignalChip(
                label = "⚡ BREAKING",
                color = CorvusTheme.colors.recency,
                tooltip = "Claim references a very recent event — sources may be limited"
            ))
            RecencySignal.RECENT -> add(SignalChip(
                label = "🕐 RECENT",
                color = CorvusTheme.colors.verdictMisleading,
                tooltip = "Claim references recent events — verify publication dates"
            ))
            else -> {}
        }
        // Viral chip
        when (viralDetection) {
            is ViralDetectionResult.KnownHoax -> add(SignalChip(
                label = "⚠ KNOWN HOAX",
                color = CorvusTheme.colors.verdictFalse,
                tooltip = "Matches known misinformation. Similarity: ${(viralDetection.similarityScore * 100).roundToInt()}%"
            ))
            is ViralDetectionResult.PossiblyRelated -> add(SignalChip(
                label = "~ POSSIBLY VIRAL",
                color = CorvusTheme.colors.verdictMisleading,
                tooltip = "Similar to a known claim. Treat with caution."
            ))
            else -> {}
        }
    }

    if (chips.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        items(chips) { chip ->
            InlineSignalChip(chip)
        }
    }
}

data class SignalChip(val label: String, val color: Color, val tooltip: String)

@Composable
fun InlineSignalChip(chip: SignalChip) {
    Surface(
        color  = chip.color.copy(alpha = 0.10f),
        shape  = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, chip.color.copy(alpha = 0.35f))
    ) {
        Text(
            text      = chip.label,
            modifier  = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style     = CorvusTheme.typography.labelSmall,
            color     = chip.color,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.5.sp
        )
    }
}
```

### Tasks — Enhancement 1

- [ ] Audit `LazyColumn` item order — move VerdictCard to position 1 (after conditional banners)
- [ ] Move `RecencyWarningBanner` to full-width only for `RECENCY_UNVERIFIABLE` verdict
- [ ] Move `ViralWarningBanner` to full-width only for `KnownHoax` short-circuit
- [ ] Implement `VerdictSignalChips` composable
- [ ] Implement `InlineSignalChip` composable
- [ ] Add `VerdictSignalChips` to top of `VerdictCard` content
- [ ] Add `contentPadding(bottom = 88.dp)` to `LazyColumn`
- [ ] Unit test: RECENCY_UNVERIFIABLE → full RecencyBanner renders
- [ ] Unit test: BREAKING + non-RECENCY_UNVERIFIABLE → chip inside VerdictCard only
- [ ] Unit test: KnownHoax short-circuit → full ViralBanner renders
- [ ] Unit test: PossiblyRelated → chip inside VerdictCard, no banner

**Estimated duration: 3 days**

---

---

# Enhancement 2 — Verdict Card Visual Hierarchy

## Problem

The verdict word is a `Text` composable with `CorvusTheme.typography.displayLarge` — likely 36sp DM Serif Display. It competes visually with the explanation text, harm warning, and confidence bar. The user's eye is not immediately drawn to it.

## Solution

The verdict word dominates at 46sp minimum. The confidence bar runs directly under it as a visual extension of the verdict. Harm tag is inline, small, subordinate. One-sentence summary replaces a full paragraph in the collapsed state.

```kotlin
@Composable
fun VerdictCard(
    result   : CorvusCheckResult.GeneralResult,
    modifier : Modifier = Modifier
) {
    val verdictColor  = result.verdict.color()
    val borderWidth   = when (result.harmAssessment.level) {
        HarmLevel.HIGH     -> 2.dp
        HarmLevel.MODERATE -> 1.5.dp
        else               -> 1.dp
    }
    val cardBackground = when (result.harmAssessment.level) {
        HarmLevel.HIGH -> result.verdict.color().copy(alpha = 0.05f)
        else           -> CorvusTheme.colors.surface
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(
            borderWidth,
            if (result.harmAssessment.level == HarmLevel.HIGH)
                CorvusTheme.colors.verdictFalse
            else
                verdictColor.copy(alpha = 0.35f)
        ),
        shape  = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Signal chips (recency + viral) ───────────────────
            VerdictSignalChips(
                recencySignal  = result.recencySignal,
                viralDetection = result.viralDetection
            )

            // ── Claim type label ─────────────────────────────────
            Text(
                text      = result.claimType.name.replace("_", " "),
                style     = CorvusTheme.typography.labelSmall,
                color     = CorvusTheme.colors.textTertiary,
                fontFamily = IbmPlexMono,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))

            // ── Verdict word — visually dominant ─────────────────
            Text(
                text      = result.verdict.displayName(),
                style     = TextStyle(
                    fontFamily = DmSerifDisplay,
                    fontSize   = 46.sp,
                    lineHeight = 50.sp,
                    color      = verdictColor
                ),
                modifier  = Modifier.fillMaxWidth()
            )

            // ── Confidence bar — runs directly under verdict word ─
            Spacer(Modifier.height(6.dp))
            ConfidenceBar(
                confidence = result.confidence,
                color      = verdictColor
            )
            Spacer(Modifier.height(2.dp))

            // ── Confidence value + contextual label ──────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text      = result.confidence.confidenceLabel(),
                    style     = CorvusTheme.typography.caption,
                    color     = CorvusTheme.colors.textSecondary,
                    fontFamily = IbmPlexMono
                )
                Text(
                    text      = "${(result.confidence * 100).roundToInt()}%",
                    style     = CorvusTheme.typography.caption,
                    color     = verdictColor,
                    fontFamily = IbmPlexMono,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Harm tag — inline, subordinate ───────────────────
            if (result.harmAssessment.level != HarmLevel.NONE) {
                Spacer(Modifier.height(10.dp))
                HarmInlineTag(result.harmAssessment)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = CorvusTheme.colors.border)
            Spacer(Modifier.height(14.dp))

            // ── Explanation — collapsed to 3 lines by default ────
            val maxLines = if (expanded) Int.MAX_VALUE else 3
            FormattedExplanation(
                text     = result.explanation,
                maxLines = maxLines
            )

            // ── Expand affordance ─────────────────────────────────
            if (!expanded) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick        = { expanded = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "Read full analysis →",
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.accent,
                        fontFamily = IbmPlexMono
                    )
                }
            }

            // ── Kernel of truth summary chips ────────────────────
            result.kernelOfTruth?.let { kernel ->
                Spacer(Modifier.height(10.dp))
                KernelSummaryRow(
                    trueCount  = kernel.trueParts.size,
                    falseCount = kernel.falseParts.size
                )
            }
        }
    }
}

@Composable
fun ConfidenceBar(confidence: Float, color: Color) {
    val animatedWidth by animateFloatAsState(
        targetValue    = confidence,
        animationSpec  = tween(600, easing = FastOutSlowInEasing),
        label          = "confidence"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(CorvusTheme.colors.border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedWidth)
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
fun KernelSummaryRow(trueCount: Int, falseCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (trueCount > 0) {
            Surface(
                color  = CorvusTheme.colors.verdictTrue.copy(alpha = 0.1f),
                shape  = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, CorvusTheme.colors.verdictTrue.copy(alpha = 0.3f))
            ) {
                Text(
                    "$trueCount thing${if (trueCount > 1) "s" else ""} true",
                    modifier  = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style     = CorvusTheme.typography.caption,
                    color     = CorvusTheme.colors.verdictTrue,
                    fontFamily = IbmPlexMono
                )
            }
        }
        if (falseCount > 0) {
            Surface(
                color  = CorvusTheme.colors.verdictFalse.copy(alpha = 0.1f),
                shape  = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, CorvusTheme.colors.verdictFalse.copy(alpha = 0.3f))
            ) {
                Text(
                    "$falseCount thing${if (falseCount > 1) "s" else ""} false",
                    modifier  = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style     = CorvusTheme.typography.caption,
                    color     = CorvusTheme.colors.verdictFalse,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }
}

// Verdict display names — full word, not enum string
fun Verdict.displayName() = when (this) {
    Verdict.TRUE                  -> "True"
    Verdict.FALSE                 -> "False"
    Verdict.MISLEADING            -> "Misleading"
    Verdict.PARTIALLY_TRUE        -> "Partially True"
    Verdict.UNVERIFIABLE          -> "Unverifiable"
    Verdict.RECENCY_UNVERIFIABLE  -> "Too Recent"
    Verdict.NOT_A_CLAIM           -> "Not a Claim"
}
```

### Tasks — Enhancement 2

- [ ] Update `VerdictCard` — verdict word at 46sp DM Serif Display
- [ ] Implement `ConfidenceBar` with animated width fill (600ms FastOutSlowIn)
- [ ] Position confidence bar directly under verdict word
- [ ] Add confidence value + contextual label row below bar
- [ ] Collapse explanation to 3 lines with "Read full analysis →" expand affordance
- [ ] Implement `KernelSummaryRow` — true/false count chips at bottom of VerdictCard
- [ ] Move harm tag inline (not a separate card above) when MODERATE
- [ ] Keep `HarmWarningBlock` as separate block for HIGH harm — below the explanation
- [ ] Implement `Verdict.displayName()` — human-readable names
- [ ] Unit test: verdict word renders in correct verdict colour
- [ ] Unit test: confidence bar animates to correct width
- [ ] Unit test: expand/collapse explanation works correctly
- [ ] Visual test: HIGH harm card shows red border + background tint
- [ ] Visual test: 46sp DM Serif verdict word does not truncate on small screens

**Estimated duration: 3 days**

---

---

# Enhancement 3 — Sticky Verdict Strip on Scroll

## Problem

The VerdictCard scrolls off screen after 2–3 source card heights. By the time the user is reading sources, they have no visual reminder of what verdict they're reading evidence for.

## Solution

A thin 36dp strip that appears below the app bar when `firstVisibleItemIndex > 0`, showing the verdict word in small DM Serif + confidence number. Disappears when user scrolls back to top.

```kotlin
// In ResultScreen — wraps the LazyColumn

@Composable
fun ResultScreen(
    result   : CorvusCheckResult.GeneralResult,
    modifier : Modifier = Modifier
) {
    val listState  = rememberLazyListState()

    // Derive sticky visibility from scroll state
    val showSticky by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    Box(modifier = modifier.fillMaxSize()) {

        LazyColumn(state = listState, /* ... */ ) { /* items */ }

        // Sticky verdict strip — overlays top of LazyColumn
        AnimatedVisibility(
            visible = showSticky,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(tween(200)),
            exit  = slideOutVertically { -it } + fadeOut(tween(150))
        ) {
            StickyVerdictStrip(
                verdict    = result.verdict,
                confidence = result.confidence
            )
        }
    }
}

@Composable
fun StickyVerdictStrip(
    verdict    : Verdict,
    confidence : Float
) {
    val verdictColor = verdict.color()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = CorvusTheme.colors.void_.copy(alpha = 0.95f),
        border   = BorderStroke(
            width = 0.dp,
            color = Color.Transparent
        )
    ) {
        Column {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Verdict word — small DM Serif, verdict colour
                Text(
                    text      = verdict.displayName().uppercase(),
                    style     = TextStyle(
                        fontFamily  = DmSerifDisplay,
                        fontSize    = 14.sp,
                        letterSpacing = 1.sp,
                        color       = verdictColor
                    )
                )

                // Confidence + label
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text      = confidence.confidenceLabel(),
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.textTertiary,
                        fontFamily = IbmPlexMono
                    )
                    Text(
                        text      = "${(confidence * 100).roundToInt()}%",
                        style     = CorvusTheme.typography.caption,
                        color     = verdictColor,
                        fontFamily = IbmPlexMono,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Thin verdict-coloured line at the bottom of the strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                verdictColor.copy(alpha = 0.5f),
                                verdictColor.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
```

### Backdrop Blur (Optional Enhancement)

If targeting API 31+, add a `BlurMaskFilter` or use the `Modifier.blur()` API for a frosted glass effect:

```kotlin
// API 31+ only — wrap with version check
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Surface(
        modifier = Modifier.fillMaxWidth().blur(radius = 8.dp),
        color    = CorvusTheme.colors.void_.copy(alpha = 0.85f)
    ) { /* strip content */ }
}
```

### Tasks — Enhancement 3

- [ ] Implement `StickyVerdictStrip` composable
- [ ] Wire `derivedStateOf { listState.firstVisibleItemIndex > 0 }` to strip visibility
- [ ] Implement `slideInVertically + fadeIn` enter animation (200ms)
- [ ] Implement `slideOutVertically + fadeOut` exit animation (150ms)
- [ ] Add verdict-coloured gradient line at bottom of strip
- [ ] Wrap `LazyColumn` in `Box` with strip as overlay
- [ ] Ensure strip does not interfere with `TopAppBar` (position correctly below app bar)
- [ ] Optional: API 31+ blur modifier
- [ ] Unit test: strip visible when `firstVisibleItemIndex > 0`
- [ ] Unit test: strip hidden when `firstVisibleItemIndex == 0`
- [ ] Visual test: strip colour matches verdict on all 7 verdict types

**Estimated duration: 1 day**

---

---

# Enhancement 4 — Source Card Credibility Tier Visuals

## Problem

All source cards have a 1dp `--border` left edge regardless of whether they are an official government transcript or an anonymous blog. Users who notice the credibility score and bar (in expanded state) are fine — but the collapsed state reveals nothing at a glance.

## Solution

Left border colour and width encode credibility tier at a glance. Credibility score and bar always visible in collapsed state (no tap required). Low-credibility sources show explicit "excluded from LLM context" disclosure.

```kotlin
@Composable
fun SourceCard(
    source      : CorvusSource,
    numberLabel : Int,
    modifier    : Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val rating     = source.outletRating

    // ── Tier-encoded border ──────────────────────────────────
    val (borderColor, borderWidth) = when (source.credibilityTier) {
        CredibilityTier.PRIMARY  ->
            Pair(Color(0xFFD6A03D), 2.dp)   // Gold — authoritative
        CredibilityTier.VERIFIED ->
            Pair(CorvusTheme.colors.accent.copy(alpha = 0.5f), 1.5.dp)
        CredibilityTier.GENERAL  ->
            Pair(CorvusTheme.colors.border, 1.dp)
        else ->
            Pair(CorvusTheme.colors.verdictFalse.copy(alpha = 0.4f), 1.dp) // Excluded
    }

    val cardBackground = when {
        source.credibilityTier == CredibilityTier.PRIMARY ->
            Color(0xFFD6A03D).copy(alpha = 0.04f)
        (rating?.credibility ?: 50) < 40 ->
            CorvusTheme.colors.verdictFalse.copy(alpha = 0.03f)
        else ->
            CorvusTheme.colors.surface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(borderWidth, borderColor),
        shape    = RoundedCornerShape(
            topStart     = 0.dp,   // Sharp left edge emphasises the border
            topEnd       = 4.dp,
            bottomStart  = 0.dp,
            bottomEnd    = 4.dp
        ),
        colors   = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column {
            // ── Collapsed main row ───────────────────────────────
            Row(
                modifier              = Modifier
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top
            ) {
                // Source number badge
                SourceNumberBadge(numberLabel)

                Column(modifier = Modifier.weight(1f)) {

                    // Publisher + source type row
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Tier badge — always visible
                            CredibilityTierBadge(source.credibilityTier)
                            Text(
                                text      = source.publisher ?: extractDomain(source.url),
                                style     = CorvusTheme.typography.labelSmall,
                                color     = CorvusTheme.colors.accent,
                                fontFamily = IbmPlexMono,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // Credibility score — always visible in collapsed state
                        rating?.let { CompactCredibilityScore(it.credibility) }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Article title
                    Text(
                        text     = source.title,
                        style    = CorvusTheme.typography.bodySmall,
                        color    = if ((rating?.credibility ?: 50) < 40)
                            CorvusTheme.colors.textTertiary   // Dim excluded sources
                        else
                            CorvusTheme.colors.textPrimary,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Date + type row
                    Row(
                        modifier              = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        source.publicationDate?.let { SourceDateDisplay(it) }
                        SourceTypeChip(source.sourceType)
                    }

                    // LLM exclusion disclosure — always visible for excluded sources
                    if ((rating?.credibility ?: 50) < 40) {
                        Spacer(Modifier.height(6.dp))
                        LlmExclusionNotice()
                    }
                }
            }

            // ── Expanded: full ratings breakdown ─────────────────
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = CorvusTheme.colors.border)
                    rating?.let { r ->
                        SourceRatingBreakdown(
                            rating    = r,
                            modifier  = Modifier.padding(12.dp)
                        )
                    }
                    source.url?.let { url ->
                        HorizontalDivider(color = CorvusTheme.colors.border)
                        TextButton(
                            onClick        = { uriHandler.openUri(url) },
                            modifier       = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(
                                "Open source →",
                                style     = CorvusTheme.typography.labelSmall,
                                color     = CorvusTheme.colors.accent,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CredibilityTierBadge(tier: CredibilityTier) {
    val (label, color) = when (tier) {
        CredibilityTier.PRIMARY  -> "GOV" to Color(0xFFD6A03D)
        CredibilityTier.VERIFIED -> "VERIFIED" to CorvusTheme.colors.verdictTrue
        CredibilityTier.GENERAL  -> return  // No badge for general
        else                     -> "EXCLUDED" to CorvusTheme.colors.verdictFalse
    }
    Surface(
        color  = color.copy(alpha = 0.1f),
        shape  = RoundedCornerShape(1.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text      = label,
            modifier  = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style     = CorvusTheme.typography.caption,
            color     = color,
            fontFamily = IbmPlexMono,
            fontSize   = 8.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun CompactCredibilityScore(credibility: Int) {
    val color = when {
        credibility >= 75 -> CorvusTheme.colors.verdictTrue
        credibility >= 50 -> CorvusTheme.colors.verdictMisleading
        else              -> CorvusTheme.colors.verdictFalse
    }
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text      = "$credibility",
            style     = CorvusTheme.typography.caption,
            color     = color,
            fontFamily = IbmPlexMono,
            fontWeight = FontWeight.Medium
        )
        // Mini bar — 32dp wide, always visible
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(CorvusTheme.colors.border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(credibility / 100f)
                    .background(color)
            )
        }
    }
}

@Composable
fun LlmExclusionNotice() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(
            CorvusIcons.Info,
            modifier = Modifier.size(10.dp),
            tint     = CorvusTheme.colors.verdictFalse.copy(alpha = 0.6f)
        )
        Text(
            text      = "Excluded from LLM analysis — shown for transparency only",
            style     = CorvusTheme.typography.caption,
            color     = CorvusTheme.colors.textTertiary,
            fontFamily = IbmPlexMono,
            fontStyle = FontStyle.Italic
        )
    }
}
```

### Tasks — Enhancement 4

- [ ] Update `SourceCard` border colour and width to encode credibility tier
- [ ] PRIMARY tier: gold border 2dp + subtle gold background tint
- [ ] VERIFIED tier: accent border 1.5dp
- [ ] Excluded (credibility &lt; 40): red border 1dp + dimmed title text
- [ ] Implement `CredibilityTierBadge` — GOV · VERIFIED · EXCLUDED
- [ ] Implement `CompactCredibilityScore` — score number + 32dp mini bar, always visible in collapsed state
- [ ] Implement `LlmExclusionNotice` — always visible for excluded sources
- [ ] Dim title text to `textTertiary` for excluded sources
- [ ] Sharp left corners (0dp radius) on source card to make left border more prominent
- [ ] Unit test: PRIMARY tier renders gold border
- [ ] Unit test: credibility &lt; 40 shows exclusion notice
- [ ] Visual test: all 4 tier states render distinct from each other at a glance

**Estimated duration: 2 days**

---

---

# Enhancement 5 — Grounded Facts Auto-Expand Fragment

## Problem

VERIFIED facts (similarity ≥ 0.70, matched fragment available) look identical to LOW_CONFIDENCE facts and UNATTRIBUTED facts. The `matchedFragment` field is populated but not surfaced unless the user knows to look for it. The evidence that proves the fact is correct is invisible.

## Solution

VERIFIED facts auto-expand to show the matched fragment inline. No tap required. The fragment is the "receipt" — the exact text from the source that justifies the fact. LOW_CONFIDENCE and UNATTRIBUTED facts get a dashed left border instead of solid to signal weaker evidence visually.

```kotlin
@Composable
fun GroundedFactRow(
    fact          : GroundedFact,
    source        : CorvusSource?,
    onCitationClick: () -> Unit
) {
    // VERIFIED facts auto-expand fragment — no tap needed
    val autoExpanded = fact.verification?.confidence == CitationConfidence.VERIFIED &&
                       fact.verification.matchedFragment != null

    var showFragment by remember { mutableStateOf(autoExpanded) }

    val leftBarColor = fact.verification?.confidence?.barColor()
        ?: CorvusTheme.colors.border

    // Dashed border for LOW_CONFIDENCE and UNATTRIBUTED
    val useDashedBorder = fact.verification?.confidence == CitationConfidence.LOW_CONFIDENCE ||
                          fact.verification?.confidence == CitationConfidence.UNATTRIBUTED

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top
    ) {
        // Left bar — solid for verified, dashed for uncertain
        if (useDashedBorder) {
            DashedVerticalBar(color = leftBarColor)
        } else {
            SolidVerticalBar(color = leftBarColor)
        }

        Column(modifier = Modifier.weight(1f)) {

            // Fact statement
            Text(
                text  = if (fact.isDirectQuote) "\"${fact.statement}\"" else fact.statement,
                style = if (fact.isDirectQuote)
                    CorvusTheme.typography.body.copy(fontStyle = FontStyle.Italic)
                else
                    CorvusTheme.typography.body,
                color = when (fact.verification?.confidence) {
                    CitationConfidence.LOW_CONFIDENCE -> CorvusTheme.colors.textSecondary
                    CitationConfidence.UNATTRIBUTED   -> CorvusTheme.colors.textTertiary
                    else                              -> CorvusTheme.colors.textPrimary
                }
            )

            // Citation row + confidence badge
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (fact.sourceIndex != null && source != null) {
                    CitationBadge(
                        index         = fact.sourceIndex,
                        publisherName = source.publisher ?: "Source ${fact.sourceIndex + 1}",
                        onClick       = onCitationClick
                    )
                } else {
                    Text(
                        "General knowledge",
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.textTertiary,
                        fontStyle = FontStyle.Italic,
                        fontFamily = IbmPlexMono
                    )
                }
                fact.verification?.let { CitationConfidenceBadge(it.confidence) }
            }

            // Auto-expanded fragment — VERIFIED + fragment available
            AnimatedVisibility(visible = showFragment) {
                fact.verification?.matchedFragment?.let { fragment ->
                    Spacer(Modifier.height(6.dp))
                    MatchedFragmentCard(fragment)
                }
            }

            // Fragment toggle — for PARTIAL (not auto-expanded but available)
            if (fact.verification?.confidence == CitationConfidence.PARTIAL &&
                fact.verification.matchedFragment != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick        = { showFragment = !showFragment },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (showFragment) "Hide evidence" else "Show partial match →",
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.verdictMisleading,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }
    }
}

@Composable
fun SolidVerticalBar(color: Color) {
    Box(
        modifier = Modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
fun DashedVerticalBar(color: Color) {
    Canvas(
        modifier = Modifier
            .width(2.dp)
            .fillMaxHeight()
    ) {
        val dashLength = 6.dp.toPx()
        val gapLength  = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color       = color,
                start       = Offset(1f, y),
                end         = Offset(1f, (y + dashLength).coerceAtMost(size.height)),
                strokeWidth = 2.dp.toPx()
            )
            y += dashLength + gapLength
        }
    }
}

@Composable
fun MatchedFragmentCard(fragment: String) {
    Surface(
        color  = CorvusTheme.colors.surfaceRaised,
        shape  = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, CorvusTheme.colors.border)
    ) {
        Row(
            modifier              = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quote accent line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(CorvusTheme.colors.accent.copy(alpha = 0.4f))
            )
            Text(
                text      = fragment,
                style     = CorvusTheme.typography.caption.copy(fontStyle = FontStyle.Italic),
                color     = CorvusTheme.colors.textSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
```

### Tasks — Enhancement 5

- [ ] Implement `SolidVerticalBar` composable
- [ ] Implement `DashedVerticalBar` composable using `Canvas`
- [ ] Update `GroundedFactRow` — auto-expand fragment for VERIFIED facts (`showFragment` initialised from `autoExpanded`)
- [ ] Add "Show partial match →" toggle for PARTIAL facts with fragments
- [ ] Dim fact text for LOW_CONFIDENCE (textSecondary) and UNATTRIBUTED (textTertiary)
- [ ] Implement `MatchedFragmentCard` with accent quote line
- [ ] Unit test: VERIFIED + fragment → `showFragment` initialised true
- [ ] Unit test: PARTIAL + fragment → toggle button shown, not auto-expanded
- [ ] Unit test: LOW_CONFIDENCE → dashed bar, dimmed text
- [ ] Unit test: UNATTRIBUTED → dashed bar, general knowledge label

**Estimated duration: 2 days**

---

---

# Enhancement 6 — Reveal Animation Sequencing

## Problem

All result screen content currently fades or slides in simultaneously (or near-simultaneously) as soon as the result is returned. This creates visual noise and doesn't guide the eye through the information hierarchy.

## Solution

A deliberate staggered reveal sequence that creates a reading rhythm. The verdict arrives first, the user reads it, then supporting information arrives in order of importance.

```kotlin
// In ResultScreen — animation state controller

data class ResultRevealState(
    val verdictVisible         : Boolean = false,
    val missingContextVisible  : Boolean = false,
    val factsVisible           : Boolean = false,
    val sourcesVisible         : Boolean = false,
    val enrichmentVisible      : Boolean = false
)

@Composable
fun ResultScreen(result: CorvusCheckResult.GeneralResult) {
    var revealState by remember { mutableStateOf(ResultRevealState()) }

    // Staggered reveal on result arrival
    LaunchedEffect(result.checkId) {
        // Step 1: Verdict card — immediate
        revealState = revealState.copy(verdictVisible = true)
        delay(600)  // Wait for confidence bar animation to complete

        // Step 2: Missing context — highest priority supplementary info
        revealState = revealState.copy(missingContextVisible = true)
        delay(400)

        // Step 3: Facts and kernel
        revealState = revealState.copy(factsVisible = true)
        delay(600)

        // Step 4: Sources — reference material, user will scroll to these
        revealState = revealState.copy(sourcesVisible = true)
        delay(300)

        // Step 5: Entity context, timeline, methodology — supplementary
        revealState = revealState.copy(enrichmentVisible = true)
    }

    LazyColumn(/* ... */) {

        item(key = "verdict_card") {
            AnimatedVisibility(
                visible = revealState.verdictVisible,
                enter   = fadeIn(tween(400)) +
                          slideInVertically(
                              initialOffsetY = { it / 6 },
                              animationSpec  = tween(400, easing = FastOutSlowInEasing)
                          )
            ) {
                VerdictCard(result = result, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        result.missingContext?.let { ctx ->
            item(key = "missing_context") {
                AnimatedVisibility(
                    visible = revealState.missingContextVisible,
                    enter   = fadeIn(tween(350)) +
                              slideInHorizontally(
                                  initialOffsetX = { -32 },  // Slides from left — distinct from verdict
                                  animationSpec  = tween(350, easing = FastOutSlowInEasing)
                              )
                ) {
                    MissingContextCallout(ctx = ctx, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        item(key = "key_facts") {
            AnimatedVisibility(
                visible = revealState.factsVisible,
                enter   = fadeIn(tween(300))
            ) {
                GroundedFactsList(/* ... */)
            }
        }

        item(key = "sources_header") {
            AnimatedVisibility(visible = revealState.sourcesVisible, enter = fadeIn(tween(250))) {
                SourcesSectionHeader(result.sources)
            }
        }

        itemsIndexed(result.sources) { index, source ->
            AnimatedVisibility(
                visible = revealState.sourcesVisible,
                enter   = fadeIn(tween(250, delayMillis = index * 60))  // Stagger per source
            ) {
                SourceCard(source = source, numberLabel = index + 1, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        item(key = "entity_context") {
            AnimatedVisibility(
                visible = revealState.enrichmentVisible,
                enter   = fadeIn(tween(400, delayMillis = 100)) +
                          slideInVertically(
                              initialOffsetY = { it / 4 },
                              animationSpec  = tween(400, delayMillis = 100, easing = FastOutSlowInEasing)
                          )
            ) {
                result.entityContext?.let { EntityContextPanel(entity = it, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }

        item(key = "methodology") {
            AnimatedVisibility(visible = revealState.enrichmentVisible, enter = fadeIn(tween(300))) {
                result.methodologyMetadata?.let { MethodologyCard(metadata = it, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
}
```

### Animation Summary

| Component | Entry Animation | Delay from result arrival |
|---|---|---|
| VerdictCard | FadeIn + SlideUp (1/6 height) | 0ms |
| ConfidenceBar fill | Width expand 0→confidence | 200ms (within VerdictCard) |
| MissingContextCallout | FadeIn + SlideInFromLeft | 600ms |
| KernelOfTruth / Facts | FadeIn | 1,000ms |
| Source cards | FadeIn (staggered 60ms apart) | 1,600ms |
| Entity context | FadeIn + SlideUp (1/4 height) | 2,200ms |
| Methodology | FadeIn | 2,300ms |

### Tasks — Enhancement 6

- [ ] Implement `ResultRevealState` data class
- [ ] Implement staggered `LaunchedEffect` reveal controller in `ResultScreen`
- [ ] Wrap VerdictCard in `AnimatedVisibility` (FadeIn + SlideUp)
- [ ] Wrap MissingContextCallout in `AnimatedVisibility` (FadeIn + SlideFromLeft)
- [ ] Wrap GroundedFactsList in `AnimatedVisibility` (FadeIn)
- [ ] Apply per-source stagger delay: `delayMillis = index * 60`
- [ ] Wrap EntityContextPanel in `AnimatedVisibility` (FadeIn + SlideUp)
- [ ] Wrap MethodologyCard in `AnimatedVisibility` (FadeIn)
- [ ] Remove any existing individual component enter animations that conflict
- [ ] Unit test: `revealState` transitions fire in correct order
- [ ] Performance test: 60fps maintained throughout reveal sequence on mid-range device

**Estimated duration: 2 days**

---

---

# Enhancement 7 — Attribution Typography in Explanation

## Problem

The explanation paragraph reads in uniform `IBM Plex Sans` body text. Attribution phrases like *"According to Bernama..."*, *"DOSM data shows..."*, *"Official Hansard transcripts..."* are stylistically identical to the rest of the sentence. The source-grounded nature of the explanation is not immediately legible.

## Solution

Parse attribution phrases in the explanation and render them as `AnnotatedString` spans in `accent-dim` colour. The visual distinction is subtle but effective — users' eyes are drawn to the attributed phrases, reinforcing that the explanation is evidence-based rather than AI-generated opinion.

```kotlin
// domain/util/AttributionFormatter.kt

object AttributionFormatter {

    // Phrases that signal attribution to a specific source
    private val ATTRIBUTION_PHRASES = listOf(
        // English
        "According to ", "According to data from ",
        "Official ", "As confirmed by ",
        "Multiple verified", "Multiple sources",
        "Bernama reports", "Bernama, Malaysia's",
        "DOSM data", "Department of Statistics",
        "Hansard transcripts", "Official Hansard",
        "WHO ", "World Health Organization",
        "World Bank ", "IMF data",
        "Prime Minister's Office",
        "No verified source", "No official source",
        "Peer-reviewed", "Published studies",
        // BM
        "Menurut ", "Berdasarkan ",
        "Bernama melaporkan", "Data DOSM",
        "Rekod Hansard", "Sumber rasmi",
        "Pelbagai sumber", "Tiada sumber"
    )

    fun format(
        explanation : String,
        accentColor : Color,
        baseColor   : Color
    ): AnnotatedString {
        val builder = AnnotatedString.Builder()
        builder.pushStyle(SpanStyle(color = baseColor))
        builder.append(explanation)
        builder.pop()

        // Find and highlight all attribution phrases
        ATTRIBUTION_PHRASES.forEach { phrase ->
            var startIndex = 0
            while (true) {
                val index = explanation.indexOf(phrase, startIndex, ignoreCase = true)
                if (index == -1) break

                builder.addStyle(
                    style = SpanStyle(
                        color      = accentColor,
                        fontWeight = FontWeight.Medium
                    ),
                    start = index,
                    end   = index + phrase.length
                )
                startIndex = index + phrase.length
            }
        }

        return builder.toAnnotatedString()
    }
}
```

```kotlin
// ui/components/FormattedExplanation.kt

@Composable
fun FormattedExplanation(
    text     : String,
    maxLines : Int = Int.MAX_VALUE,
    modifier : Modifier = Modifier
) {
    val annotated = remember(text) {
        AttributionFormatter.format(
            explanation = text,
            accentColor = CorvusTheme.colors.accentDim,  // #8AAA00 — subdued, not distracting
            baseColor   = CorvusTheme.colors.textPrimary
        )
    }

    Text(
        text     = annotated,
        style    = CorvusTheme.typography.body.copy(lineHeight = 24.sp),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
```

### Replace in VerdictCard

```kotlin
// In VerdictCard — replace raw Text with FormattedExplanation
FormattedExplanation(
    text     = result.explanation,
    maxLines = if (expanded) Int.MAX_VALUE else 3,
    modifier = Modifier.fillMaxWidth()
)
```

### Tasks — Enhancement 7

- [ ] Implement `AttributionFormatter` object with `format()` function
- [ ] Build `ATTRIBUTION_PHRASES` list — English + BM phrases
- [ ] Implement `AnnotatedString.Builder` loop for all phrase occurrences
- [ ] Implement `FormattedExplanation` composable wrapping `Text` with `AnnotatedString`
- [ ] Replace raw `Text(explanation)` in `VerdictCard` with `FormattedExplanation`
- [ ] Use `accentDim` (`#8AAA00`) not full accent — subtle, not distracting
- [ ] Unit test: "According to Bernama..." → phrase highlighted in accentDim
- [ ] Unit test: multiple occurrences of same phrase → all highlighted
- [ ] Unit test: BM phrase "Menurut " → highlighted correctly
- [ ] Unit test: no attribution phrases → output identical to input (no spurious spans)
- [ ] Visual test: highlighted phrases legible against dark background
- [ ] Visual test: highlight does not cause reflow or line-break changes

**Estimated duration: 2 days**

---

---

# Enhancement 8 — Confidence Contextual Label

## Problem

The confidence score is a float displayed as a percentage. *"88%"* is precise but meaningless to most users. There is no reference frame — they do not know if 88% is high, medium, or cause for concern.

## Solution

A human-readable label displayed alongside the percentage. Implemented as a simple extension function. Used in the VerdictCard, the sticky strip, and the Methodology Card.

```kotlin
// domain/util/ConfidenceLabel.kt

fun Float.confidenceLabel(): String = when {
    this >= 0.90f -> "Very high confidence"
    this >= 0.75f -> "High confidence"
    this >= 0.60f -> "Moderate confidence"
    this >= 0.40f -> "Low confidence"
    this >= 0.20f -> "Very low confidence"
    else          -> "Treat with caution"
}

fun Float.confidenceLabelShort(): String = when {
    this >= 0.90f -> "Very high"
    this >= 0.75f -> "High"
    this >= 0.60f -> "Moderate"
    this >= 0.40f -> "Low"
    else          -> "Very low"
}

fun Float.confidenceColor(
    highColor    : Color,
    midColor     : Color,
    lowColor     : Color
): Color = when {
    this >= 0.75f -> highColor
    this >= 0.50f -> midColor
    else          -> lowColor
}
```

### Usage in VerdictCard

```kotlin
// Confidence row in VerdictCard
Row(
    modifier              = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment     = Alignment.CenterVertically
) {
    // Full label — left-aligned
    Text(
        text      = result.confidence.confidenceLabel(),
        style     = CorvusTheme.typography.caption,
        color     = CorvusTheme.colors.textSecondary,
        fontFamily = IbmPlexMono
    )
    // Percentage — right-aligned, verdict colour
    Text(
        text      = "${(result.confidence * 100).roundToInt()}%",
        style     = CorvusTheme.typography.caption,
        color     = result.verdict.color(),
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium
    )
}
```

### Usage in Sticky Strip

```kotlin
// In StickyVerdictStrip
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text      = confidence.confidenceLabelShort(),  // Shorter for strip
        style     = CorvusTheme.typography.caption,
        color     = CorvusTheme.colors.textTertiary,
        fontFamily = IbmPlexMono
    )
    Text(
        text      = "${(confidence * 100).roundToInt()}%",
        style     = CorvusTheme.typography.caption,
        color     = verdictColor,
        fontFamily = IbmPlexMono
    )
}
```

### Usage in Methodology Card

```kotlin
MethodologyStatRow(
    label = "Confidence",
    value = "${(metadata.confidence * 100).roundToInt()}% · ${metadata.confidence.confidenceLabel()}"
)
```

### Tasks — Enhancement 8

- [ ] Implement `Float.confidenceLabel()` extension function
- [ ] Implement `Float.confidenceLabelShort()` extension function
- [ ] Implement `Float.confidenceColor()` extension function
- [ ] Update `VerdictCard` confidence row — add full label left of percentage
- [ ] Update `StickyVerdictStrip` — add short label next to percentage
- [ ] Update `MethodologyCard` — add label alongside percentage
- [ ] Update `HistoryItemRow` — add short label next to verdict chip
- [ ] Unit test: all threshold boundaries return correct label
- [ ] Unit test: 0.0 → "Treat with caution"
- [ ] Unit test: 1.0 → "Very high confidence"
- [ ] Unit test: 0.749 → "Moderate confidence" (below 0.75 threshold)
- [ ] Unit test: 0.750 → "High confidence" (at 0.75 threshold)

**Estimated duration: 0.5 days**

---

---

# Enhancement 9 — Stage-Aware Loading Skeleton

## Problem

The current loading state shows a pipeline step label ("Retrieving sources...") and presumably a `CircularProgressIndicator`. The user sees a text label changing over ~8 seconds with no visual context of what the result screen will look like.

## Solution

A skeleton version of the result screen that mirrors the actual layout. As each pipeline stage completes, the relevant skeleton section resolves. The skeleton communicates structure; the pipeline label communicates progress.

```kotlin
// ui/components/LoadingSkeleton.kt

@Composable
fun CorvusResultSkeleton(
    currentStep : PipelineStep,
    modifier    : Modifier = Modifier
) {
    LazyColumn(
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled   = false   // No scroll during loading
    ) {
        // Pipeline progress indicator — always shown, updates with step
        item {
            PipelineProgressStrip(currentStep = currentStep)
        }

        // VerdictCard skeleton — shows immediately
        item {
            VerdictCardSkeleton()
        }

        // MissingContext skeleton — appears after CHECKING_KNOWN_FACTS
        item {
            AnimatedVisibility(
                visible = currentStep >= PipelineStep.RETRIEVING_SOURCES,
                enter   = fadeIn(tween(300))
            ) {
                MissingContextSkeleton()
            }
        }

        // Facts skeleton — appears after RETRIEVING_SOURCES
        item {
            AnimatedVisibility(
                visible = currentStep >= PipelineStep.ANALYZING,
                enter   = fadeIn(tween(300))
            ) {
                KeyFactsSkeleton()
            }
        }

        // Sources skeleton — appears during ANALYZING
        item {
            AnimatedVisibility(
                visible = currentStep >= PipelineStep.ANALYZING,
                enter   = fadeIn(tween(300))
            ) {
                SourcesSkeleton()
            }
        }
    }
}

@Composable
fun VerdictCardSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = CorvusTheme.colors.surface,
        border   = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape    = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            ShimmerBox(width = 80.dp, height = 9.dp)   // Claim type label
            Spacer(Modifier.height(8.dp))
            ShimmerBox(width = 160.dp, height = 46.dp) // Verdict word (DM Serif 46sp height)
            Spacer(Modifier.height(8.dp))
            ShimmerBox(width = Dp.Infinity, height = 3.dp)  // Confidence bar
            Spacer(Modifier.height(4.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShimmerBox(width = 120.dp, height = 9.dp)
                ShimmerBox(width = 30.dp, height = 9.dp)
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = CorvusTheme.colors.border)
            Spacer(Modifier.height(14.dp))
            // Explanation lines
            ShimmerBox(width = Dp.Infinity, height = 9.dp)
            Spacer(Modifier.height(4.dp))
            ShimmerBox(width = Dp.Infinity, height = 9.dp)
            Spacer(Modifier.height(4.dp))
            ShimmerBox(width = 200.dp, height = 9.dp)
        }
    }
}

@Composable
fun MissingContextSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        color    = CorvusTheme.colors.surface,
        border   = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape    = RoundedCornerShape(6.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(width = 120.dp, height = 9.dp)
                ShimmerBox(width = Dp.Infinity, height = 9.dp)
                ShimmerBox(width = 180.dp, height = 9.dp)
            }
        }
    }
}

@Composable
fun KeyFactsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(width = 70.dp, height = 9.dp)  // "KEY FACTS" label
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.width(2.dp).height(48.dp).background(CorvusTheme.colors.border))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ShimmerBox(width = Dp.Infinity, height = 9.dp)
                    ShimmerBox(width = 200.dp, height = 9.dp)
                    ShimmerBox(width = 100.dp, height = 8.dp)
                }
            }
        }
    }
}

@Composable
fun SourcesSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShimmerBox(width = 60.dp, height = 9.dp)  // "SOURCES" header
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = CorvusTheme.colors.surface,
                border   = BorderStroke(1.dp, CorvusTheme.colors.border),
                shape    = RoundedCornerShape(
                    topStart = 0.dp, topEnd = 4.dp,
                    bottomStart = 0.dp, bottomEnd = 4.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShimmerBox(width = 20.dp, height = 20.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ShimmerBox(width = 80.dp, height = 9.dp)
                            ShimmerBox(width = 50.dp, height = 9.dp)
                        }
                        ShimmerBox(width = Dp.Infinity, height = 9.dp)
                        ShimmerBox(width = 140.dp, height = 8.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerBox(width: Dp, height: Dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue   = -500f,
        targetValue    = 500f,
        animationSpec  = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label          = "shimmerOffset"
    )

    val resolvedWidth = if (width == Dp.Infinity) {
        with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp - 64.dp }
    } else width

    Box(
        modifier = Modifier
            .size(resolvedWidth, height)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    colors  = listOf(
                        CorvusTheme.colors.border,
                        CorvusTheme.colors.surfaceRaised,
                        CorvusTheme.colors.border
                    ),
                    startX  = offset - 200f,
                    endX    = offset + 200f
                )
            )
    )
}
```

### Pipeline Progress Strip

```kotlin
@Composable
fun PipelineProgressStrip(currentStep: PipelineStep) {
    val steps = PipelineStep.values().filter { it != PipelineStep.IDLE && it != PipelineStep.DONE }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = CorvusTheme.colors.surface,
        border   = BorderStroke(1.dp, CorvusTheme.colors.border),
        shape    = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Current step label with pulsing accent dot
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                PulsingDot()
                Text(
                    text      = currentStep.displayLabel(),
                    style     = CorvusTheme.typography.labelSmall,
                    color     = CorvusTheme.colors.accent,
                    fontFamily = IbmPlexMono
                )
            }

            Spacer(Modifier.height(10.dp))

            // Step dots row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                steps.forEach { step ->
                    val isComplete = step.ordinal < currentStep.ordinal
                    val isCurrent  = step == currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    isComplete -> CorvusTheme.colors.accent
                                    isCurrent  -> CorvusTheme.colors.accentDim
                                    else       -> CorvusTheme.colors.border
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Completed steps list
            steps.filter { it.ordinal < currentStep.ordinal }.take(3).forEach { step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("✓", style = CorvusTheme.typography.caption, color = CorvusTheme.colors.accentDim)
                    Text(
                        step.displayLabel(),
                        style     = CorvusTheme.typography.caption,
                        color     = CorvusTheme.colors.textTertiary,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 0.7f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(CorvusTheme.colors.accent, CircleShape)
    )
}

fun PipelineStep.displayLabel() = when (this) {
    PipelineStep.PRE_SCREENING        -> "Pre-screening claim..."
    PipelineStep.CHECKING_KNOWN_FACTS -> "Checking fact databases..."
    PipelineStep.RETRIEVING_SOURCES   -> "Retrieving sources..."
    PipelineStep.ANALYZING            -> "Analysing..."
    else                              -> ""
}
```

### ViewModel Integration

The ViewModel emits `PipelineStep` updates as the pipeline progresses — already in `CorvusUiState`. The `ResultScreen` switches between `CorvusResultSkeleton` and the actual result screen:

```kotlin
@Composable
fun ResultScreen(uiState: CorvusUiState) {
    when {
        uiState.isLoading ->
            CorvusResultSkeleton(
                currentStep = uiState.currentStep,
                modifier    = Modifier.fillMaxSize()
            )
        uiState.result != null ->
            CorvusResultContent(
                result   = uiState.result,
                modifier = Modifier.fillMaxSize()
            )
        uiState.error != null ->
            CorvusErrorState(
                error    = uiState.error,
                modifier = Modifier.fillMaxSize()
            )
    }
}
```

### Tasks — Enhancement 9

- [ ] Implement `ShimmerBox` composable with infinite shimmer animation
- [ ] Implement `VerdictCardSkeleton` matching actual VerdictCard layout
- [ ] Implement `MissingContextSkeleton`
- [ ] Implement `KeyFactsSkeleton` (3 rows with left bar)
- [ ] Implement `SourcesSkeleton` (3 source cards)
- [ ] Implement `PipelineProgressStrip` with step dots + completed list
- [ ] Implement `PulsingDot` composable
- [ ] Implement `PipelineStep.displayLabel()` extension
- [ ] Implement `CorvusResultSkeleton` with `AnimatedVisibility` per section
- [ ] Update `ResultScreen` to switch between skeleton and result based on `uiState.isLoading`
- [ ] Ensure skeleton `LazyColumn` has `userScrollEnabled = false`
- [ ] Unit test: `PipelineStep.displayLabel()` returns correct label for all steps
- [ ] Unit test: skeleton sections appear in correct order as step advances
- [ ] Performance test: shimmer animation maintains 60fps on Pixel 6a

**Estimated duration: 3 days**

---

---

## Combined Roadmap

| Enhancement | Duration | Can Parallelise With |
|---|---|---|
| 8 — Confidence Contextual Label | 0.5 days | Everything — pure utility function |
| 3 — Sticky Verdict Strip | 1 day | 8 |
| 7 — Attribution Typography | 2 days | 3, 8 |
| 5 — Grounded Facts Auto-Expand | 2 days | 3, 7, 8 |
| 4 — Source Credibility Tier Visuals | 2 days | 5, 7 |
| 6 — Reveal Animation Sequencing | 2 days | After 1, 2 are stable |
| 1 — Above-the-Fold Contract | 3 days | Start first — all others depend on item order |
| 2 — Verdict Card Hierarchy | 3 days | After 1 is stable |
| 9 — Stage-Aware Loading Skeleton | 3 days | Independent — parallel with everything |
| **Total** | **~18.5 days** | |

**Recommended build order:**

Start with Enhancement 1 (above-fold contract) and Enhancement 8 (confidence label) simultaneously — 1 establishes the correct item order that all other enhancements depend on; 8 is a pure utility function needed by 2, 3, and 9. Then proceed: 2 → 3 → 4 → 5 in sequence. Run 7 (attribution typography) in parallel with 4. Run 9 (loading skeleton) as a completely independent track at any point.

---

## New Dependencies

```kotlin
// No new external dependencies required.
// All animations: existing Compose Animation APIs
// Shimmer: custom Canvas-based (no library needed)
// AnnotatedString: Compose foundation — already available
// AttributionFormatter: pure Kotlin string processing
```

---

## Definition of Done

**Enhancement 1 — Above-the-Fold Contract**
- VerdictCard always the first visible item without scrolling on 360dp+ width devices
- RecencyWarningBanner only renders as full-width for `RECENCY_UNVERIFIABLE` verdict
- ViralWarningBanner only renders as full-width for `KnownHoax` short-circuit
- All other recency/viral signals render as chips inside VerdictCard
- `LazyColumn` has `contentPadding(bottom = 88.dp)`

**Enhancement 2 — Verdict Card Hierarchy**
- Verdict word renders at 46sp DM Serif Display in verdict colour
- Confidence bar animates 0→confidence in 600ms below the verdict word
- Explanation collapsed to 3 lines with "Read full analysis →" expand affordance
- KernelSummaryRow chips visible at bottom of card when kernel present
- Harm tag inline (not separate card) for MODERATE harm

**Enhancement 3 — Sticky Verdict Strip**
- Strip appears on `firstVisibleItemIndex > 0` with 200ms slide+fade
- Strip disappears on scroll back to top with 150ms exit
- Verdict-coloured gradient line at bottom of strip
- Correct colour on all 7 verdict types

**Enhancement 4 — Source Credibility Tier Visuals**
- PRIMARY tier: gold border 2dp, subtle gold background tint
- VERIFIED tier: accent border 1.5dp
- GENERAL tier: standard border 1dp, no badge
- Excluded: red border 1dp, dimmed title, exclusion notice always visible
- Credibility score + 32dp mini bar always visible in collapsed state

**Enhancement 5 — Grounded Facts Auto-Expand**
- VERIFIED facts with fragments auto-expand on first render
- PARTIAL facts show "Show partial match →" toggle
- LOW_CONFIDENCE: dashed left bar, dimmed text
- UNATTRIBUTED: dashed left bar, "General knowledge" label
- Dashed bar uses Canvas (not a border property)

**Enhancement 6 — Reveal Animation Sequencing**
- VerdictCard reveals at 0ms
- MissingContextCallout reveals at 600ms (slides from left)
- Facts reveal at 1,000ms
- Sources stagger from 1,600ms at 60ms apart
- Entity context + Methodology at 2,200ms+
- No animation jank — 60fps throughout on Pixel 6a

**Enhancement 7 — Attribution Typography**
- Attribution phrases in explanation rendered in `#8AAA00` (accent-dim), SemiBold
- All phrases in `ATTRIBUTION_PHRASES` list covered
- BM phrases covered (Menurut, Berdasarkan, etc.)
- Zero false positives on 10 test explanations
- No reflow or layout changes caused by annotation

**Enhancement 8 — Confidence Contextual Label**
- `confidenceLabel()` returns correct string for all 7 threshold boundaries
- Label visible in VerdictCard alongside percentage
- Short label visible in StickyVerdictStrip
- Label included in Methodology Card stats row

**Enhancement 9 — Stage-Aware Loading Skeleton**
- Skeleton matches actual result screen layout structure
- `VerdictCardSkeleton` height matches actual VerdictCard height
- Skeleton sections animate in progressively as pipeline steps advance
- `PipelineProgressStrip` shows step dots + completed steps
- Shimmer animation smooth at 60fps
- Skeleton has `userScrollEnabled = false`
- Clean transition from skeleton to actual result (no jarring layout shift)
