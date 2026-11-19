package zw.co.eduquinn.app.data.cart

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zw.co.eduquinn.app.data.model.Course

/**
 * In-memory only for v1 — mirrors the web app's own cart, which is also
 * purely client-side (never persisted server-side until checkout creates
 * an order). Doesn't survive process death; easy to swap for a
 * DataStore-backed version later if that turns out to matter.
 */
object CartStore {
    private val _items = MutableStateFlow<List<Course>>(emptyList())
    val items: StateFlow<List<Course>> = _items.asStateFlow()

    fun add(course: Course) {
        if (_items.value.none { it.id == course.id }) {
            _items.value = _items.value + course
        }
    }

    fun remove(courseId: String) {
        _items.value = _items.value.filterNot { it.id == courseId }
    }

    fun contains(courseId: String): Boolean = _items.value.any { it.id == courseId }

    fun clear() {
        _items.value = emptyList()
    }

    val subtotalCents: Int get() = _items.value.sumOf { it.priceCents }
}
