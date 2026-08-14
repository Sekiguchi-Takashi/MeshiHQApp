package jp.appathy.meshihq.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.ui.detail.DetailScreen
import jp.appathy.meshihq.ui.edit.EditScreen
import jp.appathy.meshihq.ui.home.HomeScreen
import jp.appathy.meshihq.ui.import_.ImportScreen
import jp.appathy.meshihq.ui.map.MapScreen
import jp.appathy.meshihq.ui.settings.SettingsScreen
import jp.appathy.meshihq.ui.stats.StatsScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "ホーム", Icons.Filled.Home),
    MAP("map", "地図", Icons.Filled.Map),
    IMPORT("import", "取込", Icons.Filled.CloudDownload),
    STATS("stats", "記録", Icons.Filled.BarChart),
    SETTINGS("settings", "設定", Icons.Filled.Settings)
}

@Composable
fun MeshiNavHost(repository: ShopRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            val routeBase = currentRoute?.substringBefore("?")
            if (routeBase != null && Tab.entries.any { it.route == routeBase }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = routeBase == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Tab.HOME.route)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    repository = repository,
                    onOpenShop = { navController.navigate("detail/$it") },
                    onNewShop = { navController.navigate("edit?id=0&lat=0&lon=0") }
                )
            }
            composable(Tab.MAP.route) {
                MapScreen(
                    repository = repository,
                    onOpenShop = { navController.navigate("detail/$it") },
                    onNewShopAt = { lat, lon ->
                        navController.navigate("edit?id=0&lat=$lat&lon=$lon")
                    },
                    onImportHere = { lat, lon ->
                        navController.navigate("import?lat=$lat&lon=$lon")
                    }
                )
            }
            composable(
                route = "import?lat={lat}&lon={lon}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.FloatType; defaultValue = 0f },
                    navArgument("lon") { type = NavType.FloatType; defaultValue = 0f }
                )
            ) { entry ->
                ImportScreen(
                    repository = repository,
                    centerLat = entry.arguments?.getFloat("lat")?.toDouble() ?: 0.0,
                    centerLon = entry.arguments?.getFloat("lon")?.toDouble() ?: 0.0,
                    onOpenShop = { navController.navigate("detail/$it") }
                )
            }
            composable(Tab.STATS.route) {
                StatsScreen(
                    repository = repository,
                    onOpenShop = { navController.navigate("detail/$it") }
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(repository = repository)
            }
            composable(
                route = "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                DetailScreen(
                    repository = repository,
                    shopId = entry.arguments?.getLong("id") ?: 0L,
                    onEdit = { navController.navigate("edit?id=$it&lat=0&lon=0") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "edit?id={id}&lat={lat}&lon={lon}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("lat") { type = NavType.FloatType; defaultValue = 0f },
                    navArgument("lon") { type = NavType.FloatType; defaultValue = 0f }
                )
            ) { entry ->
                EditScreen(
                    repository = repository,
                    shopId = entry.arguments?.getLong("id") ?: 0L,
                    initialLat = entry.arguments?.getFloat("lat")?.toDouble() ?: 0.0,
                    initialLon = entry.arguments?.getFloat("lon")?.toDouble() ?: 0.0,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
