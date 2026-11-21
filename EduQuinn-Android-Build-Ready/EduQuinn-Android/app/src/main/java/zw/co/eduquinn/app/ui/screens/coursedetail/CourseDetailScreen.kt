package zw.co.eduquinn.app.ui.screens.coursedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.Review
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.components.PriceTag
import zw.co.eduquinn.app.ui.components.RatingStars
import zw.co.eduquinn.app.ui.components.courseIcon
import zw.co.eduquinn.app.ui.nav.Destinations
import zw.co.eduquinn.app.ui.theme.Cream
import zw.co.eduquinn.app.ui.theme.Ink

/**
 * Mirrors course-details.html's section order: a dark hero (eyebrow/
 * title/subhead/rating/instructor), a purchase card, "About this course",
 * "Student reviews", and a static "Access" checklist — the same 4-item
 * copy the website uses. The site also shows a lesson count under
 * "Course content", left out here: CourseDetailResponse/Course has no
 * lesson-count field, so there's nothing real to put there.
 */
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
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        item {
                            // Hero — dark band, mirrors .details-hero.
                            Column(
                                modifier = Modifier.fillMaxWidth().background(Ink).padding(20.dp),
                            ) {
                                Text(
                                    (course.educationLevel?.uppercase() ?: "COURSE"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    course.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Cream,
                                )
                                (course.subtitle ?: course.description)?.let {
                                    Spacer(Modifier.size(6.dp))
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Cream.copy(alpha = 0.85f))
                                }
                                Spacer(Modifier.size(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RatingStars(course.rating, course.reviewCount)
                                    if (course.students > 0) {
                                        Text(
                                            " · ${course.students} students",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Cream.copy(alpha = 0.8f),
                                        )
                                    }
                                }
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    "Created by ${course.instructorName ?: "EduQuinn Instructor"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Cream.copy(alpha = 0.85f),
                                )
                            }
                        }

                        item {
                            // Purchase card — icon badge + price + add to cart.
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                courseIcon(course.category),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp),
                                            )
                                        }
                                    }
                                    PriceTag(course.priceCents, course.currency)
                                    Spacer(Modifier.size(12.dp))
                                    OutlinedButton(
                                        onClick = viewModel::addToCart,
                                        enabled = !uiState.isInCart,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (uiState.isInCart) "In cart" else "Add to cart")
                                    }
                                }
                            }
                        }

                        item {
                            DetailPanel(title = "About this course") {
                                Text(
                                    course.description?.takeIf { it.isNotBlank() }
                                        ?: "Course information will be added by the instructor.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        item {
                            DetailPanel(title = "Student reviews (${uiState.reviews.size})") {
                                if (uiState.reviews.isEmpty()) {
                                    Text(
                                        "No student reviews yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    uiState.reviews.forEachIndexed { index, review ->
                                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                        ReviewRow(review)
                                    }
                                }
                            }
                        }

                        item {
                            DetailPanel(title = "Access") {
                                AccessItem("Learn on mobile and desktop")
                                AccessItem("Progress saved to your account")
                                AccessItem("Course resources where provided")
                                AccessItem("Certificate when completion requirements are met")
                            }
                        }

                        item { Spacer(Modifier.size(8.dp)) }
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

@Composable
private fun DetailPanel(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}

@Composable
private fun ReviewRow(review: Review) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(review.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            RatingStars(review.rating.toDouble())
        }
        review.review?.let {
            Spacer(Modifier.size(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
