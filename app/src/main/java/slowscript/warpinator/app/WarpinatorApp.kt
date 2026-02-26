package slowscript.warpinator.app

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import slowscript.warpinator.feature.about.AboutScreen
import slowscript.warpinator.feature.home.HomeScreen
import slowscript.warpinator.feature.settings.SettingsScreen

val LocalNavController = staticCompositionLocalOf<NavController?> {
    null
}

@Composable
fun WarpinatorApp(
    navController: NavHostController,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalNavController provides navController) {
            Box(Modifier.fillMaxSize()) {
                WarpinatorIntentHandler()

                NavHost(
                    navController = navController, startDestination = "home",

                    enterTransition = {
                        slideInHorizontally(initialOffsetX = { it })
                    },
                    exitTransition = {
                        scaleOut(targetScale = 0.9f)
                    },
                    popEnterTransition = {
                        scaleIn(initialScale = 0.9f)
                    },
                    popExitTransition = {
                        slideOutHorizontally(targetOffsetX = { it })
                    },
                ) {
                    composable("home") {
                        HomeScreen()
                    }

                    composable("settings") {
                        SettingsScreen()
                    }

                    composable("about") {
                        AboutScreen()
                    }
                }
            }
        }
    }
}
