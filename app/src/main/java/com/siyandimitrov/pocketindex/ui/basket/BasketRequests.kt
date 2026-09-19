package com.siyandimitrov.pocketindex.ui.basket

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the basket tab should show when another screen opens it. */
sealed interface BasketTarget {
    data class Product(val productId: Long) : BasketTarget
    data class Category(val categoryId: Long) : BasketTarget
}

/**
 * Hands a [BasketTarget] from one tab to the basket. The basket's view model lives with its
 * navigation entry, so a request made from the overview cannot reach it directly; it is parked
 * here and consumed once the basket is showing.
 */
@Singleton
class BasketRequests @Inject constructor() {
    private val pending = MutableStateFlow<BasketTarget?>(null)
    val target: StateFlow<BasketTarget?> = pending.asStateFlow()

    fun open(target: BasketTarget) {
        pending.value = target
    }

    fun clear() {
        pending.value = null
    }
}
