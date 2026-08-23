package com.elysium369.meet.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.elysium369.meet.core.education.theory.ReviewGrade
import com.elysium369.meet.core.education.theory.TheoryLearningEngine
import com.elysium369.meet.core.education.theory.TheoryLicenseTrack
import com.elysium369.meet.core.education.theory.TheoryProgressSnapshot
import com.elysium369.meet.core.education.theory.TheoryQuestionMemory
import com.elysium369.meet.identity.ActivePrincipalKernel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@HiltViewModel
class TheoryExamViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val activePrincipalKernel: ActivePrincipalKernel,
) : ViewModel() {
    private val store = TheoryProgressStore(context)
    private val _progress = MutableStateFlow(TheoryProgressSnapshot())
    val progress: StateFlow<TheoryProgressSnapshot> = _progress.asStateFlow()
    private var principalId: String = activePrincipalKernel.current().id

    init {
        viewModelScope.launch {
            activePrincipalKernel.activePrincipal.collectLatest { principal ->
                principalId = principal.id
                _progress.value = store.load(principal.id)
            }
        }
    }

    fun selectTrack(track: TheoryLicenseTrack) = mutate { it.copy(track = track) }

    fun completeLesson(lessonId: String) = mutate {
        it.copy(completedLessons = it.completedLessons + lessonId)
    }

    fun recordAnswer(questionId: String, correct: Boolean, grade: ReviewGrade) = mutate { snapshot ->
        val current = snapshot.memories[questionId] ?: TheoryQuestionMemory(questionId)
        val effectiveGrade = if (correct) grade else ReviewGrade.AGAIN
        val scheduled = TheoryLearningEngine.schedule(current, effectiveGrade, LocalDate.now().toEpochDay())
        snapshot.copy(
            memories = snapshot.memories + (questionId to scheduled),
            totalAnswered = snapshot.totalAnswered + 1,
            totalCorrect = snapshot.totalCorrect + if (correct) 1 else 0,
        )
    }

    fun recordExam(track: TheoryLicenseTrack, score: Int) = mutate { snapshot ->
        val previous = snapshot.bestExamScore[track] ?: 0
        snapshot.copy(bestExamScore = snapshot.bestExamScore + (track to maxOf(previous, score)))
    }

    private fun mutate(block: (TheoryProgressSnapshot) -> TheoryProgressSnapshot) {
        val updated = block(_progress.value)
        _progress.value = updated
        store.save(principalId, updated)
    }
}

private class TheoryProgressStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "meet_theory_exam_progress_v1",
        Context.MODE_PRIVATE,
    )

    fun load(principalId: String): TheoryProgressSnapshot {
        val raw = preferences.getString(key(principalId), null) ?: return TheoryProgressSnapshot()
        return runCatching {
            val root = JSONObject(raw)
            val memoriesJson = root.optJSONObject("memories") ?: JSONObject()
            val memories = buildMap {
                val keys = memoriesJson.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val value = memoriesJson.getJSONObject(id)
                    put(
                        id,
                        TheoryQuestionMemory(
                            questionId = id,
                            repetitions = value.optInt("repetitions"),
                            intervalDays = value.optInt("intervalDays"),
                            ease = value.optDouble("ease", 2.5),
                            dueEpochDay = value.optLong("dueEpochDay"),
                            correctCount = value.optInt("correctCount"),
                            incorrectCount = value.optInt("incorrectCount"),
                        ),
                    )
                }
            }
            val bestJson = root.optJSONObject("bestExamScore") ?: JSONObject()
            val best = TheoryLicenseTrack.entries.associateWith { bestJson.optInt(it.name, 0) }
            val lessonsJson = root.optJSONArray("completedLessons") ?: JSONArray()
            val completed = buildSet {
                repeat(lessonsJson.length()) { index -> add(lessonsJson.getString(index)) }
            }
            TheoryProgressSnapshot(
                track = runCatching { TheoryLicenseTrack.valueOf(root.getString("track")) }
                    .getOrDefault(TheoryLicenseTrack.AUTOMOBILE),
                memories = memories,
                bestExamScore = best,
                completedLessons = completed,
                totalAnswered = root.optInt("totalAnswered"),
                totalCorrect = root.optInt("totalCorrect"),
            )
        }.getOrDefault(TheoryProgressSnapshot())
    }

    fun save(principalId: String, snapshot: TheoryProgressSnapshot) {
        val memories = JSONObject()
        snapshot.memories.forEach { (id, memory) ->
            memories.put(id, JSONObject().apply {
                put("repetitions", memory.repetitions)
                put("intervalDays", memory.intervalDays)
                put("ease", memory.ease)
                put("dueEpochDay", memory.dueEpochDay)
                put("correctCount", memory.correctCount)
                put("incorrectCount", memory.incorrectCount)
            })
        }
        val best = JSONObject()
        snapshot.bestExamScore.forEach { (track, score) -> best.put(track.name, score) }
        val root = JSONObject().apply {
            put("track", snapshot.track.name)
            put("memories", memories)
            put("bestExamScore", best)
            put("completedLessons", JSONArray(snapshot.completedLessons.toList()))
            put("totalAnswered", snapshot.totalAnswered)
            put("totalCorrect", snapshot.totalCorrect)
        }
        preferences.edit { putString(key(principalId), root.toString()) }
    }

    private fun key(principalId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(principalId.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
