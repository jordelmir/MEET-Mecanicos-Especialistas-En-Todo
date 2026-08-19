package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.local.MechanicalKnowledgeBundle
import com.elysium369.meet.data.local.MechanicalKnowledgeRepository
import com.elysium369.meet.data.supabase.RepairCase
import com.elysium369.meet.data.supabase.RepairComment
import com.elysium369.meet.data.supabase.RepairCaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class RepairNetworkViewModel(
    private val repository: RepairCaseRepository,
    private val mechanicalKnowledgeRepository: MechanicalKnowledgeRepository,
    private val customScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        repository: RepairCaseRepository,
        mechanicalKnowledgeRepository: MechanicalKnowledgeRepository
    ) : this(repository, mechanicalKnowledgeRepository, null)

    private val scope: CoroutineScope
        get() = customScope ?: viewModelScope

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _makeFilter = MutableStateFlow("")
    val makeFilter = _makeFilter.asStateFlow()

    private val _modelFilter = MutableStateFlow("")
    val modelFilter = _modelFilter.asStateFlow()

    private val _yearFilter = MutableStateFlow<Int?>(null)
    val yearFilter = _yearFilter.asStateFlow()

    private val _countryFilter = MutableStateFlow("")
    val countryFilter = _countryFilter.asStateFlow()

    private val _dtcFilter = MutableStateFlow("")
    val dtcFilter = _dtcFilter.asStateFlow()

    private val _sortByFilter = MutableStateFlow("votes")
    val sortByFilter = _sortByFilter.asStateFlow()

    private val _onlyVerifiedFilter = MutableStateFlow(false)
    val onlyVerifiedFilter = _onlyVerifiedFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _casesList = MutableStateFlow<List<RepairCase>>(emptyList())
    val casesList = _casesList.asStateFlow()

    private val _knowledgeBundle = MutableStateFlow(emptyKnowledgeBundle())
    val knowledgeBundle = _knowledgeBundle.asStateFlow()

    val savedCases: StateFlow<List<RepairCase>> = repository.getSavedCases()
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val myContributions: StateFlow<List<RepairCase>> = repository.getMyContributions()
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val _activeCaseDetails = MutableStateFlow<RepairCase?>(null)
    val activeCaseDetails = _activeCaseDetails.asStateFlow()

    private val _activeCaseComments = MutableStateFlow<List<RepairComment>>(emptyList())
    val activeCaseComments = _activeCaseComments.asStateFlow()

    private val _isBookmarkedState = MutableStateFlow(false)
    val isBookmarkedState = _isBookmarkedState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Automatically search when filters or search query change, debouncing text input to prevent race conditions
        @OptIn(FlowPreview::class)
        combine(
            _searchQuery.debounce(300L), _makeFilter, _modelFilter, _yearFilter,
            _countryFilter, _dtcFilter, _sortByFilter, _onlyVerifiedFilter
        ) { array: Array<Any?> ->
            array
        }.onEach { array ->
            val search = array[0] as String
            val make = array[1] as String
            val model = array[2] as String
            val year = array[3] as Int?
            val country = array[4] as String
            val dtc = array[5] as String
            val sort = array[6] as String
            val verified = array[7] as Boolean
            triggerSearch(search, make, model, year, country, dtc, sort, verified)
        }.launchIn(scope)
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setMakeFilter(make: String) { _makeFilter.value = make }
    fun setModelFilter(model: String) { _modelFilter.value = model }
    fun setYearFilter(year: Int?) { _yearFilter.value = year }
    fun setCountryFilter(country: String) { _countryFilter.value = country }
    fun setDtcFilter(dtc: String) { _dtcFilter.value = dtc }
    fun setSortByFilter(sort: String) { _sortByFilter.value = sort }
    fun setOnlyVerifiedFilter(verified: Boolean) { _onlyVerifiedFilter.value = verified }
    fun clearFilters() {
        _searchQuery.value = ""
        _makeFilter.value = ""
        _modelFilter.value = ""
        _yearFilter.value = null
        _countryFilter.value = ""
        _dtcFilter.value = ""
        _sortByFilter.value = "votes"
        _onlyVerifiedFilter.value = false
    }

    fun refreshSearch() {
        triggerSearch(
            _searchQuery.value, _makeFilter.value, _modelFilter.value,
            _yearFilter.value, _countryFilter.value, _dtcFilter.value,
            _sortByFilter.value, _onlyVerifiedFilter.value
        )
    }

    private fun triggerSearch(
        query: String, make: String, model: String, year: Int?,
        country: String, dtc: String, sort: String, verified: Boolean
    ) {
        searchJob?.cancel() // Cancel previous search execution to prevent race conditions
        searchJob = scope.launch {
            _isLoading.value = true
            val results = repository.searchCases(query, make, model, year, country, dtc, sort, verified)
            _casesList.value = results
            val knowledgeQuery = listOf(query, dtc, make, model)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            _knowledgeBundle.value = if (knowledgeQuery.isBlank()) {
                emptyKnowledgeBundle()
            } else {
                mechanicalKnowledgeRepository.search(knowledgeQuery)
            }
            _isLoading.value = false
        }
    }

    fun selectCase(caseId: String) {
        scope.launch {
            _isLoading.value = true
            val details = repository.getCaseById(caseId)
            _activeCaseDetails.value = details
            if (details != null) {
                _isBookmarkedState.value = repository.isBookmarked(details.id)
                val comments = repository.getCommentsForCase(details.id)
                _activeCaseComments.value = comments
            }
            _isLoading.value = false
        }
    }

    fun toggleBookmark(repairCase: RepairCase) {
        scope.launch {
            repository.toggleBookmark(repairCase)
            _isBookmarkedState.value = repository.isBookmarked(repairCase.id)
        }
    }

    fun upvoteCase(caseId: String) {
        scope.launch {
            if (repository.upvoteCase(caseId)) {
                // Refresh active case details
                _activeCaseDetails.value?.let { current ->
                    _activeCaseDetails.value = current.copy(votes = current.votes + 1)
                }
                refreshSearch()
            }
        }
    }

    fun downvoteCase(caseId: String) {
        scope.launch {
            if (repository.downvoteCase(caseId)) {
                // Refresh active case details
                _activeCaseDetails.value?.let { current ->
                    _activeCaseDetails.value = current.copy(votes = (current.votes - 1).coerceAtLeast(0))
                }
                refreshSearch()
            }
        }
    }

    fun submitComment(caseId: String, authorName: String, reputation: String, body: String) {
        scope.launch {
            if (body.isBlank()) return@launch
            val comment = RepairComment(
                id = java.util.UUID.randomUUID().toString(),
                case_id = caseId,
                user_id = com.elysium369.meet.data.supabase.SupabaseManager.client.auth.currentUserOrNull()?.id ?: "anonymous_user",
                author_name = authorName.ifBlank { "Mecánico Elysium Vanguard" },
                author_reputation = reputation,
                comment_body = body,
                created_at = System.currentTimeMillis().toString()
            )
            if (repository.addComment(comment)) {
                val comments = repository.getCommentsForCase(caseId)
                _activeCaseComments.value = comments
            }
        }
    }

    fun submitCase(
        make: String, model: String, year: Int, engine: String, country: String,
        dtc: String, symptoms: String, solution: String, cost: Double,
        timeSpent: Int, partsUsed: String, onSuccess: () -> Unit
    ) {
        scope.launch {
            _isLoading.value = true
            val newCase = RepairCase(
                id = java.util.UUID.randomUUID().toString(),
                vehicle_make = make,
                vehicle_model = model,
                year = year,
                engine = engine,
                country = country,
                dtc_code = dtc,
                symptoms = symptoms,
                solution = solution,
                cost = cost,
                time_spent = timeSpent,
                parts_used = partsUsed,
                verified = false,
                votes = 0,
                success_rate = 0.0, // Unverified initial state
                created_at = System.currentTimeMillis().toString()
            )
            val success = repository.insertRepairCase(newCase)
            _isLoading.value = false
            if (success) {
                refreshSearch()
                onSuccess()
            }
        }
    }

    // Helper: calculate reputational status dynamically based on votes or cases (honest community badges)
    fun getReputationTitle(authorId: String, totalCasesByAuthor: Int = 1): String {
        return when {
            totalCasesByAuthor >= 11 -> "Contribuidor Master 🏆"
            totalCasesByAuthor >= 6 -> "Contribuidor Destacado 🛠️"
            totalCasesByAuthor >= 3 -> "Contribuidor Frecuente 🤝"
            totalCasesByAuthor >= 1 -> "Contribuidor Comunitario"
            else -> "Usuario"
        }
    }

    private fun emptyKnowledgeBundle() = MechanicalKnowledgeBundle(
        safetyProtocols = emptyList(),
        symptomGuides = emptyList(),
        procedures = emptyList(),
        rebuildGuides = emptyList(),
        trenchKnowledge = emptyList(),
        chemicals = emptyList(),
        tools = emptyList(),
        matrixLinks = emptyList()
    )
}
