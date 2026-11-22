package zw.co.eduquinn.app.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import zw.co.eduquinn.app.ui.components.CourseCard
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.nav.Destinations

/**
 * Restructured to match public/index.html section-for-section: hero
 * (search + live stats) -> category pills -> education-stage strip ->
 * "Featured courses" grid -> "Popular courses" compact list -> promo
 * cards -> benefits strip. Course Detail/Checkout/My Learning/Account/
 * Login stay plain Material3 for now — this pass covers the Catalog/home
 * screen only.
 */
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
    val cartIds = cartItems.map { it.id }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Mirrors the website's persistent brand lockup (EQ
                    // badge + wordmark + tagline) shown at the top of
                    // every page - the plain "EduQuinn" text title this
                    // replaced had neither the badge, the burgundy
                    // wordmark color, nor the tagline.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "EQ",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Column {
                            Text(
                                "EduQuinn",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Learn. Grow. Succeed.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Destinations.WISHLIST) }) {
                        BadgedBox(badge = { if (uiState.wishlistIds.isNotEmpty()) Badge { Text("${uiState.wishlistIds.size}") } }) {
                            Icon(Icons.Filled.FavoriteBorder, contentDescription = "Wishlist")
                        }
                    }
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
            // Bento redesign: the home screen used to be one full-width
            // LazyColumn of stacked sections. The approved mockup lays
            // these out as a 2-column grid of tiles instead - hero, course
            // cards, and the two promo cards all become grid cells, with
            // every other section still spanning the full width via
            // GridItemSpan(2) so it reads the same as a normal section.
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    CatalogHero(
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        stats = uiState.stats,
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    SectionHeading(eyebrow = "EXPLORE", title = "Top categories")
                }
                item(span = { GridItemSpan(2) }) {
                    CategoryPillsRow(
                        selected = uiState.selectedCategory,
                        onSelect = viewModel::onCategorySelect,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    StageStripRow(
                        selected = uiState.selectedStage,
                        onSelect = viewModel::onStageSelect,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    SectionHeading(eyebrow = "HANDPICKED", title = "Featured courses")
                }
                items(uiState.visibleCourses, key = { it.id }, span = { GridItemSpan(1) }) { course ->
                    CourseCard(
                        course = course,
                        isWishlisted = course.id in uiState.wishlistIds,
                        isInCart = course.id in cartIds,
                        onClick = { navController.navigate(Destinations.courseDetail(course.id)) },
                        onToggleWishlist = {
                            if (!viewModel.toggleWishlist(course.id)) {
                                navController.navigate(Destinations.login(Destinations.CATALOG))
                            }
                        },
                        onAddToCart = { CartStore.add(course) },
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeading(eyebrow = "TRENDING", title = "Popular courses")
                        Spacer(Modifier.height(8.dp))
                        PopularCoursesList(
                            courses = uiState.popularCourses,
                            onCourseClick = { navController.navigate(Destinations.courseDetail(it.id)) },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                item(span = { GridItemSpan(1) }) {
                    InstructorPromoCard(
                        onClick = { navController.navigate(Destinations.register(role = "instructor")) },
                    )
                }
                item(span = { GridItemSpan(1) }) {
                    LiveClassesPromoCard(
                        onClick = { navController.navigate(Destinations.LIVE_CLASSES) },
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    BenefitsStrip()
                }

                item(span = { GridItemSpan(2) }) {
                    BusinessPromoSection(
                        onExploreClick = { navController.navigate(Destinations.BUSINESS) },
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(eyebrow: String, title: String) {
    Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
