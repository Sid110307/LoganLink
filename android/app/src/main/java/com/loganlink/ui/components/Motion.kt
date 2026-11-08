package com.loganlink.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun Modifier.reveal(): Modifier = composed {
    if (LocalInspectionMode.current) return@composed this
    val anim = remember { Animatable(0f) }
    var y by remember { mutableStateOf<Float?>(null) }
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val rise = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(y != null) {
        val top = y ?: return@LaunchedEffect
        delay((top / screenH).coerceIn(0f, 1f).times(160).toLong())
        anim.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    this
        .onGloballyPositioned { if (y == null) y = it.positionInRoot().y }
        .graphicsLayer { alpha = anim.value; translationY = (1f - anim.value) * rise }
}

fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

fun Modifier.fadingEdges(state: ScrollState, edge: Float = 28f): Modifier = composed {
    val px = with(LocalDensity.current) { edge.dp.toPx() }
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topA = (state.value / px).coerceIn(0f, 1f)
            val botA = ((state.maxValue - state.value) / px).coerceIn(0f, 1f)
            if (topA > 0f) drawRect(Brush.verticalGradient(listOf(Color.Transparent.copy(alpha = 1f - topA), Color.Black), 0f, px),
                size = size.copy(height = px), blendMode = BlendMode.DstIn)
            if (botA > 0f) drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent.copy(alpha = 1f - botA)), size.height - px, size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - px), size = size.copy(height = px), blendMode = BlendMode.DstIn)
        }
}

fun Modifier.fadeScroll(): Modifier = composed {
    val s = rememberScrollState()
    fadingEdges(s).verticalScroll(s)
}

@Composable
fun TypewriterText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, perCharMs: Long = 28) {
    val preview = LocalInspectionMode.current
    var shown by rememberSaveable(text) { mutableIntStateOf(if (preview) text.length else 0) }
    LaunchedEffect(text) { while (shown < text.length) { delay(perCharMs); shown++ } }
    Text(text.take(shown), modifier, style = style, color = color)
}

@Composable
fun MorphText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    AnimatedContent(
        text, modifier,
        transitionSpec = {
            (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160)))
        },
        label = "morph",
    ) { Text(it, style = style, color = color, maxLines = 2) }
}
