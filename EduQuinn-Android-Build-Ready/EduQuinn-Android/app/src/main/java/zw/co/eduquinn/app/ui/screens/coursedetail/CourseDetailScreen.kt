package zw.co.eduquinn.app.ui.screens.coursedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.components.PriceTag
import zw.co.eduquinn.app.ui.components.RatingStars
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    courseId: String,
) {
    val viewModel: CourseDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CourseDetailViewModel(courseId, app.services.courseRepository, app.services.wishlistRepository)
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.addedToCartMessage) {
        uiState.addedToCartMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissCartMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.course?.title ?: "Course") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!viewModel.toggleWishlist()) {
                            navController.navigate(Destinations.login(Destinations.courseDetail(courseId)))
                        }
                    }) {
                        Icon(
                            if (uiState.isWishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Wishlist",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.course == null -> ErrorState(
                uiState.error ?: "This course could not be found.",
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )
            else -> {
                val course = uiState.course!!
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                        item {
                            course.category?.let {
                                Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text(course.title, style = MaterialTheme.typography.headlineSmall)
                            course.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RatingStars(course.rating, course.reviewCount)
                                course.instructorName?.let { Text("by $it", style = MaterialTheme.typography.bodyMedium) }
                            }
                            PriceTag(course.priceCents, course.currency)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            course.description?.let {
                                Text("About this course", style = MaterialTheme.typography.titleMedium)
                                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                            }
                            Text("Reviews (${uiState.reviews.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(uiState.reviews) { review ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(review.studentName, style = MaterialTheme.typography.titleMedium)
                                    RatingStars(review.rating.toDouble())
                                }
                                review.review?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::addToCart,
                            enabled = !uiState.isInCart,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (uiState.isInCart) "In cart" else "Add to cart")
                        }
                        Button(
                            onClick = { navController.navigate(Destinations.checkout(course.id)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Buy now")
                        }
                    }
                }
            }
        }
    }
}
