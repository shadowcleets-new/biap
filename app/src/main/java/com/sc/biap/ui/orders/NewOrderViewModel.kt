package com.sc.biap.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.DataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.OrderLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @file NewOrderViewModel.kt
 * @description The draft order being assembled, before anything leaves stock.
 *
 * [TABLE OF CONTENTS]
 * 1. TYPES
 * 2. THE VIEW MODEL
 * 3. THE PICK LIST
 * 4. PLACING
 *
 * NOTHING HERE TOUCHES STOCK:
 *   The draft is entirely in memory until [place] is called. Backing out of the screen, or
 *   the process being killed, costs a draft and nothing else - no half-decremented sizes to
 *   reconcile afterwards.
 *
 * PRICES ARE NOT CARRIED IN THE DRAFT:
 *   The lines go to the repository with a zero price and the repository reads the real one
 *   out of the database as it writes. The screen may have been open for a while, and the
 *   price an order records has to be the price at the moment it was placed, not the one that
 *   happened to be on screen when it was opened.
 */

// #region 1. Types

/** One size of one design. The key the draft is held under. */
data class PieceKey(val designId: String, val size: String)

/** What the bottom bar reports, recomputed as the picks change. */
data class OrderDraft(
    val pieces: Int = 0,
    /** Paise. Unpriced sizes contribute nothing rather than a guess. */
    val total: Long = 0L,
    val designCount: Int = 0,
    val hasUnpriced: Boolean = false,
) {
    val isEmpty: Boolean get() = pieces == 0
}

// #endregion

// #region 2. The view model

class NewOrderViewModel(
    private val dataRepository: DataRepository,
    /** Null for a new order; an id opens that order for editing. */
    private val orderId: String? = null,
) : ViewModel() {

    val isEditing: Boolean = orderId != null

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _picks = MutableStateFlow<Map<PieceKey, Int>>(emptyMap())
    val picks: StateFlow<Map<PieceKey, Int>> = _picks.asStateFlow()

    /**
     * What the order held when this screen opened, per size.
     *
     * Not just the starting values - it is also the ceiling. A size can be raised to
     * `stock + whatever this order already holds`, because those pieces are not gone, only
     * spoken for by this order. Capping at plain stock would refuse to save an order the
     * moment it had emptied a size, including saving it back exactly as it was.
     */
    private val _held = MutableStateFlow<Map<PieceKey, Int>>(emptyMap())
    val held: StateFlow<Map<PieceKey, Int>> = _held.asStateFlow()

    /** True until an order being edited has actually been read back. */
    private val _loading = MutableStateFlow(orderId != null)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /**
     * The price each size was ALREADY sold at on this order.
     *
     * The running total has to show what the order is worth, and a line that went out in June
     * is worth its June price whatever the catalogue says today. Only a size that was never
     * on this order falls through to the current price - there is no historic one to use.
     */
    private val _recordedPrices = MutableStateFlow<Map<PieceKey, Long>>(emptyMap())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Declared after every StateFlow it touches: an init block runs in declaration order, and
    // reaching a property above its own initialiser is a null dereference waiting to happen.
    init {
        if (orderId != null) {
            viewModelScope.launch {
                val order = dataRepository.orderById(orderId)
                if (order != null) {
                    val byKey = order.lines.groupBy { PieceKey(it.designId, it.size) }
                    _held.value = byKey.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
                    _picks.value = _held.value
                    _recordedPrices.value = byKey.mapValues { (_, lines) -> lines.first().unitPrice }
                } else {
                    _error.value = "That order no longer exists."
                }
                _loading.value = false
            }
        }
    }

    /**
     * What can actually be ordered: active designs holding at least one piece.
     *
     * Archived designs are out because archiving is how a style is retired, and sold-out
     * designs are out because there is nothing to take. Showing either would be offering
     * something the next screen has to refuse.
     */
    val designs: StateFlow<List<Design>> = combine(
        dataRepository.biData,
        _query,
        _held,
    ) { data, query, held ->
        val onThisOrder = held.keys.map { it.designId }.toSet()
        data.designs
            .filterNot { it.isArchived }
            // Sold out normally means "nothing to take", but a design already on this order
            // has to stay listed however empty the catalogue now reads - its pieces are held
            // by this order, and hiding it would make its own quantities uneditable.
            .filter { design ->
                design.id in onThisOrder || design.sizeInventories.any { it.stock > 0 }
            }
            .filter { design ->
                query.isBlank() ||
                    design.name.contains(query, ignoreCase = true) ||
                    design.category.contains(query, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** How many of a size may be picked: what is on the shelf plus what this order holds. */
    fun ceilingFor(designId: String, size: String, stock: Int): Int =
        stock + (_held.value[PieceKey(designId, size)] ?: 0)

    val draft: StateFlow<OrderDraft> = combine(
        dataRepository.biData,
        _picks,
        _recordedPrices,
    ) { data, picks, recorded ->
        val chosen = picks.filterValues { it > 0 }
        if (chosen.isEmpty()) return@combine OrderDraft()

        // The order's own price first, today's catalogue price only for a size it never had.
        val priceOf = { key: PieceKey ->
            recorded[key] ?: data.designs.firstOrNull { it.id == key.designId }
                ?.sizeInventories?.firstOrNull { it.size == key.size }
                ?.price ?: 0L
        }
        OrderDraft(
            pieces = chosen.values.sum(),
            total = chosen.entries.sumOf { (key, qty) -> priceOf(key) * qty },
            designCount = chosen.keys.map { it.designId }.distinct().size,
            hasUnpriced = chosen.keys.any { priceOf(it) <= 0L },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrderDraft())

    fun updateQuery(value: String) {
        _query.value = value
    }

    // #endregion

    // #region 3. The pick list

    /**
     * Set how many of one size are on the draft. [available] caps it; zero removes the entry.
     *
     * Clamped here rather than in the UI so the cap holds however the value arrives - the
     * stepper is not the only thing that could ever call this, and a draft that can exceed
     * the stock is a draft the repository will reject at the last possible moment.
     */
    fun setQuantity(designId: String, size: String, quantity: Int, available: Int) {
        val key = PieceKey(designId, size)
        val clamped = quantity.coerceIn(0, available)
        _picks.update { current ->
            if (clamped == 0) current - key else current + (key to clamped)
        }
    }

    fun clear() {
        _picks.value = emptyMap()
    }

    // #endregion

    // #region 4. Placing

    fun dismissError() {
        _error.value = null
    }

    /**
     * Write the order and take its pieces out of stock. [onPlaced] runs only on success.
     *
     * Prices go over as zero on purpose - see the file header. The repository fills them in
     * from the database inside the same transaction that moves the stock.
     */
    fun place(onPlaced: () -> Unit) {
        if (_saving.value) return
        val lines = _picks.value
            .filterValues { it > 0 }
            .map { (key, qty) -> OrderLine(key.designId, key.size, qty, unitPrice = 0L) }
        // An empty NEW order is nothing at all, so there is nothing to do. An empty EDIT is a
        // cancellation and goes through [cancelEditedOrder], which the screen routes to after
        // confirming - never from here, so this can never destroy a record by accident.
        if (lines.isEmpty()) return

        viewModelScope.launch {
            _saving.value = true
            val failure = if (orderId != null) {
                dataRepository.editOrder(orderId, lines)
            } else {
                dataRepository.placeOrder(lines)
            }
            _saving.value = false
            if (failure == null) onPlaced() else _error.value = failure
        }
    }

    /**
     * Emptying an order and saving cancels it: the pieces go back and the record is removed.
     *
     * The confirmation lives on the screen rather than here. It is the same action the Cancel
     * button on the order card performs, and it is the only irreversible thing this screen can
     * do - reaching it by taking the last piece off should not be quieter than reaching it
     * deliberately.
     */
    fun cancelEditedOrder(onCancelled: () -> Unit) {
        val id = orderId ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            val failure = dataRepository.cancelOrder(id)
            _saving.value = false
            if (failure == null) onCancelled() else _error.value = failure
        }
    }

    /** Pieces this order held when the screen opened, for the confirmation to name. */
    val heldPieces: Int get() = _held.value.values.sum()
}

// #endregion
