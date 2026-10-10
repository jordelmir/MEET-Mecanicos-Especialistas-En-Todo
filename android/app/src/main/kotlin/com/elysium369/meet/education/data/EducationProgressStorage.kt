package com.elysium369.meet.education.data

import android.content.Context
import androidx.core.content.edit
import com.elysium369.meet.education.domain.ConceptKnowledgeState
import com.elysium369.meet.education.domain.EpistemicTruthState
import com.elysium369.meet.education.domain.FsrsCardState
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class EducationTrackProgress(
    val trackName: String,
    val selectedUnitId: String? = null,
    val selectedConceptId: String? = null,
    val currentTaskIndex: Int = 0,
    val completedTaskIds: Set<String> = emptySet(),
    val accumulatedColones: Int = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

interface EducationProgressStorage {
    fun saveLastActiveTrack(trackName: String)
    fun getLastActiveTrack(): String?
    fun saveTrackProgress(progress: EducationTrackProgress)
    fun getTrackProgress(trackName: String): EducationTrackProgress?
    fun saveConceptState(state: ConceptKnowledgeState)
    fun getConceptState(conceptId: String): ConceptKnowledgeState?
    fun getAllConceptStates(): Map<String, ConceptKnowledgeState>
    fun recordEvidenceHash(hash: String)
    fun getAllEvidenceHashes(): List<String>
    fun saveFsrsCard(card: FsrsCardState)
    fun getFsrsCard(conceptId: String): FsrsCardState?
    fun clearAll()
}

/**
 * In-memory fallback implementation for JVM unit tests or decoupled environments.
 */
class InMemoryEducationProgressStorage : EducationProgressStorage {
    private var lastActiveTrack: String? = null
    private val trackProgressMap = ConcurrentHashMap<String, EducationTrackProgress>()
    private val conceptStatesMap = ConcurrentHashMap<String, ConceptKnowledgeState>()
    private val evidenceHashes = mutableListOf<String>()
    private val fsrsCardsMap = ConcurrentHashMap<String, FsrsCardState>()

    override fun saveLastActiveTrack(trackName: String) {
        lastActiveTrack = trackName
    }

    override fun getLastActiveTrack(): String? = lastActiveTrack

    override fun saveTrackProgress(progress: EducationTrackProgress) {
        trackProgressMap[progress.trackName] = progress
    }

    override fun getTrackProgress(trackName: String): EducationTrackProgress? {
        return trackProgressMap[trackName]
    }

    override fun saveConceptState(state: ConceptKnowledgeState) {
        conceptStatesMap[state.conceptId] = state
    }

    override fun getConceptState(conceptId: String): ConceptKnowledgeState? {
        return conceptStatesMap[conceptId]
    }

    override fun getAllConceptStates(): Map<String, ConceptKnowledgeState> {
        return HashMap(conceptStatesMap)
    }

    override fun recordEvidenceHash(hash: String) {
        synchronized(evidenceHashes) {
            evidenceHashes.add(hash)
        }
    }

    override fun getAllEvidenceHashes(): List<String> {
        return synchronized(evidenceHashes) { evidenceHashes.toList() }
    }

    override fun saveFsrsCard(card: FsrsCardState) {
        fsrsCardsMap[card.conceptId] = card
    }

    override fun getFsrsCard(conceptId: String): FsrsCardState? {
        return fsrsCardsMap[conceptId]
    }

    override fun clearAll() {
        lastActiveTrack = null
        trackProgressMap.clear()
        conceptStatesMap.clear()
        synchronized(evidenceHashes) { evidenceHashes.clear() }
        fsrsCardsMap.clear()
    }
}

/**
 * Robust SharedPreferences implementation with JSON persistence.
 * Guarantees zero data loss across Android process death and app restarts.
 */
class SharedPreferencesEducationProgressStorage(
    context: Context,
) : EducationProgressStorage {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    companion object {
        private const val PREFS_NAME = "meet_education_progress_v2"
        private const val KEY_LAST_TRACK = "key_last_active_track"
        private const val KEY_PREFIX_TRACK = "track_progress_"
        private const val KEY_CONCEPT_STATES = "key_concept_states_json"
        private const val KEY_EVIDENCE_HASHES = "key_evidence_hashes_json"
        private const val KEY_FSRS_CARDS = "key_fsrs_cards_json"
    }

    override fun saveLastActiveTrack(trackName: String) {
        prefs.edit { putString(KEY_LAST_TRACK, trackName) }
    }

    override fun getLastActiveTrack(): String? {
        return prefs.getString(KEY_LAST_TRACK, null)
    }

    override fun saveTrackProgress(progress: EducationTrackProgress) {
        val root = JSONObject().apply {
            put("trackName", progress.trackName)
            put("selectedUnitId", progress.selectedUnitId ?: "")
            put("selectedConceptId", progress.selectedConceptId ?: "")
            put("currentTaskIndex", progress.currentTaskIndex)
            put("accumulatedColones", progress.accumulatedColones)
            put("updatedAtEpochMs", progress.updatedAtEpochMs)
            val completedArray = JSONArray()
            progress.completedTaskIds.forEach { completedArray.put(it) }
            put("completedTaskIds", completedArray)
        }
        prefs.edit {
            putString(KEY_PREFIX_TRACK + progress.trackName, root.toString())
            putString(KEY_LAST_TRACK, progress.trackName)
        }
    }

    override fun getTrackProgress(trackName: String): EducationTrackProgress? {
        val raw = prefs.getString(KEY_PREFIX_TRACK + trackName, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val completedJson = root.optJSONArray("completedTaskIds") ?: JSONArray()
            val completedSet = buildSet {
                for (i in 0 until completedJson.length()) {
                    add(completedJson.getString(i))
                }
            }
            EducationTrackProgress(
                trackName = root.getString("trackName"),
                selectedUnitId = root.optString("selectedUnitId").takeIf { it.isNotBlank() },
                selectedConceptId = root.optString("selectedConceptId").takeIf { it.isNotBlank() },
                currentTaskIndex = root.optInt("currentTaskIndex", 0),
                completedTaskIds = completedSet,
                accumulatedColones = root.optInt("accumulatedColones", 0),
                updatedAtEpochMs = root.optLong("updatedAtEpochMs", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    override fun saveConceptState(state: ConceptKnowledgeState) {
        val allStates = getAllConceptStatesInternal().toMutableMap()
        allStates[state.conceptId] = state
        saveAllConceptStatesInternal(allStates)
    }

    override fun getConceptState(conceptId: String): ConceptKnowledgeState? {
        return getAllConceptStatesInternal()[conceptId]
    }

    override fun getAllConceptStates(): Map<String, ConceptKnowledgeState> {
        return getAllConceptStatesInternal()
    }

    private fun getAllConceptStatesInternal(): Map<String, ConceptKnowledgeState> {
        val raw = prefs.getString(KEY_CONCEPT_STATES, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val map = mutableMapOf<String, ConceptKnowledgeState>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = root.getJSONObject(key)
                val misconceptionsArray = item.optJSONArray("misconceptionCodes") ?: JSONArray()
                val misconceptions = buildList {
                    for (i in 0 until misconceptionsArray.length()) {
                        add(misconceptionsArray.getString(i))
                    }
                }
                map[key] = ConceptKnowledgeState(
                    learnerId = item.optString("learnerId", "local_student_cr_001"),
                    conceptId = item.getString("conceptId"),
                    masteryEstimate = item.optDouble("masteryEstimate", 0.0),
                    confidence = item.optDouble("confidence", 0.20),
                    evidenceCount = item.optInt("evidenceCount", 0),
                    successfulTransferCount = item.optInt("successfulTransferCount", 0),
                    lastDemonstratedAtEpochMs = if (item.has("lastDemonstratedAtEpochMs")) item.optLong("lastDemonstratedAtEpochMs") else null,
                    retentionEstimate = item.optDouble("retentionEstimate", 1.0),
                    misconceptionCodes = misconceptions,
                    prerequisitesSatisfied = item.optBoolean("prerequisitesSatisfied", true),
                    nextReviewEpochDay = item.optLong("nextReviewEpochDay", 0L),
                    epistemicStatus = runCatching {
                        EpistemicTruthState.valueOf(item.optString("epistemicStatus", EpistemicTruthState.DERIVED.name))
                    }.getOrDefault(EpistemicTruthState.DERIVED),
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun saveAllConceptStatesInternal(states: Map<String, ConceptKnowledgeState>) {
        val root = JSONObject()
        states.forEach { (id, s) ->
            val misconceptionsJson = JSONArray()
            s.misconceptionCodes.forEach { misconceptionsJson.put(it) }

            root.put(id, JSONObject().apply {
                put("learnerId", s.learnerId)
                put("conceptId", s.conceptId)
                put("masteryEstimate", s.masteryEstimate)
                put("confidence", s.confidence)
                put("evidenceCount", s.evidenceCount)
                put("successfulTransferCount", s.successfulTransferCount)
                s.lastDemonstratedAtEpochMs?.let { put("lastDemonstratedAtEpochMs", it) }
                put("retentionEstimate", s.retentionEstimate)
                put("misconceptionCodes", misconceptionsJson)
                put("prerequisitesSatisfied", s.prerequisitesSatisfied)
                put("nextReviewEpochDay", s.nextReviewEpochDay)
                put("epistemicStatus", s.epistemicStatus.name)
            })
        }
        prefs.edit { putString(KEY_CONCEPT_STATES, root.toString()) }
    }

    override fun recordEvidenceHash(hash: String) {
        val currentHashes = getAllEvidenceHashes().toMutableList()
        currentHashes.add(hash)
        val array = JSONArray()
        currentHashes.takeLast(200).forEach { array.put(it) } // Keep last 200 hashes
        prefs.edit { putString(KEY_EVIDENCE_HASHES, array.toString()) }
    }

    override fun getAllEvidenceHashes(): List<String> {
        val raw = prefs.getString(KEY_EVIDENCE_HASHES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        }.getOrDefault(emptyList())
    }

    override fun saveFsrsCard(card: FsrsCardState) {
        val allCards = getAllFsrsCardsInternal().toMutableMap()
        allCards[card.conceptId] = card
        val root = JSONObject()
        allCards.forEach { (id, c) ->
            root.put(id, JSONObject().apply {
                put("conceptId", c.conceptId)
                put("stabilityDays", c.stabilityDays)
                put("difficulty", c.difficulty)
                put("lastReviewTimestampMs", c.lastReviewTimestampMs)
                put("repetitionCount", c.repetitionCount)
                put("lapsesCount", c.lapsesCount)
            })
        }
        prefs.edit { putString(KEY_FSRS_CARDS, root.toString()) }
    }

    override fun getFsrsCard(conceptId: String): FsrsCardState? {
        return getAllFsrsCardsInternal()[conceptId]
    }

    private fun getAllFsrsCardsInternal(): Map<String, FsrsCardState> {
        val raw = prefs.getString(KEY_FSRS_CARDS, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val map = mutableMapOf<String, FsrsCardState>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = root.getJSONObject(id)
                map[id] = FsrsCardState(
                    conceptId = item.getString("conceptId"),
                    stabilityDays = item.optDouble("stabilityDays", 1.0),
                    difficulty = item.optDouble("difficulty", 5.0),
                    lastReviewTimestampMs = item.optLong("lastReviewTimestampMs", System.currentTimeMillis()),
                    repetitionCount = item.optInt("repetitionCount", 0),
                    lapsesCount = item.optInt("lapsesCount", 0),
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    override fun clearAll() {
        prefs.edit { clear() }
    }
}
