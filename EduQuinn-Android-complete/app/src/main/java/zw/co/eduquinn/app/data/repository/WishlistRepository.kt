package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class WishlistRepository(private val api: ApiService) {

    suspend fun wishlist(): Result<List<Course>> = safeApiCall { api.wishlist().courses }

    suspend fun add(courseId: String): Result<Unit> = safeApiCall { api.addWishlist(courseId) }

    suspend fun remove(courseId: String): Result<Unit> = safeApiCall { api.removeWishlist(courseId) }
}
