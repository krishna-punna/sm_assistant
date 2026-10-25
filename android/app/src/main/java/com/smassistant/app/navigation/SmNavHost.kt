package com.smassistant.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smassistant.app.ui.screens.AgentSettingsScreen
import com.smassistant.app.ui.screens.AiAgentListScreen
import com.smassistant.app.ui.screens.ApiStatusScreen
import com.smassistant.app.ui.screens.AssistantHubScreen
import com.smassistant.app.ui.screens.ContentAndImageScreen
import com.smassistant.app.ui.screens.DefaultHashtagsScreen
import com.smassistant.app.ui.screens.FeatureScreen
import com.smassistant.app.ui.screens.HomeScreen
import com.smassistant.app.ui.screens.ImageScreen
import com.smassistant.app.ui.screens.LinkedInSettingsScreen
import com.smassistant.app.ui.screens.PreparePromptScreen
import com.smassistant.app.ui.screens.TechnicalContentScreen

@Composable
fun SmNavHost(navController: NavHostController = rememberNavController()) {
    // "Back to Home" always returns to Home and clears everything above it.
    val backToHome: () -> Unit = { navController.popBackStack(Routes.HOME, inclusive = false) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { navController.navigate(it) })
        }
        composable(Routes.LINKEDIN_SETTINGS) {
            LinkedInSettingsScreen(onBackToHome = backToHome)
        }
        composable(Routes.AI_AGENTS) {
            AiAgentListScreen(
                onAgentSelected = { navController.navigate(Routes.agentSettings(it)) },
                onBackToHome = backToHome,
            )
        }
        composable(
            Routes.AGENT_SETTINGS,
            arguments = listOf(navArgument("agent") { type = NavType.StringType }),
        ) { entry ->
            val agent = AiAgent.valueOf(entry.arguments?.getString("agent")!!)
            AgentSettingsScreen(agent = agent, onBackToHome = backToHome)
        }
        composable(Routes.HUB) {
            AssistantHubScreen(
                onFeatureSelected = { navController.navigate(Routes.feature(it)) },
                onBackToHome = backToHome,
            )
        }
        composable(
            Routes.FEATURE,
            arguments = listOf(navArgument("feature") { type = NavType.StringType }),
        ) { entry ->
            val feature = Feature.valueOf(entry.arguments?.getString("feature")!!)
            when (feature) {
                Feature.PREPARE_PROMPT -> PreparePromptScreen(
                    onSetUpAgent = { navController.navigate(Routes.AI_AGENTS) },
                    onBackToHome = backToHome,
                )
                Feature.TECHNICAL_CONTENT -> TechnicalContentScreen(
                    onSetUpAgent = { navController.navigate(Routes.AI_AGENTS) },
                    onSetUpLinkedIn = { navController.navigate(Routes.LINKEDIN_SETTINGS) },
                    onBackToHome = backToHome,
                )
                Feature.IMAGE -> ImageScreen(
                    onSetUpAgent = { navController.navigate(Routes.AI_AGENTS) },
                    onSetUpLinkedIn = { navController.navigate(Routes.LINKEDIN_SETTINGS) },
                    onBackToHome = backToHome,
                )
                Feature.CONTENT_AND_IMAGE -> ContentAndImageScreen(
                    onSetUpAgent = { navController.navigate(Routes.AI_AGENTS) },
                    onSetUpLinkedIn = { navController.navigate(Routes.LINKEDIN_SETTINGS) },
                    onBackToHome = backToHome,
                )
                else -> FeatureScreen(feature = feature, onBackToHome = backToHome)
            }
        }
        composable(Routes.DEFAULT_HASHTAGS) {
            DefaultHashtagsScreen(onBackToHome = backToHome)
        }
        composable(Routes.API_STATUS) {
            ApiStatusScreen(onBackToHome = backToHome)
        }
    }
}
