package com.mrsep.musicrecognizer.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mrsep.musicrecognizer.core.ui.theme.PlusColors
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
fun AppNavigationBar(
    unviewedTracksCount: State<Int>,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Audile+: blue like the Recognition screen above it, the theme colours on the other tabs.
    val isBlue = currentDestination.isDestinationInHierarchy(TopLevelDestination.Recognition)
    val containerColor by animateColorAsState(
        if (isBlue) PlusColors.SkyBottom else NavigationBarDefaults.containerColor, label = "barColor"
    )
    val itemColors = if (isBlue) {
        NavigationBarItemDefaults.colors(
            selectedIconColor = PlusColors.SkyBottom,
            selectedTextColor = Color.White,
            indicatorColor = Color.White,
            unselectedIconColor = Color.White.copy(alpha = 0.85f),
            unselectedTextColor = Color.White.copy(alpha = 0.85f),
        )
    } else {
        NavigationBarItemDefaults.colors()
    }
    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
    ) {
        TopLevelDestination.entries.forEach { destinationEntry ->
            val selected = currentDestination.isDestinationInHierarchy(destinationEntry)
            NavigationBarItem(
                selected = selected,
                icon = {
                    when (destinationEntry) {
                        TopLevelDestination.Library -> LibraryNavigationIcon(
                            selected = selected,
                            unviewedTracksCount = unviewedTracksCount
                        )

                        else -> Icon(
                            painter = painterResource(
                                if (selected) destinationEntry.selectedIconResId
                                else destinationEntry.unselectedIconResId
                            ),
                            contentDescription = stringResource(destinationEntry.titleResId)
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(destinationEntry.titleResId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true, // Audile+: names always shown, so the icons stay put
                colors = itemColors,
                onClick = {
                    navController.navigate(destinationEntry.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

fun NavDestination?.isDestinationInHierarchy(destination: TopLevelDestination) =
    this?.hierarchy?.any { it.route == destination.route } == true

@Composable
fun LibraryNavigationIcon(
    selected: Boolean,
    unviewedTracksCount: State<Int>,
) {
    val badgeText = if (unviewedTracksCount.value < 1000) "${unviewedTracksCount.value}" else "999+"
    BadgedBox(
        badge = {
            // delay the entrance to avoid showing while animation after recognition is playing
            androidx.compose.animation.AnimatedVisibility(
                visible = unviewedTracksCount.value != 0,
                enter = fadeIn(tween(delayMillis = 1000)) + scaleIn(tween(delayMillis = 1000)),
                exit = fadeOut(tween()) + scaleOut(tween()),
                label = "Badge visibility",
            ) {
                Badge {
                    AnimatedContent(
                        targetState = badgeText,
                        transitionSpec = {
                            (slideInVertically { fullHeight -> fullHeight } + fadeIn())
                                .togetherWith(
                                    slideOutVertically { fullHeight -> -fullHeight } + fadeOut()
                                )
                        },
                        contentAlignment = Alignment.Center,
                        label = "Counter animation",
                    ) { countText ->
                        val badgeDescription = stringResource(
                            StringsR.string.format_amount_of_new_tracks,
                            countText
                        )
                        Text(
                            text = countText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics {
                                contentDescription = badgeDescription
                            }
                        )
                    }
                }
            }
        }
    ) {
        Icon(
            painter = painterResource(
                if (selected) TopLevelDestination.Library.selectedIconResId
                else TopLevelDestination.Library.unselectedIconResId
            ),
            contentDescription = stringResource(TopLevelDestination.Library.titleResId)
        )
    }
}
