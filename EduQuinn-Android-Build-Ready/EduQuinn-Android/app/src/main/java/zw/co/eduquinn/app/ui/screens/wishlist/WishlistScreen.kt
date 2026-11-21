package zw.co.eduquinn.app.ui.screens.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.cart.CartStore
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.CourseCard
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.components.SignedOutPrompt
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
) {
    val viewModel: WishlistViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WishlistViewModel(app.services.wishlistRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val cartItems by CartStore.items.collectAsState()
    val cartIds = cartItems.map { it.id }.toSet()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wishlist") }) },
    ) { padding ->
        when {
            !SessionManager.isLoggedIn -> SignedOutPrompt(
                onSignIn = { navController.navigate(Destinations.login(Destinations.WISHLIST)) },
                onCreateAccount = { navController.navigate(Destinations.REGISTER) },
                modifier = Modifier.padding(padding),
            )
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.courses.isEmpty() ->
                ErrorState(uiState.error ?: "Could not load your wishlist.", modifier = Modifier.padding(padding), onRetry = viewModel::load)
            uiState.courses.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Your wishlist is empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Tap the heart on any course to save it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.courses, key = { it.id }) { course ->
                    CourseCard(
                        course = course,
                        isWishlisted = true,
                        isInCart = course.id in cartIds,
                        onClick = { navController.navigate(Destinations.courseDetail(course.id)) },
                        onToggleWishlist = { viewModel.remove(course.id) },
                        onAddToCart = { CartStore.add(course) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
