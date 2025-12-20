package com.example.calorielens

import android.graphics.RectF

/**
 * @file DataModels.kt
 * @brief Berisi definisi semua kelas data (data class) yang digunakan di seluruh aplikasi.
 *        File ini berfungsi sebagai satu sumber kebenaran (single source of truth) untuk struktur data aplikasi.
 */

/**
 * @brief Merepresentasikan data nutrisi untuk satu jenis makanan.
 *
 * @param foodName Nama makanan (misalnya, "Ayam Goreng").
 * @param proteins Jumlah protein dalam gram.
 * @param fat Jumlah lemak dalam gram.
 * @param carbohydrate Jumlah karbohidrat dalam gram.
 * @param calories Jumlah total kalori, biasanya dihitung dari makronutrien.
 */
data class NutritionData(
    val foodName: String = "",
    val proteins: Float = 0f,
    val fat: Float = 0f,
    val carbohydrate: Float = 0f,
    val calories: Float = 0f
)

/**
 * @brief Merepresentasikan hasil akhir dari satu objek yang terdeteksi, siap untuk ditampilkan di UI.
 *
 * @param boundingBox Kotak pembatas (koordinat) di sekitar objek yang terdeteksi pada gambar.
 * @param className Nama kelas dari objek yang terdeteksi (misalnya, "bakso").
 * @param confidence Skor kepercayaan (0.0 hingga 1.0) dari model deteksi.
 * @param estimatedCalories Estimasi kalori untuk objek yang terdeteksi ini, dihitung berdasarkan `className`.
 */
data class DetectionResult(
    val boundingBox: RectF,
    val className: String,
    val confidence: Float,
    val estimatedCalories: Float
)

/**
 * @brief Merepresentasikan hasil deteksi mentah langsung dari output model TFLite.
 *        Ini adalah struktur data internal sebelum diolah menjadi [DetectionResult].
 *
 * @param classId ID numerik dari kelas yang terdeteksi.
 * @param confidence Skor kepercayaan (0.0 hingga 1.0) dari deteksi.
 * @param rect Kotak pembatas mentah (koordinat) dari deteksi.
 */
data class RawDetection(
    val classId: Int,
    val confidence: Float,
    val rect: RectF
)

// --- Model Data untuk Riwayat (History) ---

/**
 * @brief Merepresentasikan satu item makanan dalam sebuah log makan.
 *
 * @param name Nama makanan yang dicatat.
 * @param calories Jumlah kalori untuk item makanan tersebut.
 */
data class LoggedFoodItem(
    val name: String = "",
    val calories: Float = 0f
)

/**
 * @brief Merepresentasikan satu catatan makan lengkap (satu kali makan) yang disimpan di Firestore.
 *
 * @param id ID unik dari dokumen log di Firestore.
 * @param date Tanggal makan dalam format String "yyyy-MM-dd".
 * @param timestamp Waktu pasti saat makan dicatat, digunakan untuk pengurutan dan tampilan jam.
 * @param totalCalories Jumlah total kalori dari semua item dalam sekali makan.
 * @param items Daftar [LoggedFoodItem] yang membentuk makanan ini.
 */
data class MealLog(
    val id: String = "",
    val date: String = "",
    val timestamp: java.util.Date? = null,
    val totalCalories: Float = 0f,
    val items: List<LoggedFoodItem> = emptyList()
)