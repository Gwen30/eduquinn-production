package zw.co.eduquinn.app.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.cart.CartStore
import zw.co.eduquinn.app.ui.components.CourseCard
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
) {
    val viewModel: CatalogViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CatalogViewModel(app.services.courseRepository, app.services.wishlistRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val cartItems by CartStore.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EduQuinn") },
                actions = {
                    IconButton(
                        onClick = {
                            if (cartItems.isNotEmpty()) {
                                navController.navigate(Destinations.checkout(cartItems.joinToString(",") { it.id }))
                            }
                        },
                    ) {
                        BadgedBox(badge = { if (cartItems.isNotEmpty()) Badge { Text("${cartItems.size}") } }) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.allCourses.isEmpty() ->
                ErrorState(uiState.error ?: "Could not load courses.", modifier = Modifier.padding(padding), onRetry = viewModel::load)
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search courses") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )

                uiState.stats?.let { stats ->
                    Text(
                        "${stats.courses} courses · ${stats.instructors} instructors · ${stats.students} students",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.visibleCourses, key = { it.id }) { course ->
                        CourseCard(
                            course = course,
                            isWishlisted = course.id in uiState.wishlistIds,
                            onClick = { navController.navigate(Destinations.courseDetail(course.id)) },
                            onToggleWishlist = {
                                if (!viewModel.toggleWishlist(course.id)) {
                                    navController.navigate(Destinations.login(Destinations.CATALOG))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
