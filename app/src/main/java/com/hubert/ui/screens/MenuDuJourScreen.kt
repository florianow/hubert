package com.hubert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*
import com.hubert.viewmodel.Course
import com.hubert.viewmodel.GrammarMistake
import com.hubert.viewmodel.MenuType
import com.hubert.viewmodel.CourseResult
import com.hubert.viewmodel.MenuDuJourState
import com.hubert.viewmodel.ReviewItem
import kotlin.math.roundToInt

val MenuWine = Color(0xFF9C2A4B)

/** French school mention for a grade on /20. */
fun mentionFor(grade: Double): String = when {
    grade >= 18 -> "Félicitations du jury"
    grade >= 16 -> "Très bien"
    grade >= 14 -> "Bien"
    grade >= 12 -> "Assez bien"
    grade >= 10 -> "Passable"
    else -> "Insuffisant"
}

private fun gradeColor(grade: Double): Color = when {
    grade >= 14 -> CorrectGreen
    grade >= 10 -> GermanGold
    else -> WrongRed
}

private fun formatGrade(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}

/** Menu card shown before the first course, and the progress card between courses. */
@Composable
fun MenuDuJourCourseScreen(
    state: MenuDuJourState,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Menü abbrechen",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Menu du jour",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = MenuWine
            )
            Text(
                text = "${state.menuType.title} · Thème : ${state.category}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            if (state.isIntro) {
                val line = when {
                    state.menuType == MenuType.A_LA_CARTE && state.focusCount > 0 ->
                        "+ ${state.focusCount} Wörter, die dir zuletzt schwerfielen"
                    state.menuType != MenuType.A_LA_CARTE ->
                        listOfNotNull(
                            state.discoveryCards.size.takeIf { it > 0 }?.let { "Amuse-bouche : $it neue Wörter" },
                            state.dueCount.takeIf { it > 0 }?.let { "$it Wiederholungen fällig" }
                        ).joinToString(" · ").ifEmpty { null }
                    else -> null
                }
                if (line != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = line, fontSize = 12.sp, color = MenuWine.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MenuWine.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .background(MenuWine.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(vertical = 8.dp)
            ) {
                state.courses.forEachIndexed { index, course ->
                    CourseRow(
                        course = course,
                        result = state.results.getOrNull(index),
                        isNext = index == state.currentIndex
                    )
                }
            }

            state.results.lastOrNull()?.let { last ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${last.course.label} : ${last.totalCorrect} richtig, ${last.totalWrong} falsch",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        val next = state.nextCourse
        Button(
            onClick = onContinue,
            enabled = next != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MenuWine)
        ) {
            Text(
                text = if (state.isIntro) "Bon appétit !" else "Weiter: ${next?.label ?: ""}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun CourseRow(course: Course, result: CourseResult?, isNext: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isNext) MenuWine.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${course.label} — ${course.game.gameName}",
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                color = if (result != null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = course.goal,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (result != null) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CorrectGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${result.score}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = "${course.game.timeMs / 1000}s",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/** "L'addition": final grade, per-course breakdown and the words to review. */
@Composable
fun MenuDuJourAdditionScreen(
    state: MenuDuJourState,
    onTogglePin: (Int) -> Unit,
    onNewMenu: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val grade = state.grade
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "L'addition",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = MenuWine
            )
            Text(
                text = "${state.menuType.title} · ${state.category}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .border(4.dp, gradeColor(grade), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatGrade(grade),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        color = gradeColor(grade)
                    )
                    Text(
                        text = "/20",
                        fontSize = 18.sp,
                        color = gradeColor(grade).copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = mentionFor(grade),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = gradeColor(grade)
            )
            state.previousOverallScore?.let { prev ->
                val delta = (state.overallScore - prev) / 5.0
                Text(
                    text = when {
                        delta > 0 -> "▲ +${formatGrade(delta)} Punkte zum letzten Menü"
                        delta < 0 -> "▼ ${formatGrade(delta)} Punkte zum letzten Menü"
                        else -> "Gleich wie beim letzten Menü"
                    },
                    fontSize = 13.sp,
                    color = if (delta >= 0) CorrectGreen else WrongRed
                )
            }
            if (state.menusPlayed > 0) {
                Text(
                    text = "Menü Nr. ${state.menusPlayed}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        items(state.results) { result ->
            CourseScoreBar(result, state.previousCourseScores[result.course.game.gameType])
            Spacer(Modifier.height(10.dp))
        }

        if (state.review.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "À revoir — anpinnen, damit Hubert sie wiederholt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            }
            items(state.review) { item ->
                ReviewRow(item, isPinned = item.rank in state.pinnedRanks, onTogglePin = onTogglePin)
            }
        }

        if (state.grammarMistakes.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Grammatik & Zahlen — so wäre es richtig",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            }
            items(state.grammarMistakes) { GrammarMistakeRow(it) }
        }

        if (state.review.isEmpty() && state.grammarMistakes.isEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Kein einziger Fehler — chapeau !",
                    fontWeight = FontWeight.Bold,
                    color = CorrectGreen,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNewMenu,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MenuWine)
            ) {
                Text("Neues Menü bestellen", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBackToMenu,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Zurück zum Hauptmenü")
            }
        }
    }
}

@Composable
private fun CourseScoreBar(result: CourseResult, previous: Int?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${result.course.label} · ${result.course.game.gameName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (previous != null && previous != result.score) {
                val up = result.score > previous
                Text(
                    text = if (up) "▲ " else "▼ ",
                    fontSize = 11.sp,
                    color = if (up) CorrectGreen else WrongRed
                )
            }
            Text(text = "${result.score}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { result.score / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = gradeColor(result.score / 5.0),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
        Text(
            text = "${result.totalCorrect} richtig · ${result.totalWrong} falsch",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ReviewRow(item: ReviewItem, isPinned: Boolean, onTogglePin: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.french, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FrenchBlue)
            Text(
                text = "${item.german} · ${item.courses.joinToString(", ")}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = { onTogglePin(item.rank) }) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (isPinned) "Lösen" else "Anpinnen",
                tint = if (isPinned) AccentPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun GrammarMistakeRow(mistake: GrammarMistake) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = mistake.question,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            text = "${mistake.correctAnswer}  ·  nicht: ${mistake.yourAnswer}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CorrectGreen
        )
        Text(
            text = mistake.gameName,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

/** First step: Express, Complet or À la carte. */
@Composable
fun MenuDuJourTypeScreen(
    dailyCategory: String,
    completPreview: String,
    onChoose: (MenuType) -> Unit,
    onShowWeeklyReport: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "La carte",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            color = MenuWine,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Welches Menü darf es heute sein?",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        MenuType.entries.forEach { type ->
            MenuTypeCard(
                type = type,
                today = dailyCategory.takeIf { type != MenuType.A_LA_CARTE && it.isNotEmpty() },
                coursesToday = completPreview.takeIf { type == MenuType.COMPLET && it.isNotEmpty() },
                onClick = { onChoose(type) }
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onShowWeeklyReport,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("📊 Wochenbericht ansehen", color = MenuWine)
        }
    }
}

@Composable
private fun MenuTypeCard(type: MenuType, today: String?, coursesToday: String?, onClick: () -> Unit) {
    val courses = coursesToday ?: when (type) {
        MenuType.EXPRESS -> "Trouvez · Classez · Écrivez"
        MenuType.COMPLET -> "Trouvez · Grammatik · 3 aus Complétez, Classez, Écrivez, Nombrez"
        MenuType.A_LA_CARTE -> "Trouvez · Complétez · Classez · Écrivez"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MenuWine.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .background(MenuWine.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = type.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = MenuWine
        )
        Text(
            text = type.description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (today != null) {
            Text(
                text = "Heute : $today",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MenuWine
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = courses,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
    }
}

/** À la carte: pick the theme yourself. */
@Composable
fun MenuDuJourCategoryScreen(
    categories: List<String>,
    onChoose: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "À la carte — Thema wählen",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MenuWine
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                Text(
                    text = category,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MenuWine.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { onChoose(category) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

/** Amuse-bouche: the new words of the day, one card at a time, before the first course. */
@Composable
fun MenuDuJourDiscoveryScreen(
    state: MenuDuJourState,
    onAnswer: (alreadyKnown: Boolean) -> Unit,
    onSpeak: () -> Unit,
    onBack: () -> Unit
) {
    val card = state.currentCard ?: return
    val word = card.word
    // Article for nouns; with l' or a leading h (muet or aspiré isn't in the data) show the gender instead
    val isNoun = word.gender != null && word.pos in listOf("nm", "nf")
    val first = word.french.first().lowercaseChar()
    val article = when {
        !isNoun -> null
        first in "aeiouâéèêîôûœ" -> "l'"
        first == 'h' -> null
        word.gender == "m" -> "le "
        else -> "la "
    }
    val genderNote = if (isNoun && (article == "l'" || first == 'h')) {
        if (word.gender == "m") "männlich" else "weiblich"
    } else null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Menü abbrechen",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Amuse-bouche · ${state.discoveryIndex + 1}/${state.discoveryCards.size}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = MenuWine
            )
        }
        LinearProgressIndicator(
            progress = { (state.discoveryIndex + 1f) / state.discoveryCards.size },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
            color = MenuWine,
            trackColor = MenuWine.copy(alpha = 0.12f)
        )

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Neues Wort · Rang ${word.rank}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = (article ?: "") + word.french,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = FrenchBlue,
                textAlign = TextAlign.Center
            )
            val details = listOfNotNull(word.ipa?.let { "/$it/" }, genderNote).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(text = details, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = word.german,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            IconButton(
                onClick = onSpeak,
                modifier = Modifier
                    .size(56.dp)
                    .border(2.dp, FrenchBlue.copy(alpha = 0.3f), CircleShape)
                    .background(FrenchBlue.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Anhören",
                    tint = FrenchBlue
                )
            }
            if (card.exampleFr != null) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MenuWine.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = card.exampleFr, fontSize = 16.sp, textAlign = TextAlign.Center)
                    card.exampleDe?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onAnswer(true) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Kenne ich schon", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = { onAnswer(false) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MenuWine)
            ) {
                Text("Neu für mich →", fontWeight = FontWeight.Bold)
            }
        }
    }
}
