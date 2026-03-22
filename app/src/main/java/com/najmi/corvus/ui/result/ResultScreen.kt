package com.najmi.corvus.ui.result

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.QuoteVerdict
import com.najmi.corvus.domain.model.Verdict
import com.najmi.corvus.ui.theme.CorvusShapes
import com.najmi.corvus.ui.components.EntityContextPanel
import com.najmi.corvus.ui.components.EntityContextSkeleton
import com.najmi.corvus.ui.viewmodel.CorvusViewModel
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(
    viewModel: CorvusViewModel = hiltViewModel(),
    onAnalyzeAnother: () -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.result
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    fun scrollToSource(sourceIndex: Int) {
        scope.launch {
            // Calculate index: VerdictCard(1) + Spacer(1) + BackButton(1) + ExplanationHeader(1) + Explanations(1) + ...
            // It's easier to find the index by tag or just a fixed offset if possible.
            // Sources start after everything else.
            // Let's use a simple heuristic or find the item index dynamically.
            val totalItemsBeforeSources = 8 // approximate
            listState.animateScrollToItem(totalItemsBeforeSources + sourceIndex)
        }
    }

    BackHandler(onBack = onBack)

    fun shareResult() {
        result?.let { r ->
            val sourceUrls = r.sources.joinToString("\n") { "• ${it.url}" }
            val shareText = buildString {
                appendLine("🔍 Fact Check by Corvus")
                appendLine()
                appendLine("Claim: ${r.claim}")
                appendLine()
                
                when (r) {
                    is CorvusCheckResult.GeneralResult -> {
                        appendLine("Verdict: ${r.verdict.name.replace("_", " ")}")
                        if (r.verdict == Verdict.UNVERIFIABLE && r.plausibility != null) {
                            appendLine("Plausibility: ${r.plausibility.score.name}")
                        }
                        appendLine("Confidence: ${(r.confidence * 100).toInt()}%")
                        
                        if (r.harmAssessment.level != com.najmi.corvus.domain.model.HarmLevel.NONE) {
                            appendLine()
                            appendLine("⚠️ HARM RISK: ${r.harmAssessment.level}")
                            appendLine("Category: ${r.harmAssessment.category}")
                            appendLine("Reason: ${r.harmAssessment.reason}")
                        }

                        appendLine()
                        appendLine("Explanation:")
                        appendLine(r.explanation)
                        if (r.keyFacts.isNotEmpty()) {
                            appendLine()
                            appendLine("Key Facts:")
                            r.keyFacts.forEach { appendLine("• $it") }
                        }
                    }
                    is CorvusCheckResult.QuoteResult -> {
                        appendLine("Verdict: ${r.quoteVerdict.name.replace("_", " ")}")
                        if (r.quoteVerdict == QuoteVerdict.UNVERIFIABLE && r.plausibility != null) {
                            appendLine("Plausibility: ${r.plausibility.score.name}")
                        }
                        appendLine("Speaker: ${r.speaker}")
                        appendLine("Confidence: ${(r.confidence * 100).toInt()}%")

                        if (r.harmAssessment.level != com.najmi.corvus.domain.model.HarmLevel.NONE) {
                            appendLine()
                            appendLine("⚠️ HARM RISK: ${r.harmAssessment.level}")
                            appendLine("Category: ${r.harmAssessment.category}")
                            appendLine("Reason: ${r.harmAssessment.reason}")
                        }

                        appendLine()
                        appendLine("Context:")
                        appendLine(r.contextExplanation)
                    }
                    is CorvusCheckResult.CompositeResult -> {
                        appendLine("Overall Verdict: ${r.compositeVerdict.name.replace("_", " ")}")
                        appendLine("Avg. Confidence: ${(r.confidence * 100).toInt()}%")
                        appendLine()
                        appendLine("Sub-claims:")
                        appendLine(r.compositeSummary)
                    }
                    is CorvusCheckResult.ViralHoaxResult -> {
                        appendLine("🚨 KNOWN HOAX DETECTED")
                        appendLine("Match: ${r.matchedClaim}")
                        appendLine("Summary: ${r.summary}")
                        if (r.debunkUrls.isNotEmpty()) {
                            appendLine("Sources: ${r.debunkUrls.joinToString(", ")}")
                        }
                    }
                }

                if (r.sources.isNotEmpty()) {
                    appendLine()
                    appendLine("Sources:")
                    append(sourceUrls)
                }
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Share fact check"))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        result?.let { corvusResult ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                item {
                    VerdictCard(
                        result = corvusResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = when (corvusResult) {
                            is CorvusCheckResult.QuoteResult -> "CONTEXT"
                            is CorvusCheckResult.CompositeResult -> "SUMMARY"
                            else -> "EXPLANATION"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (corvusResult is CorvusCheckResult.GeneralResult && corvusResult.missingContext != null) {
                    item {
                        var mcVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(200)
                            mcVisible = true
                        }
                        AnimatedVisibility(
                            visible = mcVisible,
                            enter = slideInVertically(
                                initialOffsetY = { -20 },
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(400))
                        ) {
                            MissingContextCallout(corvusResult.missingContext)
                        }
                    }
                }

                if (corvusResult !is CorvusCheckResult.ViralHoaxResult) {
                    item {
                        ExpandableExplanation(
                            explanation = when (corvusResult) {
                                is CorvusCheckResult.GeneralResult -> corvusResult.explanation
                                is CorvusCheckResult.QuoteResult -> corvusResult.contextExplanation
                                is CorvusCheckResult.CompositeResult -> corvusResult.compositeSummary
                                else -> ""
                            }
                        )
                    }
                }

                if (corvusResult is CorvusCheckResult.GeneralResult && corvusResult.kernelOfTruth != null) {
                    item {
                        KernelOfTruthCard(
                            kernel = corvusResult.kernelOfTruth,
                            sources = corvusResult.sources,
                            onSourceClick = { scrollToSource(it) }
                        )
                    }
                }

                if (corvusResult is CorvusCheckResult.GeneralResult && corvusResult.keyFacts.isNotEmpty()) {
                    item {
                        GroundedFactsList(
                            facts = corvusResult.keyFacts,
                            sources = corvusResult.sources,
                            onSourceClick = { scrollToSource(it) }
                        )
                    }
                }

                if (corvusResult is CorvusCheckResult.QuoteResult && corvusResult.keyFacts.isNotEmpty()) {
                    item {
                        GroundedFactsList(
                            facts = corvusResult.keyFacts,
                            sources = corvusResult.sources,
                            onSourceClick = { scrollToSource(it) }
                        )
                    }
                }

                // Entity Context Panel
                corvusResult.entityContext?.let { entity ->
                    item(key = "entity_context") {
                        var entityVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(entity) {
                            delay(450)
                            entityVisible = true
                        }
                        
                        AnimatedVisibility(
                            visible = entityVisible,
                            enter = fadeIn(tween(400)) + slideInVertically(
                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 4 }
                            )
                        ) {
                            EntityContextPanel(
                                entity = entity,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } ?: run {
                    if (uiState.isEntityContextLoading) {
                        item(key = "entity_context_skeleton") {
                            EntityContextSkeleton()
                        }
                    }
                }

                if (corvusResult.sources.isNotEmpty()) {
                    item {
                        Text(
                            text = "EVIDENCE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    itemsIndexed(corvusResult.sources) { index, source ->
                        AnimatedVisibility(
                            visible = showContent,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 280,
                                    delayMillis = 200 + (index * 80)
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 280,
                                    delayMillis = 200 + (index * 80)
                                ),
                                initialOffsetY = { it / 2 }
                            )
                        ) {
                            SourceCard(
                                source = source,
                                index = index
                            )
                        }
                    }
                }

                val timeline = when (corvusResult) {
                    is CorvusCheckResult.GeneralResult -> corvusResult.confidenceTimeline
                    is CorvusCheckResult.QuoteResult -> corvusResult.confidenceTimeline
                    is CorvusCheckResult.CompositeResult -> corvusResult.confidenceTimeline
                    else -> emptyList()
                }

                if (timeline.size >= 2) {
                    item {
                        ConfidenceTimelineCard(points = timeline)
                    }
                }

                item {
                    val methodology = (corvusResult as? CorvusCheckResult.GeneralResult)?.methodology
                    MethodologyCard(methodology)
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.reset()
                            onAnalyzeAnother()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CorvusShapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "ANALYSE",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            shareResult()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = CorvusShapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "SHARE",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableExplanation(
    explanation: String,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }
    val maxLines = 4

    val shouldShowExpand = explanation.length > 200 || explanation.lines().size > 3

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        SelectionContainer {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (shouldShowExpand && !isExpanded) maxLines else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, CorvusShapes.medium)
                    .padding(16.dp)
            )
        }

        if (shouldShowExpand) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboardManager.setText(AnnotatedString(explanation))
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy explanation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    }
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Show less" else "Show more",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    clipboardManager.setText(AnnotatedString(explanation))
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy explanation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
