package com.elysium369.meet.fulfillment.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.remote.SupabaseModule
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PersonalFinancialActivityEntry(
    @SerialName("entry_id") val entryId: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("activity_type") val activityType: String,
    val title: String,
    @SerialName("reference_id") val referenceId: String,
    val flow: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val status: String,
    val source: String,
    @SerialName("is_confirmed") val isConfirmed: Boolean,
)

data class UnifiedFinancialActivityState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val entries: List<PersonalFinancialActivityEntry> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class UnifiedFinancialActivityViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(UnifiedFinancialActivityState())
    val state: StateFlow<UnifiedFinancialActivityState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        if (SupabaseModule.client.auth.currentUserOrNull() == null) {
            mutableState.value = UnifiedFinancialActivityState(loading = false, signedIn = false)
            return@launch
        }
        mutableState.value = mutableState.value.copy(loading = true, signedIn = true, error = null)
        runCatching {
            SupabaseModule.client.postgrest.rpc(
                "personal_financial_activity_v1",
                buildJsonObject { put("p_limit", 500) },
            ).decodeList<PersonalFinancialActivityEntry>()
        }.onSuccess { rows ->
            mutableState.value = UnifiedFinancialActivityState(loading = false, signedIn = true, entries = rows)
        }.onFailure { failure ->
            mutableState.value = UnifiedFinancialActivityState(
                loading = false,
                signedIn = true,
                entries = mutableState.value.entries,
                error = failure.message ?: "No se pudo actualizar la actividad financiera.",
            )
        }
    }
}
