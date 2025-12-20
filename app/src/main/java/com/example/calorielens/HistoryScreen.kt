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

/**
 * @file HistoryScreen.kt
 * @brief Berisi implementasi UI untuk layar Riwayat Makanan (Food History).
 *        Layar ini mengambil dan menampilkan catatan makanan pengguna dari Firestore,
 *        mengelompokkannya berdasarkan tanggal.
 */

/**
 * @brief Composable utama untuk layar riwayat.
 *
 * Fungsi ini menangani:
 * 1.  Pengambilan data riwayat makan (`mealLogs`) dari Firestore untuk pengguna yang sedang login.
 * 2.  Manajemen state untuk `isLoading` dan `groupedMeals`.
 * 3.  Menampilkan indikator loading, pesan kosong, atau daftar riwayat yang dikelompokkan berdasarkan tanggal.
 */
@Composable
fun HistoryScreen() {
    // Inisialisasi Firebase Auth, Firestore, dan konteks lokal.
    val auth = Firebase.auth
    val db = Firebase.firestore
    val context = LocalContext.current
    val userId = auth.currentUser?.uid

    // State untuk menampung riwayat makan yang sudah dikelompokkan berdasarkan tanggal (String "yyyy-MM-dd").
    var groupedMeals by remember { mutableStateOf<Map<String, List<MealLog>>>(emptyMap()) }
    // State untuk menandai status proses pengambilan data.
    var isLoading by remember { mutableStateOf(true) }

    /**
     * `LaunchedEffect` digunakan untuk menjalankan suspend function (coroutine) dengan aman
     * di dalam scope Composable. Efek ini akan dijalankan setiap kali `userId` berubah.
     * Tugasnya adalah mengambil data dari Firestore.
     */
    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                // Mengambil data dari sub-koleksi 'mealLogs' milik pengguna,
                // diurutkan berdasarkan timestamp (terbaru dulu).
                val snapshot = db.collection("users").document(userId).collection("mealLogs")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await() // .await() adalah bagian dari kotlinx-coroutines-play-services

                // Memetakan dokumen Firestore menjadi list objek MealLog.
                val meals = snapshot.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        
                        // Parsing manual list 'items' untuk keamanan tipe data.
                        // Data dari Firestore bisa jadi tidak konsisten, jadi perlu penanganan null.
                        val rawItems = data["items"] as? List<Map<String, Any>> ?: emptyList()
                        val parsedItems = rawItems.map { itemMap ->
                            LoggedFoodItem(
                                name = itemMap["name"] as? String ?: "Unknown",
                                calories = (itemMap["calories"] as? Number)?.toFloat() ?: 0f
                            )
                        }

                        // Membuat objek MealLog dari data dokumen.
                        MealLog(
                            id = doc.id,
                            date = data["date"] as? String ?: "",
                            timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate(),
                            totalCalories = (data["totalCalories"] as? Number)?.toFloat() ?: 0f,
                            items = parsedItems
                        )
                    } catch (e: Exception) {
                        null // Lewati dokumen yang formatnya salah agar aplikasi tidak crash.
                    }
                }
                
                // Mengelompokkan semua catatan makan berdasarkan tanggalnya.
                groupedMeals = meals.groupBy { it.date }
                isLoading = false // Selesai loading
            } catch (e: Exception) {
                // Menangani error jika pengambilan data gagal.
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    }

    // --- UI Layout ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Food History", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            // Tampilkan loading spinner di tengah layar.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (groupedMeals.isEmpty()) {
            // Tampilkan pesan jika tidak ada riwayat.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No meals logged yet.", color = Color.Gray)
            }
        } else {
            // Tampilkan daftar riwayat menggunakan LazyColumn untuk efisiensi.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Urutkan tanggal dari yang terbaru (descending).
                groupedMeals.keys.sortedByDescending { it }.forEach { date ->
                    val mealsOnDate = groupedMeals[date] ?: emptyList()
                    val dailyTotal = mealsOnDate.sumOf { it.totalCalories.toDouble() }
                    
                    // Header untuk setiap tanggal.
                    item {
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

                    // Daftar kartu makan (MealCard) untuk tanggal tersebut.
                    items(mealsOnDate) { meal ->
                        MealCard(meal)
                    }
                }
            }
        }
    }
}

/**
 * @brief Composable untuk menampilkan satu kartu catatan makan (MealLog).
 *
 * Kartu ini bisa di-tap untuk menampilkan atau menyembunyikan detail item makanan di dalamnya.
 *
 * @param meal Objek [MealLog] yang akan ditampilkan.
 */
@Composable
fun MealCard(meal: MealLog) {
    // State untuk mengontrol apakah detail kartu ditampilkan atau tidak.
    var expanded by remember { mutableStateOf(false) }
    // Format timestamp menjadi string jam:menit.
    val timeString = if (meal.timestamp != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(meal.timestamp) else "--:--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize() // Animasi saat ukuran kartu berubah (expand/collapse).
            .clickable { expanded = !expanded }, // Toggle state 'expanded' saat diklik.
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Baris header yang selalu terlihat.
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

            // Bagian detail yang hanya tampil saat 'expanded' bernilai true.
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

/**
 * @brief Fungsi utilitas untuk memformat string tanggal "yyyy-MM-dd".
 *
 * Mengubah tanggal hari ini menjadi "Today" untuk tampilan yang lebih ramah pengguna.
 *
 * @param dateString Tanggal dalam format "yyyy-MM-dd".
 * @return "Today" jika tanggalnya hari ini, atau `dateString` asli jika tidak hari ini.
 */
fun formatDate(dateString: String): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    return if (dateString == today) "Today" else dateString
}