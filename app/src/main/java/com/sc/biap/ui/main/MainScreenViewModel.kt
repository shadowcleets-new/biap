package com.sc.biap.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.DataRepository
import com.sc.biap.data.openJobs
import com.sc.biap.data.BusinessIntelligenceData
import com.sc.biap.data.CatalogueSummary
import com.sc.biap.data.stats
import com.sc.biap.data.summarise
import com.sc.biap.ui.main.MainScreenUiState.Success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _filters = MutableStateFlow(CatalogueFilters())
  val filters: StateFlow<CatalogueFilters> = _filters.asStateFlow()

  val uiState: StateFlow<MainScreenUiState> = combine(
    dataRepository.biData,
    _searchQuery,
    _filters
  ) { data, query, filters ->
    val searched: MainScreenUiState = if (query.isBlank()) {
      Success(data)
    } else {
      val filteredDesigns = data.designs.filter { design ->
        // 1. Search by design name or category (case-insensitive)
        val matchesBasic = design.name.contains(query, ignoreCase = true) ||
            design.category.contains(query, ignoreCase = true)

        // 2. Search by custom attributes key/value (case-insensitive)
        val matchesAttributes = design.customFields.any { (key, value) ->
          key.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
        }

        // 3. Search by size inventory (e.g. searching "L" or "XL" matches designs that have stock > 0 in size L or XL)
        val matchesSize = design.sizeInventories.any { sizeInv ->
          sizeInv.stock > 0 && sizeInv.size.equals(query, ignoreCase = true)
        }

        // 4. Search by assigned karigar names (case-insensitive)
        val matchesKarigars = design.karigars.any { karigar ->
          karigar.contains(query, ignoreCase = true)
        }

        matchesBasic || matchesAttributes || matchesSize || matchesKarigars
      }
      Success(data.copy(designs = filteredDesigns))
    }

    // The summary counts the WHOLE active catalogue, never the filtered view - otherwise
    // filtering to "needs attention" would proudly report that nothing needs attention.
    val active = data.designs.filterNot { it.isArchived }
    val summary = active.summarise()

    // Stats computed ONCE per design, then looked up.
    //
    // These were called from inside the comparator, which meant stats() ran on both sides of
    // every comparison - roughly 220 calls to sort 24 designs, each one walking the size list
    // six times and allocating a DesignStats. Sorting is O(n log n) comparisons; computing a
    // value inside the comparator makes the work O(n log n) too, for something that only
    // varies per design.
    val statsById = data.designs.associate { it.id to it.stats() }
    // Same reasoning for the alphabetical tiebreak: lowercase() allocated a fresh string on
    // every comparison it was consulted for.
    val sortKeyById = data.designs.associate { it.id to it.name.lowercase() }

    val visible = (searched as? Success)?.data?.designs.orEmpty()
      .filter { design ->
        val archiveOk = if (filters.showArchived) design.isArchived else !design.isArchived
        val attentionOk = !filters.attentionOnly ||
          statsById[design.id]?.needsAttention == true
        val categoryOk = filters.category == null || design.category == filters.category
        archiveOk && attentionOk && categoryOk
      }
      // Attention first: sizes fully out outrank sizes merely low. WITHIN a group the most
      // recently touched design comes first, so something just added or edited appears at
      // the top of where you are looking rather than alphabetically buried halfway down.
      // Alphabetical was the original tiebreak and it made new designs effectively invisible.
      .sortedWith(
        compareByDescending<com.sc.biap.data.Design> { statsById[it.id]?.outSizes ?: 0 }
          .thenByDescending { statsById[it.id]?.lowSizes ?: 0 }
          .thenByDescending { it.updatedAt }
          .thenBy { sortKeyById[it.id].orEmpty() }
      )

    val out: MainScreenUiState = Success(
      data = data.copy(designs = visible),
      summary = summary,
      categories = active.map { it.category }.filter { it.isNotBlank() }.distinct().sorted(),
      filters = filters,
      archivedCount = data.designs.count { it.isArchived },
      // The size of the whole active catalogue, NOT of the filtered view. The header reads
      // this, and a count that shrinks when you tap a filter looks exactly like designs
      // going missing.
      totalCount = active.size,
    )
    out
  }
    .catch { emit(MainScreenUiState.Error(it)) }
    // OFF THE UI THREAD.
    //
    // viewModelScope runs on Dispatchers.Main.immediate, so all of the above - searching,
    // filtering, computing stats for every design and sorting them - was happening on the
    // very thread that draws frames. Measured on device it was the dominant cause of jank
    // while scrolling: 35 of 36 dropped frames were "slow UI thread".
    //
    // flowOn affects everything UPSTREAM of it, which is exactly this pipeline; stateIn
    // still publishes on the main thread, so collectors are unaffected. The cost is that a
    // change now lands a frame later than it used to, which is the correct trade: a frame of
    // latency on a data update against a dropped frame in the middle of a scroll.
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun toggleAttentionFilter() {
    _filters.update { it.copy(attentionOnly = !it.attentionOnly) }
  }

  fun toggleArchivedFilter() {
    // Archived and "needs attention" together would be a view of retired stock nobody is
    // going to restock, so entering the archive clears the attention filter.
    _filters.update {
      if (it.showArchived) it.copy(showArchived = false)
      else CatalogueFilters(showArchived = true)
    }
  }

  fun selectCategory(category: String?) {
    _filters.update { it.copy(category = if (it.category == category) null else category) }
  }

  fun clearFilters() {
    _filters.value = CatalogueFilters()
  }

  /**
   * The Orders tab's list: newest first.
   *
   * Read off the same [DataRepository.biData] the catalogue uses rather than a second query,
   * so an order placed on this device shows up the instant its transaction commits - the same
   * commit that took its pieces out of the stock shown one tab over.
   */
  /**
   * Pieces currently out with karigars.
   *
   * On the header because "where are my goods" is a question asked far more often than it
   * is worth opening a screen for, and because the tracking is otherwise invisible until
   * the day something is sent out - which is exactly when nobody thinks to look for it.
   */
  val piecesWithKarigars: StateFlow<Int> = dataRepository.biData
    .map { data -> openJobs(data.jobWork).sumOf { it.outstanding } }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  val orders: StateFlow<List<com.sc.biap.data.Order>> = dataRepository.biData
    .map { data -> data.orders.sortedByDescending { it.createdAt } }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  /**
   * Every design, by id, for the photos and sizes an order card shows.
   *
   * Built from the WHOLE catalogue rather than [uiState]'s visible list, which is searched,
   * filtered and sorted. An order has to render itself whatever the Inventory tab happens to
   * be filtered to, and an archived design's orders are still real history.
   *
   * The whole design rather than just its name: the carousel needs the photo, and the size
   * label needs the design's own size ladder to decide whether the sizes ordered were an
   * unbroken run of it.
   */
  val designsById: StateFlow<Map<String, com.sc.biap.data.Design>> = dataRepository.biData
    .map { data -> data.designs.associateBy { it.id } }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  /** Cancel an order, returning its pieces to stock. */
  fun cancelOrder(orderId: String) {
    viewModelScope.launch { dataRepository.cancelOrder(orderId) }
  }

  /** Retire a design, or bring it back. Replaces deletion as the ordinary action. */
  fun setArchived(id: String, archived: Boolean) {
    dataRepository.setDesignArchived(id, archived)
  }

  fun deleteDesign(id: String) {
    dataRepository.deleteDesign(id)
  }

  val authManager get() = dataRepository.authManager
  val isSyncing: StateFlow<Boolean> = dataRepository.isSyncing
  val lastSyncAt: StateFlow<Long> = dataRepository.lastSyncAt
  val syncStatus: StateFlow<com.sc.biap.data.SyncStatus> = dataRepository.syncStatus

  fun syncWithGoogleDrive() {
    dataRepository.syncWithGoogleDrive()
  }

  /** Stores the passphrase that unlocks the sync key, then retries the sync. */
  fun setSyncPassphrase(passphrase: CharArray) {
    dataRepository.setSyncPassphrase(passphrase)
  }

  fun resetSyncStatus() {
    dataRepository.resetSyncStatus()
  }

  fun signOut(onComplete: () -> Unit) {
    dataRepository.signOut(onComplete)
  }

  suspend fun hasGuestData(): Boolean {
    return dataRepository.hasGuestData()
  }

  suspend fun mergeGuestData(): Boolean {
    return dataRepository.mergeGuestData()
  }

  suspend fun discardGuestData(): Boolean {
    return dataRepository.clearGuestData()
  }
}

/** What the chip row is currently narrowing the catalogue to. */
data class CatalogueFilters(
  val attentionOnly: Boolean = false,
  val category: String? = null,
  /** Archived designs are hidden from the working catalogue until asked for by name. */
  val showArchived: Boolean = false,
) {
  val isAny: Boolean get() = attentionOnly || category != null || showArchived
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(
    val data: BusinessIntelligenceData,
    val summary: CatalogueSummary = CatalogueSummary(0, 0, 0),
    val categories: List<String> = emptyList(),
    val filters: CatalogueFilters = CatalogueFilters(),
    val archivedCount: Int = 0,
    /** Every active design, regardless of the filters currently applied. */
    val totalCount: Int = 0,
  ) : MainScreenUiState
}
