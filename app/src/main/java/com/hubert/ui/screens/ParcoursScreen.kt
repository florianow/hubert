package com.hubert.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hubert.data.repository.ParcoursProgress
import com.hubert.data.repository.ParcoursRepository
import com.hubert.data.repository.ParcoursUnit
import com.hubert.ui.theme.*
import com.hubert.viewmodel.OpenLesson
import com.hubert.viewmodel.ParcoursState

val ParcoursBlue = Color(0xFF1565C0)

/** Name of the game an étape practises, for the overview. */
fun parcoursGameName(game: String): String = when (game) {
    "conjugation" -> "Conjuguez!"
    "preposition" -> "Préposez!"
    "relative_pronoun" -> "Relativisez!"
    "agreement" -> "Accordez!"
    "numbers" -> "Nombrez!"
    "gender_snap" -> "Classez!"
    else -> game
}

data class ParcoursActions(
    val back: () -> Unit,              // leave the Parcours
    val toOverview: () -> Unit,
    val selectUnit: (ParcoursUnit) -> Unit,
    val openLesson: (ParcoursUnit, Int) -> Unit,
    val lessonNext: () -> Unit,
    val closeLesson: () -> Unit,
    val jumpTo: (ParcoursUnit) -> Unit,
    val setEnabled: (Boolean) -> Unit,
    val startMenu: () -> Unit,
    val startParlez: (ParcoursUnit) -> Unit,
    val speak: (String) -> Unit
)

@Composable
fun ParcoursScreen(state: ParcoursState, actions: ParcoursActions) {
    val lesson = state.lesson
    val unit = state.selectedUnit
    when {
        lesson != null -> {
            BackHandler(onBack = actions.closeLesson)
            LessonView(lesson, actions)
        }
        unit != null -> UnitDetail(unit, state, actions)
        else -> Overview(state, actions)
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = ParcoursBlue)
    }
}

@Composable
private fun Overview(state: ParcoursState, actions: ParcoursActions) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val currentIndex = state.units.indexOfFirst { it.id == state.progress.current }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Header("Parcours", actions.back)
        Text(
            text = "${state.progress.passed.size} von ${state.units.size} Étapes geschafft · " +
                "Leçon lesen → ${ParcoursRepository.MENUS_BEFORE_CONTROL} Menüs → Contrôle (Complet ab 12/20). " +
                "Bestandene Étapes kommen nach 3, 7, 21 und 60 Tagen als Révision ins Menü zurück.",
            fontSize = 12.sp,
            color = muted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Menu du jour folgt dem Parcours", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = state.enabled, onCheckedChange = actions.setEnabled)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var lastLevel = ""
            state.units.forEachIndexed { index, unit ->
                if (unit.level != lastLevel) {
                    lastLevel = unit.level
                    item(key = "level_${unit.level}") {
                        Text(
                            text = unit.level,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = ParcoursBlue,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                }
                item(key = unit.id) {
                    UnitRow(index, unit, state.progress, onClick = { actions.selectUnit(unit) })
                }
            }
        }
    }
}

@Composable
private fun UnitRow(index: Int, unit: ParcoursUnit, progress: ParcoursProgress, onClick: () -> Unit) {
    val passed = unit.id in progress.passed
    val current = unit.id == progress.current
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) ParcoursBlue.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .then(if (current) Modifier.border(1.5.dp, ParcoursBlue, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        passed -> CorrectGreen
                        current -> ParcoursBlue
                        else -> muted.copy(alpha = 0.15f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (passed) "✓" else "${index + 1}",
                color = if (passed || current) Color.White else muted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(unit.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = listOfNotNull(parcoursGameName(unit.game), unit.parlez?.let { "🗣️ ${it.themeFr}" }).joinToString(" · "),
                fontSize = 12.sp,
                color = muted
            )
            progress.reviews[unit.id]?.let { r ->
                val days = r.dueDay - java.time.LocalDate.now().toEpochDay()
                Text(
                    text = if (days <= 0) "🔁 Wiederholung fällig – kommt im nächsten Menü" else "🔁 Wiederholung in $days ${if (days == 1L) "Tag" else "Tagen"}",
                    fontSize = 11.sp,
                    color = if (days <= 0) ParcoursBlue else muted
                )
            }
        }
        if (current) Text("jetzt", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ParcoursBlue)
    }
}

@Composable
private fun UnitDetail(unit: ParcoursUnit, state: ParcoursState, actions: ParcoursActions) {
    BackHandler(onBack = actions.toOverview)
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val index = state.units.indexOf(unit)
    val p = state.progress
    val current = unit.id == p.current
    val menus = p.menus[unit.id] ?: 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Header("Étape ${index + 1} · ${unit.level}", actions.toOverview)
        Spacer(Modifier.height(8.dp))
        Text(unit.title, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(
            text = "Grammatik-Gang im Menü: ${parcoursGameName(unit.game)}" + (unit.parlez?.let { " · Gespräch: ${it.themeFr}" } ?: ""),
            fontSize = 13.sp,
            color = muted
        )
        Spacer(Modifier.height(20.dp))

        StepLine("📖", "Leçon lesen", unit.id in p.lessonsRead)
        StepLine("🍽️", "Menüs: ${minOf(menus, ParcoursRepository.MENUS_BEFORE_CONTROL)}/${ParcoursRepository.MENUS_BEFORE_CONTROL}", menus >= ParcoursRepository.MENUS_BEFORE_CONTROL)
        StepLine("🎓", "Contrôle: ein Complet ab 12/20", unit.id in p.passed)
        p.best[unit.id]?.let { best ->
            Text("Bestes Menü: ${best / 5.0}/20".replace(".0/", "/").replace('.', ','), fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("Leçons", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        unit.lessons.forEachIndexed { i, lesson ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { actions.openLesson(unit, i) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📖", fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(lesson.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("›", fontSize = 18.sp, color = muted)
            }
        }

        Spacer(Modifier.weight(1f))
        if (unit.parlez != null) {
            OutlinedButton(
                onClick = { actions.startParlez(unit) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("🗣️ Parlez! – „${unit.parlez.themeFr}“", color = ParcoursBlue)
            }
            Text(
                text = "Freies Gespräch mit Hubert, das genau diese Grammatik braucht (optional).",
                fontSize = 11.sp,
                color = muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
            )
        }
        if (current) {
            Button(
                onClick = actions.startMenu,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MenuWine)
            ) {
                Text("🍽️ Menu du jour dazu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            OutlinedButton(
                onClick = { actions.jumpTo(unit) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (unit.id in p.passed) "Diese Étape wiederholen" else "Hier weitermachen", color = ParcoursBlue)
            }
        }
    }
}

@Composable
private fun StepLine(icon: String, text: String, done: Boolean) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (done) "✓" else "", color = CorrectGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** A grammar note from anki_french, rendered as HTML; French taps speak, German taps reveal. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LessonView(open: OpenLesson, actions: ParcoursActions) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val page = remember(open.html, dark) { lessonPage(open.html, dark) }
    val speak = rememberUpdatedState(actions.speak)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.closeLesson) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Leçon ${open.index + 1}/${open.unit.lessons.size} · ${open.unit.title}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ParcoursBlue,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "Tippe auf Französisch zum Anhören, auf ein blaues 👆-Kästchen für die Übersetzung.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun speak(text: String) {
                            post { speak.value(text) }
                        }
                    }, "Hubert")
                }
            },
            update = { view ->
                if (view.tag != page) {
                    view.tag = page
                    view.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
                }
            }
        )
        Button(
            onClick = actions.lessonNext,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
        ) {
            Text(if (open.isLast) "Verstanden ✓" else "Weiter ›", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

private fun lessonPage(html: String, dark: Boolean): String {
    val text = if (dark) "#E8E8E8" else "#1F1F1F"
    val muted = if (dark) "#A0A0A0" else "#666666"
    val card = if (dark) "#262A33" else "#FFFFFF"
    val line = if (dark) "#3A3F4A" else "#E6E6E6"
    val fr = if (dark) "#82B1FF" else "#1565C0"
    val attention = if (dark) "#4A3520" else "#FFF3E0"
    val tip = if (dark) "#1F3A24" else "#E8F5E9"
    val hidden = if (dark) "#22304A" else "#EAF2FD"
    return """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { font-family: sans-serif; font-size: 16px; line-height: 1.5; color: $text; background: transparent; margin: 0; padding: 4px 18px 24px; }
.section-title { font-size: 24px; font-weight: 800; margin: 12px 0 8px; }
.section-title[data-topic]::after { content: attr(data-topic); font-size: 12px; font-weight: 700; color: #fff; background: $fr; border-radius: 8px; padding: 2px 8px; margin-left: 8px; vertical-align: middle; }
h3 { font-size: 18px; margin: 22px 0 6px; }
h4 { font-size: 15px; margin: 16px 0 4px; }
p, li { margin: 6px 0; }
ul { padding-left: 20px; }
.examples { background: $card; border-left: 3px solid $fr; border-radius: 10px; padding: 8px 12px; margin: 10px 0; }
.examples .fr, .examples > div > .fr { font-weight: 600; color: $fr; margin-top: 4px; }
.examples .de { color: $muted; margin-bottom: 4px; }
.fr { cursor: pointer; }
span.fr { color: $fr; }
.spoiler { border: 1.5px dashed transparent; border-radius: 8px; padding: 1px 6px; transition: color .2s, text-shadow .2s, background .2s; }
.spoiler:not(.clicked) { color: transparent !important; text-shadow: 0 0 3px $muted; background: $hidden; border-color: $fr; cursor: pointer; }
.spoiler:not(.clicked)::before { content: "👆 "; color: $fr; text-shadow: none; }
div.spoiler { margin: 2px 0; }
.attention, .tip { border-radius: 10px; padding: 8px 12px; margin: 10px 0; }
.attention { background: $attention; }
.attention::before { content: "⚠️ "; }
.tip { background: $tip; }
.tip::before { content: "💡 "; }
table { border-collapse: collapse; width: 100%; margin: 8px 0; background: $card; border-radius: 10px; overflow: hidden; }
td, th { padding: 6px 8px; border-bottom: 1px solid $line; text-align: left; vertical-align: top; }
.condensed { color: $muted; font-size: 12px; white-space: nowrap; }
.ipa { color: $muted; font-family: monospace; font-size: 14px; }
u { text-decoration-color: $fr; text-decoration-thickness: 2px; text-underline-offset: 3px; }
a { color: inherit; text-decoration: none; border-bottom: 1px dotted $muted; }
del { color: $muted; }
.rounded-border { border: 1px solid $line; border-radius: 6px; padding: 0 4px; }
</style></head><body>
$html
<script>
document.addEventListener('click', function (e) {
  var a = e.target.closest('a'); if (a) { e.preventDefault(); }
  var sp = e.target.closest('.spoiler');
  if (sp && !sp.classList.contains('clicked')) { sp.classList.add('clicked'); return; }
  var fr = e.target.closest('.fr');
  if (fr) { Hubert.speak(fr.innerText); }
});
</script>
</body></html>
"""
}
