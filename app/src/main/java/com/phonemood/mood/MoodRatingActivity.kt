package com.phonemood.mood

import com.phonemood.R
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonemood.phoneMood
import com.phonemood.ui.*
import kotlinx.coroutines.*

class MoodRatingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val id = intent.getStringExtra("checkpointId") ?: run { finish(); return }
        setContent {
            PhoneMoodTheme {
                val scope = rememberCoroutineScope()
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var minutes by remember { mutableStateOf<Int?>(null) }
                var valid by remember { mutableStateOf(false) }
                LaunchedEffect(id) {
                    val checkpoint = withContext(Dispatchers.IO) { phoneMood.repository.dao.checkpoint(id) }
                    if (checkpoint == null) error = getString(R.string.this_check_in_is_no_longer_available)
                    else if (checkpoint.responseStatus == "ANSWERED") { finish() }
                    else { minutes = checkpoint.checkpointMinutes; valid = true }
                }
                Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = { finish() }) { Icon(Icons.Outlined.Close, getString(R.string.not_now)) } }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.size(96.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SentimentSatisfiedAlt, null, Modifier.size(52.dp), tint = Forest) }
                        Text(getString(R.string.how_are_you_feeling_right_now_alternate), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                        Text(minutes?.let { getString(R.string.rating_minutes, it) } ?: getString(R.string.let_s_check_in), textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.bodyLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            (0..1).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..5).forEach { column -> val score = row * 5 + column
                                    OutlinedButton(modifier = Modifier.weight(1f).height(54.dp).semantics { contentDescription = getString(R.string.score_accessibility, score) }, contentPadding = PaddingValues(0.dp), enabled = valid && !saving, onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                val saved = withContext(Dispatchers.IO) { phoneMood.repository.respond(id, score) }
                                                check(saved) { getString(R.string.this_check_in_is_no_longer_available) }
                                                MoodNotificationManager(this@MoodRatingActivity).cancel(id)
                                                phoneMood.reconcileSoon(); Toast.makeText(this@MoodRatingActivity, getString(R.string.mood_saved, score), Toast.LENGTH_SHORT).show(); finish()
                                            } catch (e: Exception) { error = getString(R.string.could_not_save_please_try_again); saving = false }
                                        }
                                    }) { Text("$score", fontSize = 21.sp, fontFamily = FontFamily.Serif) }
                                }
                            } }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(getString(R.string.anchor_low), color = Muted, style = MaterialTheme.typography.bodySmall); Text(getString(R.string.anchor_high), color = Muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (saving) CircularProgressIndicator(Modifier.size(24.dp))
                        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Text(getString(R.string.no_right_answer_just_your_answer_tap_a_number_to_save_and_return_to_your_da), textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { finish() }) { Text(getString(R.string.not_now)) }
                    }
                }
            }
        }
    }
}
