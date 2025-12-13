package com.example.calorielens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class FoodLog(
    val id: String = "",
    val foodName: String = "",
    val calories: Float = 0f,
    val date: String = "",
    val timestamp: Date? = null
)

@Composable
fun HistoryScreen() {
    val auth = Firebase.auth
    val db = Firebase.firestore
    val context = LocalContext.current
    val userId = auth.currentUser?.uid

    var foodLogs by remember { mutableStateOf<Map<String, List<FoodLog>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch Logs
    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                val snapshot = db.collection("users").document(userId).collection("foodLogs")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()

                val logs = snapshot.documents.map { doc ->
                    val data = doc.data!!
                    FoodLog(
                        id = doc.id,
                        foodName = data["foodName"] as String,
                        calories = (data["calories"] as Number).toFloat(),
                        date = data["date"] as String,
                        timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                    )
                }
                
                // Group by Date
                foodLogs = logs.groupBy { it.date }
                isLoading = false
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading history: ${e.message}", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Food History", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (foodLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No food logged yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                foodLogs.forEach { (date, logs) ->
                    val dailyTotal = logs.sumOf { it.calories.toDouble() }
                    
                    item {
                        // Date Header
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDate(date), fontWeight = FontWeight.Bold)
                            Text("Total: ${String.format("%.0f", dailyTotal)} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    items(logs) { log ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(log.foodName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(log.timestamp ?: Date()),
                                        fontSize = 12.sp, color = Color.Gray
                                    )
                                }
                                Text("${String.format("%.0f", log.calories)} kcal")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDate(dateString: String): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    return if (dateString == today) "Today" else dateString
}