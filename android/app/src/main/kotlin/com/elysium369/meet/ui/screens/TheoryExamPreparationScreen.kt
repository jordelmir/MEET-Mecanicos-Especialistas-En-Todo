package com.elysium369.meet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.education.theory.ReviewGrade
import com.elysium369.meet.core.education.theory.TheoryExamKnowledge
import com.elysium369.meet.core.education.theory.TheoryExamResult
import com.elysium369.meet.core.education.theory.TheoryLearningEngine
import com.elysium369.meet.core.education.theory.TheoryLesson
import com.elysium369.meet.core.education.theory.TheoryLicenseTrack
import com.elysium369.meet.core.education.theory.TheoryQuestion
import com.elysium369.meet.core.education.theory.TheorySource
import com.elysium369.meet.core.education.theory.TheorySourceKind
import com.elysium369.meet.core.education.theory.TheoryTopic
import com.elysium369.meet.ui.TheoryExamViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors
import java.time.LocalDate
import kotlin.random.Random

private enum class TheoryMode { HOME, LESSONS, PRACTICE, EXAM, RESULT, SOURCES }

@Composable
fun TheoryExamPreparationScreen(
    viewModel: TheoryExamViewModel,
    onBack: () -> Unit,
) {
    val progress by viewModel.progress.collectAsState()
    var modeName by rememberSaveable { mutableStateOf(TheoryMode.HOME.name) }
    var selectedLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionQuestions by remember { mutableStateOf(emptyList<TheoryQuestion>()) }
    var sessionIndex by rememberSaveable { mutableIntStateOf(0) }
    var practiceAnswer by rememberSaveable { mutableStateOf<Int?>(null) }
    val examAnswers = remember { mutableStateMapOf<String, Int>() }
    var result by remember { mutableStateOf<TheoryExamResult?>(null) }
    val mode = TheoryMode.valueOf(modeName)

    fun openPractice() {
        sessionQuestions = TheoryLearningEngine.practiceSet(
            track = progress.track,
            memories = progress.memories,
            todayEpochDay = LocalDate.now().toEpochDay(),
            size = 10,
            seed = Random.nextInt(),
        )
        sessionIndex = 0
        practiceAnswer = null
        modeName = TheoryMode.PRACTICE.name
    }

    fun openExam() {
        sessionQuestions = TheoryLearningEngine.exam(progress.track, Random.nextInt())
        examAnswers.clear()
        sessionIndex = 0
        result = null
        modeName = TheoryMode.EXAM.name
    }

    val backAction = {
        if (mode == TheoryMode.HOME) onBack() else {
            modeName = TheoryMode.HOME.name
            selectedLessonId = null
        }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "ACADEMIA VIAL\nMEET THEORY LAB",
                onBackClick = backAction,
                backgroundColor = MeetColors.backgroundDark,
            )
        },
    ) { padding ->
        AnimatedContent(targetState = mode, label = "theoryMode") { target ->
            when (target) {
                TheoryMode.HOME -> TheoryHome(
                    modifier = Modifier.padding(padding),
                    track = progress.track,
                    accuracy = progress.accuracyPercent,
                    mastery = TheoryLearningEngine.masteryPercent(progress.track, progress.memories),
                    bestScore = progress.bestExamScore[progress.track] ?: 0,
                    completedLessons = progress.completedLessons.size,
                    onTrack = viewModel::selectTrack,
                    onLessons = { modeName = TheoryMode.LESSONS.name },
                    onPractice = ::openPractice,
                    onExam = ::openExam,
                    onSources = { modeName = TheoryMode.SOURCES.name },
                )
                TheoryMode.LESSONS -> TheoryLessons(
                    modifier = Modifier.padding(padding),
                    track = progress.track,
                    selectedLessonId = selectedLessonId,
                    completedLessons = progress.completedLessons,
                    onSelect = { selectedLessonId = it },
                    onComplete = viewModel::completeLesson,
                    onPractice = ::openPractice,
                )
                TheoryMode.PRACTICE -> PracticeSession(
                    modifier = Modifier.padding(padding),
                    questions = sessionQuestions,
                    index = sessionIndex,
                    selectedAnswer = practiceAnswer,
                    onAnswer = { practiceAnswer = it },
                    onNext = { grade ->
                        val question = sessionQuestions[sessionIndex]
                        val correct = practiceAnswer == question.correctIndex
                        viewModel.recordAnswer(question.id, correct, grade)
                        if (sessionIndex == sessionQuestions.lastIndex) {
                            modeName = TheoryMode.HOME.name
                        } else {
                            sessionIndex += 1
                            practiceAnswer = null
                        }
                    },
                )
                TheoryMode.EXAM -> ExamSession(
                    modifier = Modifier.padding(padding),
                    questions = sessionQuestions,
                    index = sessionIndex,
                    answers = examAnswers,
                    onAnswer = { id, answer -> examAnswers[id] = answer },
                    onIndex = { sessionIndex = it },
                    onFinish = {
                        result = TheoryLearningEngine.evaluate(sessionQuestions, examAnswers)
                        viewModel.recordExam(progress.track, requireNotNull(result).score)
                        modeName = TheoryMode.RESULT.name
                    },
                )
                TheoryMode.RESULT -> ExamResultScreen(
                    modifier = Modifier.padding(padding),
                    result = requireNotNull(result),
                    onRetry = ::openExam,
                    onReview = ::openPractice,
                    onHome = { modeName = TheoryMode.HOME.name },
                )
                TheoryMode.SOURCES -> TheorySources(
                    modifier = Modifier.padding(padding),
                    sources = TheoryExamKnowledge.sources,
                )
            }
        }
    }
}

@Composable
private fun TheoryHome(
    modifier: Modifier,
    track: TheoryLicenseTrack,
    accuracy: Int,
    mastery: Int,
    bestScore: Int,
    completedLessons: Int,
    onTrack: (TheoryLicenseTrack) -> Unit,
    onLessons: () -> Unit,
    onPractice: () -> Unit,
    onExam: () -> Unit,
    onSources: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = MeetColors.cyberCyan,
                backgroundColor = MeetColors.backgroundDeep,
                enableHolo3D = true,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(MeetColors.cyberCyan.copy(alpha = 0.16f), Color.Transparent, MeetColors.neonGreen.copy(alpha = 0.08f)),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("LICENCIA · CONOCIMIENTO · VIDA", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("ENTRENÁ DECISIONES,\nNO RESPUESTAS", color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Plan 2026 separado por tipo de vehículo. Estudiá, recordá, aplicá y medí tu preparación sin confundir simulación con examen oficial.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        VerificationBadge()
                    }
                }
            }
        }
        item {
            Text("¿PARA CUÁL PRUEBA TE PREPARÁS?", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TheoryLicenseTrack.entries.forEach { option ->
                    TrackButton(option, option == track, Modifier.weight(1f)) { onTrack(option) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("DOMINIO", "$mastery%", MeetColors.neonGreen, Modifier.weight(1f))
                MetricTile("PRECISIÓN", "$accuracy%", MeetColors.cyberCyan, Modifier.weight(1f))
                MetricTile("MEJOR", "$bestScore", if (bestScore >= 80) MeetColors.neonGreen else MeetColors.warning, Modifier.weight(1f))
            }
        }
        item {
            TheoryActionCard("RUTA DE APRENDIZAJE", "$completedLessons / ${TheoryExamKnowledge.lessonsFor(track).size} lecciones · conceptos y decisiones", Icons.Default.MenuBook, MeetColors.cyberCyan, onLessons)
        }
        item {
            TheoryActionCard("REPASO INTELIGENTE", "10 preguntas priorizadas por vencimiento y errores", Icons.Default.Psychology, MeetColors.neonGreen, onPractice)
        }
        item {
            TheoryActionCard("SIMULACRO MEET", "40 preguntas · umbral de preparación 80 · resultado por tema", Icons.Default.Timer, MeetColors.warning, onExam)
        }
        item {
            OfficialTruthCard(onSources)
        }
        item {
            LearningMethodCard()
        }
    }
}

@Composable
private fun TrackButton(track: TheoryLicenseTrack, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MeetColors.cyberCyan.copy(alpha = 0.14f) else MeetColors.cardBackground,
        border = BorderStroke(1.dp, if (selected) MeetColors.cyberCyan else MeetColors.borderSubtle),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (track == TheoryLicenseTrack.AUTOMOBILE) "▰" else "◉", color = if (selected) MeetColors.cyberCyan else MeetColors.textSecondary, fontSize = 20.sp)
            Text(track.shortName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(if (track == TheoryLicenseTrack.AUTOMOBILE) "Clase B" else "Clase A", color = MeetColors.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MeetColors.cardBackground, border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, color = MeetColors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TheoryActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    EliteCard(modifier = Modifier.fillMaxWidth(), glowColor = color, backgroundColor = MeetColors.cardBackground, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(subtitle, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.Default.PlayArrow, null, tint = color)
        }
    }
}

@Composable
private fun OfficialTruthCard(onSources: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MeetColors.warning.copy(alpha = 0.07f), border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.45f))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VERDAD OFICIAL 2026", color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("Desde el 2 de marzo hay pruebas distintas para automóvil y motocicleta.", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("El manual oficial cuesta ₡3.500 y la prueba ₡5.000. MEET no es MOPT/COSEVI, no distribuye el manual pagado y sus preguntas son práctica original.", color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
            OutlinedButton(onClick = onSources, border = BorderStroke(1.dp, MeetColors.warning)) {
                Icon(Icons.Default.Description, null, tint = MeetColors.warning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text("FUENTES Y ENLACES OFICIALES", color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LearningMethodCard() {
    EliteCard(modifier = Modifier.fillMaxWidth(), glowColor = MeetColors.electricBlue, backgroundColor = MeetColors.backgroundDeep) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("MÉTODO DE RETENCIÓN", color = MeetColors.electricBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            listOf(
                "1  COMPRENDER · una regla y una decisión real",
                "2  RECUPERAR · responder sin mirar apuntes",
                "3  CORREGIR · explicación inmediata del error",
                "4  ESPACIAR · volver antes de olvidar por completo",
                "5  INTERCALAR · mezclar señales, riesgo, ley y maniobras",
            ).forEach { Text(it, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp) }
            Text("Basado en investigación sobre retrieval practice y spacing; no en releer hasta sentir familiaridad.", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VerificationBadge() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = MeetColors.neonGreen, modifier = Modifier.size(15.dp))
        Text("Fuentes verificadas: ${TheoryExamKnowledge.VERIFIED_ON}", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TheoryLessons(
    modifier: Modifier,
    track: TheoryLicenseTrack,
    selectedLessonId: String?,
    completedLessons: Set<String>,
    onSelect: (String?) -> Unit,
    onComplete: (String) -> Unit,
    onPractice: () -> Unit,
) {
    val lessons = TheoryExamKnowledge.lessonsFor(track)
    val selected = lessons.firstOrNull { it.id == selectedLessonId }
    if (selected != null) {
        LessonDetail(modifier, selected, selected.id in completedLessons, { onComplete(selected.id) }, { onSelect(null) }, onPractice)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("RUTA · ${track.displayName.uppercase()}", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("Seis dominios oficiales de formación + aplicación específica al vehículo.", color = MeetColors.textSecondary, fontSize = 12.sp)
        }
        items(lessons, key = { it.id }) { lesson ->
            val complete = lesson.id in completedLessons
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(lesson.id) },
                shape = RoundedCornerShape(14.dp),
                color = MeetColors.cardBackground,
                border = BorderStroke(1.dp, if (complete) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderSubtle),
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(lesson.topic.icon, color = if (complete) MeetColors.neonGreen else MeetColors.cyberCyan, fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text(lesson.topic.displayName.uppercase(), color = MeetColors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(lesson.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(lesson.objective, color = MeetColors.textSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    if (complete) Icon(Icons.Default.CheckCircle, "Completada", tint = MeetColors.neonGreen)
                }
            }
        }
    }
}

@Composable
private fun LessonDetail(modifier: Modifier, lesson: TheoryLesson, complete: Boolean, onComplete: () -> Unit, onBack: () -> Unit, onPractice: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, MeetColors.borderSubtle)) {
                Icon(Icons.Default.ArrowBack, null, tint = MeetColors.textSecondary, modifier = Modifier.size(16.dp))
                Text(" MÓDULOS", color = MeetColors.textSecondary, fontSize = 10.sp)
            }
        }
        item {
            Text("${lesson.topic.icon}  ${lesson.topic.displayName.uppercase()}", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(lesson.title, color = Color.White, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
            Text(lesson.objective, color = MeetColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        item {
            EliteCard(modifier = Modifier.fillMaxWidth(), glowColor = MeetColors.cyberCyan, backgroundColor = MeetColors.cardBackground) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lesson.keyPoints.forEachIndexed { index, point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("0${index + 1}", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text(point, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MeetColors.neonGreen.copy(alpha = 0.08f), border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))) {
                Column(Modifier.padding(15.dp)) {
                    Text("REGLA DE DECISIÓN", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(lesson.decisionRule, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Button(onClick = { onComplete(); onPractice() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)) {
                Text(if (complete) "REPASAR AHORA" else "MARCAR APRENDIDO Y PRACTICAR", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun PracticeSession(
    modifier: Modifier,
    questions: List<TheoryQuestion>,
    index: Int,
    selectedAnswer: Int?,
    onAnswer: (Int) -> Unit,
    onNext: (ReviewGrade) -> Unit,
) {
    if (questions.isEmpty()) return
    val question = questions[index]
    val revealed = selectedAnswer != null
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SessionHeader("REPASO INTELIGENTE", index + 1, questions.size, MeetColors.neonGreen) }
        item { QuestionCard(question, selectedAnswer, revealed, onAnswer) }
        if (revealed) {
            item {
                val correct = selectedAnswer == question.correctIndex
                FeedbackCard(correct, question.explanation, question.sourceIds)
            }
            item {
                Text("¿QUÉ TAN DIFÍCIL FUE RECORDARLO?", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GradeButton("OTRA VEZ", ReviewGrade.AGAIN, MeetColors.error, Modifier.weight(1f), onNext)
                    GradeButton("DIFÍCIL", ReviewGrade.HARD, MeetColors.warning, Modifier.weight(1f), onNext)
                    GradeButton("BIEN", ReviewGrade.GOOD, MeetColors.cyberCyan, Modifier.weight(1f), onNext)
                    GradeButton("FÁCIL", ReviewGrade.EASY, MeetColors.neonGreen, Modifier.weight(1f), onNext)
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(question: TheoryQuestion, selectedAnswer: Int?, revealed: Boolean, onAnswer: (Int) -> Unit) {
    EliteCard(modifier = Modifier.fillMaxWidth(), glowColor = MeetColors.cyberCyan, backgroundColor = MeetColors.backgroundDeep) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${question.topic.icon} ${question.topic.displayName.uppercase()}", color = MeetColors.cyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(question.prompt, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
            question.options.forEachIndexed { index, option ->
                val isSelected = selectedAnswer == index
                val isCorrect = revealed && index == question.correctIndex
                val color = when {
                    isCorrect -> MeetColors.neonGreen
                    revealed && isSelected -> MeetColors.error
                    isSelected -> MeetColors.cyberCyan
                    else -> MeetColors.borderSubtle
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !revealed) { onAnswer(index) },
                    shape = RoundedCornerShape(11.dp),
                    color = color.copy(alpha = if (isSelected || isCorrect) 0.12f else 0.04f),
                    border = BorderStroke(1.dp, color),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(('A'.code + index).toChar().toString(), color = if (isSelected || isCorrect) color else MeetColors.textMuted, fontWeight = FontWeight.Black)
                        Text(option, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(correct: Boolean, explanation: String, sourceIds: Set<String>) {
    val color = if (correct) MeetColors.neonGreen else MeetColors.error
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.08f), border = BorderStroke(1.dp, color.copy(alpha = 0.6f))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (correct) "DECISIÓN CORRECTA" else "CORRECCIÓN ACTIVA", color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(explanation, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
            Text("Trazabilidad: ${sourceIds.joinToString()}", color = MeetColors.textMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun GradeButton(label: String, grade: ReviewGrade, color: Color, modifier: Modifier, onNext: (ReviewGrade) -> Unit) {
    OutlinedButton(onClick = { onNext(grade) }, modifier = modifier, contentPadding = PaddingValues(5.dp), border = BorderStroke(1.dp, color)) {
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SessionHeader(label: String, current: Int, total: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("$current / $total", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { current.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = color, trackColor = MeetColors.borderSubtle)
    }
}

@Composable
private fun ExamSession(
    modifier: Modifier,
    questions: List<TheoryQuestion>,
    index: Int,
    answers: Map<String, Int>,
    onAnswer: (String, Int) -> Unit,
    onIndex: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    if (questions.isEmpty()) return
    val question = questions[index]
    val selected = answers[question.id]
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SessionHeader("SIMULACRO MEET · SIN RETROALIMENTACIÓN", index + 1, questions.size, MeetColors.warning) }
        item { QuestionCard(question, selected, false) { onAnswer(question.id, it) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onIndex(index - 1) }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("ANTERIOR") }
                if (index < questions.lastIndex) {
                    Button(onClick = { onIndex(index + 1) }, enabled = selected != null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning)) { Text("SIGUIENTE", color = Color.Black, fontWeight = FontWeight.Black) }
                } else {
                    Button(onClick = onFinish, enabled = answers.size == questions.size, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)) { Text("CALIFICAR", color = Color.Black, fontWeight = FontWeight.Black) }
                }
            }
        }
        item { Text("Respondidas: ${answers.size}/${questions.size}. Este simulacro es una herramienta MEET y no reproduce la interfaz ni el banco oficial.", color = MeetColors.textMuted, fontSize = 9.sp, lineHeight = 13.sp) }
    }
}

@Composable
private fun ExamResultScreen(modifier: Modifier, result: TheoryExamResult, onRetry: () -> Unit, onReview: () -> Unit, onHome: () -> Unit) {
    val color = if (result.passed) MeetColors.neonGreen else MeetColors.warning
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { result.score / 100f }, modifier = Modifier.size(150.dp), strokeWidth = 10.dp, color = color, trackColor = MeetColors.borderSubtle)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(result.score.toString(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    Text("DE 100", color = MeetColors.textMuted, fontSize = 9.sp)
                }
            }
        }
        item { Text(if (result.passed) "UMBRAL MEET ALCANZADO" else "TODAVÍA HAY QUE CONSOLIDAR", color = color, fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
        item { Text("${result.correct} correctas de ${result.total}. El 80 es el umbral oficial publicado, pero aprobar simulaciones no garantiza el resultado real.", color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center) }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MeetColors.cardBackground, border = BorderStroke(1.dp, MeetColors.borderSubtle)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PRIORIDAD DE REPASO", color = MeetColors.warning, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    result.weakestTopics.forEach { Text("${it.icon}  ${it.displayName}", color = Color.White, fontSize = 12.sp) }
                }
            }
        }
        item { Button(onClick = onReview, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)) { Text("REPASAR PUNTOS DÉBILES", color = Color.Black, fontWeight = FontWeight.Black) } }
        item { OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MeetColors.cyberCyan)) { Icon(Icons.Default.Refresh, null, tint = MeetColors.cyberCyan); Text(" NUEVO SIMULACRO", color = MeetColors.cyberCyan) } }
        item { OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("VOLVER AL TABLERO") } }
    }
}

@Composable
private fun TheorySources(modifier: Modifier, sources: List<TheorySource>) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("CENTRO DE FUENTES", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("Autoridad, vigencia y límites visibles. Abrí siempre la fuente viva antes de depender de una cifra legal.", color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
            VerificationBadge()
        }
        items(sources, key = { it.id }) { source ->
            val color = when (source.kind) {
                TheorySourceKind.OFFICIAL -> MeetColors.neonGreen
                TheorySourceKind.LAW -> MeetColors.cyberCyan
                TheorySourceKind.RESEARCH -> MeetColors.electricBlue
                TheorySourceKind.INDEPENDENT_PRACTICE -> MeetColors.warning
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(source.url) },
                shape = RoundedCornerShape(14.dp), color = MeetColors.cardBackground, border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(source.kind.name.replace('_', ' '), color = color, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Icon(Icons.Default.OpenInNew, "Abrir enlace", tint = color, modifier = Modifier.size(16.dp))
                    }
                    Text(source.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(source.authority, color = MeetColors.textSecondary, fontSize = 10.sp)
                    Text(source.note, color = MeetColors.textMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
