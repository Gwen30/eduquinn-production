package zw.co.eduquinn.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String,
    val title: String,
    val slug: String,
    val subtitle: String? = null,
    val description: String? = null,
    val educationLevel: String? = null,
    val category: String? = null,
    val priceCents: Int,
    val currency: String,
    val qualityScore: Double? = null,
    val instructorName: String? = null,
    val students: Int = 0,
    val rating: Double? = null,
    val reviewCount: Int = 0,
)

@Serializable
data class CoursesResponse(val courses: List<Course>)

@Serializable
data class PublicStats(val students: Int, val instructors: Int, val courses: Int)

@Serializable
data class Review(
    val rating: Int,
    val review: String? = null,
    val createdAt: String,
    val studentName: String,
)

@Serializable
data class CourseDetailResponse(val course: Course, val reviews: List<Review> = emptyList())

@Serializable
data class WishlistItem(val courseId: String)

@Serializable
data class WishlistResponse(val courses: List<Course> = emptyList())
