package com.elysium369.meet.safety.science.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.science.data.SafetyScienceDao
import com.elysium369.meet.safety.science.data.SciClaimEntity
import com.elysium369.meet.safety.science.data.SciEntityEntity
import com.elysium369.meet.safety.science.data.SciEventEntity
import com.elysium369.meet.safety.science.data.SciHypothesisEntity
import com.elysium369.meet.safety.science.data.SciPublicationEntity
import com.elysium369.meet.safety.science.data.SciReplicationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ResearchViewModel @Inject constructor(
    private val dao: SafetyScienceDao,
) : ViewModel() {

    val entities: StateFlow<List<SciEntityEntity>> =
        dao.observeAllEntities().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val claims: StateFlow<List<SciClaimEntity>> =
        dao.observeRecentClaims(100).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val events: StateFlow<List<SciEventEntity>> =
        dao.observeRecentEvents(100).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val hypotheses: StateFlow<List<SciHypothesisEntity>> =
        dao.observeHypotheses().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val replications: StateFlow<List<SciReplicationEntity>> =
        dao.observeReplications().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val publications: StateFlow<List<SciPublicationEntity>> =
        dao.observePublications().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
}
