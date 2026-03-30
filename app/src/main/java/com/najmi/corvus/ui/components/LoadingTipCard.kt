package com.najmi.corvus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.najmi.corvus.ui.theme.CorvusBorder
import com.najmi.corvus.ui.theme.CorvusShapes
import kotlinx.coroutines.delay

private val loadingTips = listOf(
    "Claims are classified by type: Scientific claims need peer review, statistical claims need data verification.",
    "We rewrite your claim into multiple search queries to find evidence you might have missed.",
    "Searching Tavily, Wikipedia, and Google Fact Check simultaneously...",
    "Wikidata contains over 100 million structured facts about people, places, and events.",
    "Source credibility is dynamic—outlets can improve or decline based on track record.",
    "Zombie hoaxes are false claims that resurface years later with new dates attached.",
    "Actor-Critic architecture: One AI drafts the analysis, another audits it for errors.",
    "Breaking news often lacks verification—temporal analysis checks if evidence is current.",
    "We algorithmically match every AI citation against original source text.",
    "RAG verification ensures the final explanation is grounded in retrieved evidence.",
    "Token stewardship keeps Corvus free by optimizing across multiple AI providers."
)

@Composable
fun LoadingTipCard(
    modifier: Modifier = Modifier
) {
    var tipIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            tipIndex = (tipIndex + 1) % loadingTips.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CorvusShapes.medium
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "Did you know?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedContent(
            targetState = tipIndex,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "tipTransition"
        ) { index ->
            Text(
                text = loadingTips[index],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
