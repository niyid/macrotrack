package com.techducat.macrotrack.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.techducat.macrotrack.R
import com.techducat.macrotrack.ui.addentry.AddEntryScreen
import com.techducat.macrotrack.ui.diary.DiaryScreen
import com.techducat.macrotrack.ui.goals.GoalsScreen
import com.techducat.macrotrack.ui.scan.ScanScreen
import com.techducat.macrotrack.ui.search.SearchScreen

private object Routes {
    const val DIARY = "diary"
    const val SEARCH = "search"
    const val SCAN = "scan"
    const val GOALS = "goals"
    const val ADD_ENTRY = "add_entry/{foodId}"

    fun addEntry(foodId: String) = "add_entry/$foodId"
}

private data class BottomTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.DIARY, R.string.nav_diary, Icons.Filled.RestaurantMenu),
    BottomTab(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
    BottomTab(Routes.SCAN, R.string.nav_scan, Icons.Filled.CameraAlt),
    BottomTab(Routes.GOALS, R.string.nav_goals, Icons.Filled.BarChart)
)

@Composable
fun MacroTrackNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                bottomTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DIARY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DIARY) { DiaryScreen() }

            composable(Routes.SEARCH) {
                SearchScreen(onFoodSelected = { food ->
                    navController.navigate(Routes.addEntry(food.id))
                })
            }

            composable(Routes.SCAN) {
                ScanScreen(onFoodConfirmed = { food ->
                    navController.navigate(Routes.addEntry(food.id))
                })
            }

            composable(Routes.GOALS) { GoalsScreen() }

            composable(
                route = Routes.ADD_ENTRY,
                arguments = listOf(navArgument("foodId") { type = NavType.StringType })
            ) {
                AddEntryScreen(onLogged = {
                    navController.navigate(Routes.DIARY) {
                        popUpTo(Routes.DIARY) { inclusive = true }
                    }
                })
            }
        }
    }
}
