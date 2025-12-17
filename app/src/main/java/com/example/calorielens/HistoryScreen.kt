package com.example.calorielens

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

@Composable
fun HistoryScreen() {
    val auth = Firebase.auth
    val db = Firebase.firestore
    val context = LocalContext.current
    val userId = auth.currentUser?.uid

    // Group by Date -> List of Meals
    var groupedMeals by remember { mutableStateOf<Map<String, List<MealLog>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch Logs
    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                val snapshot = db.collection("users").document(userId).collection("mealLogs")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()

                val meals = snapshot.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        
                        // Parse Items List manually to be safe
                        val rawItems = data["items"] as? List<Map<String, Any>> ?: emptyList()
                        val parsedItems = rawItems.map { itemMap ->
                            LoggedFoodItem(
                                name = itemMap["name"] as? String ?: "Unknown",
                                calories = (itemMap["calories"] as? Number)?.toFloat() ?: 0f
                            )
                        }

                        MealLog(
                            id = doc.id,
                            date = data["date"] as? String ?: "",
                            timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate(),
                            totalCalories = (data["totalCalories"] as? Number)?.toFloat() ?: 0f,
                            items = parsedItems
                        )
                    } catch (e: Exception) {
                        null // Skip malformed docs
                    }
                }
                
                groupedMeals = meals.groupBy { it.date }
                isLoading = false
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
        } else if (groupedMeals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No meals logged yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Explicitly sort dates in descending order (newest first)
                groupedMeals.keys.sortedByDescending { it }.forEach { date ->
                    val mealsOnDate = groupedMeals[date] ?: emptyList()
                    val dailyTotal = mealsOnDate.sumOf { it.totalCalories.toDouble() }
                    
                    item {
                        // Date Header
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDate(date), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Day Total: ${String.format("%.0f", dailyTotal)} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    items(mealsOnDate) { meal ->
                        MealCard(meal)
                    }
                }
            }
        }
    }
}

@Composable
fun MealCard(meal: MealLog) {
    var expanded by remember { mutableStateOf(false) }
    val timeString = if (meal.timestamp != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(meal.timestamp) else "--:--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header Row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Meal at $timeString", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${meal.items.size} items", fontSize = 12.sp, color = Color.Gray)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${String.format("%.0f", meal.totalCalories)} kcal",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 16.sp
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand"
                    )
                }
            }

            // Expanded Details
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                
                meal.items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("• ${item.name}", fontSize = 14.sp)
                        Text("${String.format("%.0f", item.calories)} kcal", fontSize = 14.sp, color = Color.Gray)
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