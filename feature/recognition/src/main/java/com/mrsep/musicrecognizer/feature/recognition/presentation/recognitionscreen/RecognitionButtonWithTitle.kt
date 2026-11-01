package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.ui.theme.PlusColors
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun RecognitionButtonWithTitle(
    title: String,
    activated: Boolean,
    soundLevelState: State<Float>,
    onButtonClick: () -> Unit,
    onButtonLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        header()
        AnimatedContent(
            contentAlignment = Alignment.Center,
            targetState = title,
            transitionSpec = {
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(durationMillis = 350)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 300, easing = EaseOut)
                )).togetherWith(
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(durationMillis = 350)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 150, easing = EaseIn)
                    )
                )
            },
            label = "buttonTitleTransition"
        ) {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            )
        }

        RecognitionButton(
            onClick = onButtonClick,
            onLongClick = onButtonLongClick,
            activated = activated,
            soundLevelState = soundLevelState,
        )
    }
}

@Composable
private fun RecognitionButton(
    activated: Boolean,
    soundLevelState: State<Float>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        RippleEffect(
            activated = activated,
            modifier = Modifier.size(300.dp),
            startOffsetFraction = 0.5f, // Should be a bit less than buttonSize/rippleSize
            baseColor = Color.White.copy(alpha = 0.07f),
            activatedColor = Color.White.copy(alpha = 0.3f),
            circlesCount = 7,
            animationSpeed = 12_000,
            scaleEasing = LinearEasing,
            fadeEasing = LinearEasing,
        )
        BasicRecognitionButton(
            modifier = Modifier
                .size(208.dp),
            activated = activated,
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            WaveAnimated(
                activated = activated,
                soundLevelState = soundLevelState,
                properties = WaveAnimatedProperties(
                    baseColor = Color.White.copy(alpha = 0.9f),
                    activatedColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxHeight(0.65f)
                    .fillMaxWidth(0.9f)
            )
        }
    }
}

@Composable
private fun BasicRecognitionButton(
    activated: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonScaleFactor = remember { Animatable(1f) }
    LaunchedEffect(activated) {
        buttonScaleFactor.animateTo(
            targetValue = 1.1f,
            animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        buttonScaleFactor.animateTo(
            targetValue = 1f,
            animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )
    }
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = buttonScaleFactor.value
                scaleY = buttonScaleFactor.value
            }
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = PlusColors.SkyBottom,
                spotColor = PlusColors.SkyBottom
            )
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onClickLabel = stringResource(
                    if (activated) StringsR.string.action_cancel_recognition
                    else StringsR.string.action_recognize
                ),
                onLongClick = onLongClick.takeIf { !activated },
                onLongClickLabel = stringResource(StringsR.string.action_recognize).takeIf { !activated },
                hapticFeedbackEnabled = false,
                role = Role.Button
            ),
        shape = CircleShape,
        color = PlusColors.Frost,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight,
                )
                .padding(ButtonDefaults.ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}
