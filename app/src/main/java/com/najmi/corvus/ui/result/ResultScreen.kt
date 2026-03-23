package com.najmi.corvus.ui.result

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.ui.theme.*
import com.najmi.corvus.domain.model.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.ui.components.EntityContextPanel
import com.najmi.corvus.ui.components.EntityContextSkeleton
import com.najmi.corvus.ui.viewmodel.CorvusViewModel
import com.najmi.corvus.domain.model.toShareText
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
    var queryExpanded by rememberSaveable { mutableStateOf(false) }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    fun scrollToSource(sourceIndex: Int) {
        scope.launch {
            // Scroll directly to the source by its key
            val targetKey = "evidence_source_$sourceIndex"
            val targetIndex = listState.layoutInfo.visibleItemsInfo.find { it.key == targetKey }?.index
            
            if (targetIndex != null) {
                listState.animateScrollToItem(targetIndex)
            } else {
                // Fallback: search total items count (less efficient but exhaustive)
                val totalIndex = (0 until listState.layoutInfo.totalItemsCount).find { 
                    // This is hard to check without custom key mapping, but we can try to scroll to the end
                    // since sources are near the bottom. 
                    // Better yet, just use a reasonable guess if not visible.
                    false 
                }
                listState.animateScrollToItem(10 + sourceIndex) // improved heuristic
            }
        }
    }

    BackHandler(onBack = onBack)

    fun shareResult() {
        result?.let { r ->
            val shareText = r.toShareText()
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
        Box(modifier = Modifier.fillMaxSize()) {
        result?.let { corvusResult ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (showContent) 1f else 0f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "top_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item(key = "back_button_row") {
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
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                item(key = "query_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                queryExpanded = !queryExpanded
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            1.dp, 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        shape = CorvusShapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "YOUR QUERY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                    
                                    if (!queryExpanded && corvusResult.claim.length > 100) {
                                        Text(
                                            text = "tap to expand",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                                Text(
                                    text = corvusResult.claim,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = if (queryExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                item(key = "verdict_card") {
                    VerdictCard(
                        result = corvusResult,
                        modifier = Modifier.fillMaxWidth(),
                        onSourceClick = { scrollToSource(it) }
                    )
                }

                item(key = "explanation_header") {
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
                    item(key = "missing_context") {
                        var mcVisible by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            if (!mcVisible) {
                                delay(200)
                                mcVisible = true
                            }
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
                    val explanation = when (corvusResult) {
                        is CorvusCheckResult.GeneralResult -> corvusResult.explanation
                        is CorvusCheckResult.QuoteResult -> corvusResult.contextExplanation
                        is CorvusCheckResult.CompositeResult -> corvusResult.compositeSummary
                        else -> ""
                    }

                    if (explanation.isNotBlank()) {
                        item(key = "expandable_explanation") {
                            ExpandableExplanation(explanation = explanation)
                        }
                    }
                }

                if (corvusResult is CorvusCheckResult.GeneralResult && corvusResult.kernelOfTruth != null) {
                    item(key = "kernel_of_truth") {
                        KernelOfTruthCard(
                            kernel = corvusResult.kernelOfTruth,
                            sources = corvusResult.sources,
                            onSourceClick = { scrollToSource(it) }
                        )
                    }
                }

                if (corvusResult is CorvusCheckResult.GeneralResult && corvusResult.keyFacts.isNotEmpty()) {
                    item(key = "key_facts_general") {
                        GroundedFactsList(
                            facts = corvusResult.keyFacts,
                            sources = corvusResult.sources,
                            onSourceClick = { scrollToSource(it) }
                        )
                    }
                }

                if (corvusResult is CorvusCheckResult.QuoteResult && corvusResult.keyFacts.isNotEmpty()) {
                    item(key = "key_facts_quote") {
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
                    item(key = "evidence_header") {
                        Text(
                            text = "EVIDENCE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    itemsIndexed(
                        items = corvusResult.sources,
                        key = { index, _ -> "source_$index" }
                    ) { index, source ->
                        var sourceVisible by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            if (!sourceVisible) {
                                delay(200 + (index * 80L))
                                sourceVisible = true
                            }
                        }

                        AnimatedVisibility(
                            visible = sourceVisible,
                            enter = fadeIn(
                                animationSpec = tween(durationMillis = 280)
                            ) + slideInVertically(
                                animationSpec = tween(durationMillis = 280),
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
                    item(key = "confidence_timeline") {
                        ConfidenceTimelineCard(points = timeline)
                    }
                }

                item(key = "methodology") {
                    val methodology = (corvusResult as? CorvusCheckResult.GeneralResult)?.methodology
                    MethodologyCard(methodology)
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
            
            AnimatedVisibility(
                visible = showScrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 110.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        } ?: run {
            // Empty state fallback
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No result available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Try analysing a claim first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onAnalyzeAnother() },
                    shape = CorvusShapes.small
                ) {
                    Text("GO TO INPUT")
                }
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
                        contentDescription = "Analyse another",
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
                        contentDescription = "Share result",
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
