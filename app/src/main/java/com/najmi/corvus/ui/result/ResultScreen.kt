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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najmi.corvus.ui.theme.*
import com.najmi.corvus.domain.model.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.najmi.corvus.ui.viewmodel.CorvusViewModel
import com.najmi.corvus.ui.components.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    viewModel: CorvusViewModel = hiltViewModel(),
    onAnalyzeAnother: () -> Unit,
    onBack: () -> Unit = {},
    onRequestHumanReview: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.result

    LaunchedEffect(uiState.showHumanReviewScreen) {
        if (uiState.showHumanReviewScreen) {
            onRequestHumanReview?.invoke()
        }
    }
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    var showContent by remember { mutableStateOf(false) }
    var queryExpanded by rememberSaveable { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNotes by remember { mutableStateOf("") }
    var isCurrentResultBookmarked by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        result?.let {
            isCurrentResultBookmarked = viewModel.isCurrentResultBookmarked()
        }
    }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    val showStickyStrip by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }

    // Hide-on-scroll logic for bottom bar
    val density = LocalDensity.current
    val bottomBarHeight = 120.dp
    val bottomBarHeightPx = with(density) { bottomBarHeight.toPx() }
    var bottomBarOffsetHeightPx by remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx - delta
                bottomBarOffsetHeightPx = newOffset.coerceIn(0f, bottomBarHeightPx)
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    fun scrollToSource(sourceId: String, sourceIndex: Int) {
        scope.launch {
            val targetKey = "source_${sourceId}"
            val targetIndex = listState.layoutInfo.visibleItemsInfo.find { it.key == targetKey }?.index
            
            if (targetIndex != null) {
                listState.animateScrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(14 + sourceIndex)
            }
        }
    }

    BackHandler(onBack = onBack)

    fun shareResult() {
        result?.let { r ->
            val shareText = r.toShareText()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText as String)
            }
            context.startActivity(Intent.createChooser(intent, "Share fact check"))
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (result != null) {
                val sources = result.sources
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "top_spacer") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item(key = "back_button_row") {
                        StaggeredReveal(index = 0, show = showContent) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            }
                        }
                    }

                    // 1. PRIMARY FINDING BANNERS (Recency/Viral short-circuits)
                    if (result is CorvusCheckResult.GeneralResult) {
                        if (result.verdict == Verdict.RECENCY_UNVERIFIABLE) {
                            item(key = "recency_banner") {
                                StaggeredReveal(index = 1, show = showContent) {
                                    ZombieClaimWarningBanner(result.temporalMismatch ?: TemporalMismatchReport(true, 0, 0, 0, 0, emptyList(), null))
                                }
                            }
                        }
                        
                        if (result.viralDetection is ViralDetectionResult.KnownHoax && 
                            result.verdict == Verdict.RECENCY_UNVERIFIABLE) {
                            item(key = "viral_banner") {
                                StaggeredReveal(index = 2, show = showContent) {
                                    ViralHoaxResultCard(CorvusCheckResult.ViralHoaxResult(
                                        claim = result.claim,
                                        matchedClaim = (result.viralDetection as ViralDetectionResult.KnownHoax).matchedClaim,
                                        debunkUrls = (result.viralDetection as ViralDetectionResult.KnownHoax).debunkUrls
                                    ))
                                }
                            }
                        }
                    }

                    // 2. VERDICT CARD — always the first substantive item
                    item(key = "verdict_card") {
                        StaggeredReveal(index = 3, show = showContent) {
                            VerdictCard(
                                result = result,
                                modifier = Modifier.fillMaxWidth(),
                                onSourceClick = { index -> 
                                    val source = sources.getOrNull(index)
                                    if (source != null) {
                                        scrollToSource(source.id, index)
                                    }
                                }
                            )
                        }
                    }

                    // 2.5 EMPTY & ERROR STATES
                    if (result is CorvusCheckResult.GeneralResult) {
                        when (result.verificationStatus) {
                            VerificationStatus.NO_SOURCES_FOUND -> {
                                item(key = "no_sources_card") {
                                    StaggeredReveal(index = 4, show = showContent) {
                                        NoSourcesFoundCard(
                                            reasons = result.noSourceReasons,
                                            onRequestHumanReview = {
                                                viewModel.requestHumanReview()
                                                onRequestHumanReview?.invoke()
                                            }
                                        )
                                    }
                                }
                            }
                            VerificationStatus.API_FAILURE -> {
                                item(key = "api_failure_card") {
                                    StaggeredReveal(index = 4, show = showContent) {
                                        ApiFailureCard(
                                            errorMessage = result.apiErrorMessage ?: "Unknown error",
                                            partialResultsAvailable = result.savedProgress != null,
                                            onRetry = { viewModel.retryVerification() },
                                            onNotifyWhenComplete = { viewModel.notifyWhenComplete() }
                                        )
                                    }
                                }
                            }
                            VerificationStatus.LOW_CONFIDENCE -> {
                                item(key = "low_confidence_card") {
                                    StaggeredReveal(index = 4, show = showContent) {
                                        LowConfidenceCard(
                                            currentConfidence = result.confidence,
                                            knownFactors = result.sources.mapNotNull { it.title }.take(3),
                                            helpRequests = result.helpRequests
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    // 3. TEMPORAL CONTEXT BANNER
                    if (result is CorvusCheckResult.GeneralResult && 
                        (result.temporalMismatch?.hasSignificantMismatch == true || result.recencySignal != null)) {
                        item(key = "temporal_banner") {
                            StaggeredReveal(index = 5, show = showContent) {
                                TemporalContextBanner(
                                    mismatchReport = result.temporalMismatch,
                                    recencySignal = result.recencySignal,
                                    onLearnMore = { /* Open learn more URL */ }
                                )
                            }
                        }
                    }

                    // 3. MISSING CONTEXT / MISLEADING ELEMENTS
                    if (result is CorvusCheckResult.GeneralResult && result.missingContext != null) {
                        item(key = "missing_context") {
                            StaggeredReveal(index = 4, show = showContent) {
                                MissingContextCallout(result.missingContext!!)
                            }
                        }
                    }

                    // 4. ANALYZED CLAIM
                    item(key = "query_card") {
                        StaggeredReveal(index = 5, show = showContent) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clickable {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        queryExpanded = !queryExpanded
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                                ),
                                border = BorderStroke(
                                    1.dp, 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ),
                                shape = CorvusShapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "ANALYZED CLAIM",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                ),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                letterSpacing = 1.sp
                                            )
                                            
                                            if (!queryExpanded && result.claim.length > 100) {
                                                Text(
                                                    text = "expand",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                    fontStyle = FontStyle.Italic
                                                )
                                            }
                                        }
                                        Text(
                                            text = result.claim,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                            maxLines = if (queryExpanded) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. EXPLANATION
                    item(key = "explanation_header") {
                        StaggeredReveal(index = 6, show = showContent) {
                            Text(
                                text = when (result) {
                                    is CorvusCheckResult.GeneralResult -> "EXPLANATION"
                                    is CorvusCheckResult.QuoteResult -> "CONTEXT & ORIGIN"
                                    is CorvusCheckResult.CompositeResult -> "SUMMARY"
                                    else -> "RESULT DETAILS"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    item(key = "explanation_text") {
                        StaggeredReveal(index = 7, show = showContent) {
                            val explanation = when (result) {
                                is CorvusCheckResult.GeneralResult -> result.explanation
                                is CorvusCheckResult.QuoteResult -> result.contextExplanation
                                is CorvusCheckResult.CompositeResult -> result.compositeSummary
                                else -> ""
                            }
                            
                            if (explanation.isNotBlank()) {
                                ExpandableExplanation(explanation = explanation)
                            }
                        }
                    }

                    // 6. KERNEL OF TRUTH (for misleading claims)
                    if (result is CorvusCheckResult.GeneralResult && result.kernelOfTruth != null) {
                        item(key = "kernel_of_truth_header") {
                            StaggeredReveal(index = 8, show = showContent) {
                                Text(
                                    text = "KERNEL OF TRUTH",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                        
                        item(key = "kernel_card") {
                            StaggeredReveal(index = 9, show = showContent) {
                                KernelOfTruthCard(
                                    kernel = result.kernelOfTruth,
                                    sources = sources,
                                    onSourceClick = { scrollToSource(sources[it].id, it) }
                                )
                            }
                        }
                    }

                    // 7. GROUNDED FACTS
                    val groundedFacts = when (result) {
                        is CorvusCheckResult.GeneralResult -> result.keyFacts
                        is CorvusCheckResult.QuoteResult -> result.keyFacts
                        else -> emptyList()
                    }

                    if (groundedFacts.isNotEmpty()) {
                        item(key = "grounded_facts_header") {
                            StaggeredReveal(index = 10, show = showContent) {
                                Text(
                                    text = "GROUNDED FACTS",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                        
                        item(key = "grounded_facts_list") {
                            StaggeredReveal(index = 11, show = showContent) {
                                GroundedFactsList(
                                    facts = groundedFacts,
                                    sources = sources,
                                    onSourceClick = { scrollToSource(sources[it].id, it) }
                                )
                            }
                        }
                    }

                    // 8. ENTITY CONTEXT
                    val entity = result.entityContext
                    if (entity != null) {
                        item(key = "entity_context") {
                            StaggeredReveal(index = 12, show = showContent) {
                                EntityContextPanel(entity = entity, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    // 9. EVIDENCE & SOURCES
                    if (sources.isNotEmpty()) {
                        item(key = "sources_header") {
                            StaggeredReveal(index = 13, show = showContent) {
                                Text(
                                    text = "EVIDENCE & SOURCES",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }

                        itemsIndexed(
                            items = sources,
                            key = { _, source -> "source_${source.id}" }
                        ) { index, source ->
                            StaggeredReveal(index = 14 + index, show = showContent) {
                                SourceCard(
                                    source = source,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 10. CONFIDENCE TIMELINE
                    val timeline = when (result) {
                        is CorvusCheckResult.GeneralResult -> result.confidenceTimeline
                        is CorvusCheckResult.QuoteResult -> result.confidenceTimeline
                        is CorvusCheckResult.CompositeResult -> result.confidenceTimeline
                        else -> emptyList<ConfidencePoint>()
                    }

                    if (timeline.size >= 2) {
                        item(key = "timeline_header") {
                            StaggeredReveal(index = 15 + sources.size, show = showContent) {
                                Text(
                                    text = "ANALYSIS TIMELINE",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                        item(key = "confidence_timeline") {
                            StaggeredReveal(index = 16 + sources.size, show = showContent) {
                                ConfidenceTimelineCard(
                                    points = timeline,
                                    sources = sources,
                                    showSourceAttribution = true,
                                    onConfidenceDrop = { point, prevConf, source, idx ->
                                        // Dialog is handled internally by the card
                                    }
                                )
                            }
                        }
                    }

                    // 11. METHODOLOGY
                    item(key = "methodology") {
                        StaggeredReveal(index = 17 + sources.size, show = showContent) {
                            MethodologyCard(result)
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Scroll to top FAB
                AnimatedVisibility(
                    visible = showScrollToTop && !showStickyStrip,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 120.dp, end = 16.dp)
                        .graphicsLayer { translationY = bottomBarOffsetHeightPx }
                ) {
                    FloatingActionButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                    }
                }

                // STICKY VERDICT STRIP
                AnimatedVisibility(
                    visible = showStickyStrip,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    StickyVerdictStrip(
                        result = result,
                        onBack = onBack,
                        onScrollToTop = {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    )
                }
            } else if (uiState.isLoading) {
                CorvusResultSkeleton()
            }

            // Floating Bottom Actions
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ResultBottomActions(
                    onAnalyzeAnother = { 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnalyzeAnother() 
                    },
                    onShare = { 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        shareResult() 
                    },
                    onBookmark = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isCurrentResultBookmarked) {
                            // Already bookmarked - could show a message
                        } else {
                            showBookmarkDialog = true
                        }
                    },
                    isBookmarked = isCurrentResultBookmarked,
                    offsetY = bottomBarOffsetHeightPx,
                    maxHeightPx = bottomBarHeightPx
                )
            }
        }
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("Add Bookmark") },
            text = {
                Column {
                    Text(
                        text = "Save this fact-check result for later reference.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = bookmarkNotes,
                        onValueChange = { bookmarkNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add a note (optional)") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addBookmark(bookmarkNotes)
                        isCurrentResultBookmarked = true
                        showBookmarkDialog = false
                        bookmarkNotes = ""
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StaggeredReveal(
    index: Int,
    show: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = index * 120)) +
                slideInVertically(
                    initialOffsetY = { it / 4 }, // Start from 1/4 of the height lower
                    animationSpec = tween(durationMillis = 600, delayMillis = index * 120)
                ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun StickyVerdictStrip(
    result: CorvusCheckResult,
    onBack: () -> Unit,
    onScrollToTop: () -> Unit
) {
    Surface(
        onClick = onScrollToTop,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val verdict = result.getVerdictDisplayName()
                val color = result.getVerdictColor()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = verdict.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    )
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(result.confidence)
                                .fillMaxHeight()
                                .background(color, CircleShape)
                        )
                    }
                }
                
                Text(
                    text = result.claim,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ResultBottomActions(
    onAnalyzeAnother: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    isBookmarked: Boolean,
    offsetY: Float,
    maxHeightPx: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { 
                // Translate down to hide (offsetY is 0 to maxHeightPx)
                translationY = offsetY 
                // Fade out smoothly as it moves
                alpha = (1f - (offsetY / maxHeightPx)).coerceIn(0f, 1f)
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAnalyzeAnother,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = CorvusShapes.medium
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("ANALYSE")
                }

                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = CorvusShapes.medium
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("SHARE")
                }
            }

            OutlinedButton(
                onClick = onBookmark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (isBookmarked) "BOOKMARKED" else "BOOKMARK FOR LATER")
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CorvusShapes.medium)
                    .padding(16.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    clipboardManager.setText(AnnotatedString(explanation))
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
            }

            if (shouldShowExpand) {
                TextButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    }
                ) {
                    Text(if (isExpanded) "SHOW LESS" else "SHOW MORE")
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun CorvusCheckResult.getVerdictDisplayName(): String {
    return when (this) {
        is CorvusCheckResult.GeneralResult -> verdict.displayName()
        is CorvusCheckResult.QuoteResult -> quoteVerdict.displayName()
        is CorvusCheckResult.CompositeResult -> compositeVerdict.displayName()
        else -> "Result"
    }
}

@Composable
private fun CorvusCheckResult.getVerdictColor(): Color {
    return when (this) {
        is CorvusCheckResult.GeneralResult -> getVerdictColor(verdict)
        is CorvusCheckResult.QuoteResult -> getQuoteVerdictColor(quoteVerdict)
        is CorvusCheckResult.CompositeResult -> getVerdictColor(compositeVerdict)
        else -> Color.Gray
    }
}
