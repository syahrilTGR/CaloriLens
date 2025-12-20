package com.example.calorielens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color as GraphicsColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home // For Scan tab
import androidx.compose.material.icons.filled.List // For History tab
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.calorielens.ui.theme.CalorieLensTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * @file MainActivity.kt
 * @brief Titik masuk utama (main entry point) dan komponen inti dari aplikasi CalorieLens.
 *
 * Activity ini bertanggung jawab untuk:
 * - Mengelola siklus hidup aplikasi.
 * - Inisialisasi layanan penting seperti Firebase Auth dan YoloDetector.
 * - Mengatur konten UI utama menggunakan Jetpack Compose.
 * - Bertindak sebagai "otak" atau "service layer" yang menyediakan data dan logika bisnis
 *   untuk Composable (UI). Ini termasuk deteksi makanan, perhitungan kalori, sinkronisasi data,
 *   dan manajemen profil pengguna.
 */

/**
 * @brief Merepresentasikan data profil pengguna yang diambil dari Firestore.
 */
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val weight: Float = 0f,
    val height: Float = 0f,
    val age: Int = 0,
    val gender: String = "Male" // "Male" or "Female"
)

class MainActivity : ComponentActivity() {

    // Instance Firebase Authentication untuk mengelola sesi pengguna.
    private lateinit var auth: FirebaseAuth
    
    // Instance YoloDetector, diinisialisasi di onCreate. Nullable untuk keamanan.
    private var yoloDetector: YoloDetector? = null
    
    // Tag untuk logging, mempermudah debugging melalui Logcat.
    private val TAG = "CalorieLens"

    // Database nutrisi dalam memori. Menggunakan `mutableStateMapOf` agar perubahan pada
    // database ini dapat secara otomatis memicu recomposition pada UI yang menggunakannya.
    var nutritionDB = mutableStateMapOf<String, NutritionData>()

    /**
     * @brief Fungsi yang dipanggil saat Activity pertama kali dibuat.
     *        Tempat untuk inisialisasi utama.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Inisialisasi Firebase Authentication.
        auth = Firebase.auth
        // 2. Inisialisasi YoloDetector, meneruskan konteks Activity.
        yoloDetector = YoloDetector(this)
        // 3. Muat data nutrisi awal (dari cache atau default).
        initializeNutritionData()
        
        // 4. Atur konten UI menggunakan Jetpack Compose.
        setContent {
            CalorieLensTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Panggil Composable utama yang mengatur logika navigasi.
                    AppContent(this)
                }
            }
        }
    }

    /**
     * @brief Composable yang mengatur struktur utama aplikasi.
     *
     * Composable ini menentukan apakah akan menampilkan `LoginScreen` atau `Scaffold` utama
     * (termasuk `MainScreen` dan `HistoryScreen`) berdasarkan status login pengguna.
     *
     * @param activity Instance dari `MainActivity`, diteruskan untuk memungkinkan Composable
     *                 lain memanggil fungsi-fungsi di dalam Activity (misal: `detectFood`).
     */
    @Composable
    fun AppContent(activity: MainActivity) {
        // State untuk melacak pengguna yang sedang login.
        var currentUser by remember { mutableStateOf(auth.currentUser) }
        // State untuk mengontrol tab yang aktif di bottom navigation. 0: Scan, 1: History.
        var currentScreen by remember { mutableStateOf(0) }

        if (currentUser == null) {
            // Jika tidak ada pengguna yang login, tampilkan layar login.
            // `onLoginSuccess` adalah callback yang akan memperbarui `currentUser` setelah login berhasil.
            LoginScreen(onLoginSuccess = { currentUser = auth.currentUser })
        } else {
            // Jika pengguna sudah login, tampilkan UI utama dengan navigasi.
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Scan") },
                            label = { Text("Scan") },
                            selected = currentScreen == 0,
                            onClick = { currentScreen = 0 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.List, contentDescription = "History") },
                            label = { Text("History") },
                            selected = currentScreen == 1,
                            onClick = { currentScreen = 1 }
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentScreen) {
                        0 -> MainScreen(activity, onLogout = {
                            auth.signOut()
                            currentUser = null // Set `currentUser` ke null untuk kembali ke LoginScreen.
                        })
                        1 -> HistoryScreen()
                    }
                }
            }
        }
    }

    /**
     * @brief Menginisialisasi `nutritionDB` dari SharedPreferences atau data default.
     *
     * Fungsi ini mencoba memuat data nutrisi dari cache lokal (SharedPreferences).
     * Jika data tidak ada di cache (misalnya, saat aplikasi dijalankan pertama kali),
     * ia akan menggunakan nilai default yang di-hardcode.
     *
     * @implNote Data yang disimpan di `SharedPreferences` bersifat persisten. Data ini **tidak akan terhapus**
     *           jika pengguna hanya melakukan "Hapus Cache" (Clear Cache) dari pengaturan Android.
     *           Data ini hanya akan hilang jika pengguna melakukan "Hapus Data" (Clear Data/Storage)
     *           atau meng-uninstal aplikasi.
     */
    private fun initializeNutritionData() {
        // Data nutrisi default sebagai fallback.
        val defaults = mapOf(
            "ayam_goreng" to NutritionData("Ayam Goreng", 25f, 15f, 5f),
            "bakso" to NutritionData("Bakso", 15f, 10f, 20f),
            // ... (data lainnya)
            "tempe_goreng" to NutritionData("Tempe Goreng", 10f, 8f, 5f),
            "tempe_tepung" to NutritionData("Tempe Tepung", 8f, 10f, 12f)
        )

        val prefs = getSharedPreferences("NutritionCache", Context.MODE_PRIVATE)
        nutritionDB.clear()
        
        // Iterasi melalui semua kunci default untuk mengisi nutritionDB.
        defaults.keys.forEach { key ->
            val name = prefs.getString("$key.name", null)
            if (name != null) {
                // Jika ada di cache, muat dari SharedPreferences.
                val p = prefs.getFloat("$key.p", 0f)
                val f = prefs.getFloat("$key.f", 0f)
                val c = prefs.getFloat("$key.c", 0f)
                nutritionDB[key] = NutritionData(name, p, f, c)
            } else {
                // Jika tidak ada di cache, gunakan data default.
                nutritionDB[key] = defaults[key]!!
            }
        }
    }

    /**
     * @brief Sinkronisasi data nutrisi dari koleksi 'foods' di Firestore ke cache lokal.
     *
     * Fungsi ini mengambil semua data dari Firestore, memperbarui `nutritionDB` dalam memori,
     * dan menyimpannya ke `SharedPreferences` untuk penggunaan offline.
     */
    suspend fun syncWithFirebase() {
        try {
            val db = Firebase.firestore
            val result = db.collection("foods").get().await()
            val prefs = getSharedPreferences("NutritionCache", Context.MODE_PRIVATE)
            
            // Gunakan `edit` untuk melakukan beberapa perubahan pada SharedPreferences secara efisien.
            prefs.edit {
                for (document in result) {
                    val id = document.id
                    val name = document.getString("name") ?: id
                    val p = document.getDouble("proteins")?.toFloat() ?: 0f
                    val f = document.getDouble("fat")?.toFloat() ?: 0f
                    val c = document.getDouble("carbs")?.toFloat() ?: 0f
                    
                    // Perbarui data di memori.
                    nutritionDB[id] = NutritionData(name, p, f, c)
                    
                    // Simpan data ke SharedPreferences.
                    putString("$id.name", name)
                    putFloat("$id.p", p)
                    putFloat("$id.f", f)
                    putFloat("$id.c", c)
                }
            }
            // Tampilkan notifikasi di Main thread setelah selesai.
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Nutrition Data Updated!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * @brief Memproses gambar untuk deteksi makanan dan menghitung kalori.
     *
     * @param bitmap Gambar input yang akan dideteksi.
     * @return Daftar [DetectionResult] yang sudah berisi estimasi kalori.
     */
    fun detectFood(bitmap: Bitmap): List<DetectionResult> {
        // 1. Panggil YoloDetector untuk mendapatkan hasil deteksi mentah.
        val rawResults = yoloDetector?.detect(bitmap) ?: emptyList()
        val finalResults = ArrayList<DetectionResult>()

        // 2. Iterasi melalui hasil mentah untuk menghitung kalori.
        for (res in rawResults) {
            val cal = calculateCalorie(res.className)
            // 3. Buat objek DetectionResult baru dengan kalori yang sudah diestimasi.
            finalResults.add(DetectionResult(res.boundingBox, res.className, res.confidence, cal))
        }
        return finalResults
    }

    /**
     * @brief Menghitung estimasi kalori untuk satu jenis makanan berdasarkan data di `nutritionDB`.
     *        Rumus: (Protein * 4) + (Lemak * 9) + (Karbohidrat * 4).
     *
     * @param foodName Nama kunci makanan (misal: "ayam_goreng").
     * @return Estimasi kalori dalam float, atau 0f jika makanan tidak ditemukan.
     */
    private fun calculateCalorie(foodName: String): Float {
        val data = nutritionDB[foodName] ?: return 0f
        return (data.proteins * 4) + (data.fat * 9) + (data.carbohydrate * 4)
    }

    // --- Fungsi-fungsi terkait Profil Pengguna ---

    /**
     * @brief Mengambil data profil pengguna dari Firestore.
     *
     * @param uid ID unik pengguna Firebase.
     * @return Objek [UserProfile] jika berhasil, atau null jika gagal.
     */
    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val db = Firebase.firestore
            val document = db.collection("users").document(uid).get().await()
            document.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            null
        }
    }

    /**
     * @brief Menghitung kebutuhan kalori harian (TDEE - Total Daily Energy Expenditure).
     *        Menggunakan rumus Harris-Benedict (revisi) untuk BMR.
     *
     * @param profile Profil pengguna yang berisi data berat, tinggi, usia, dan gender.
     * @return Kebutuhan kalori harian dalam integer.
     */
    fun calculateDailyCalories(profile: UserProfile): Int {
        // Hitung BMR (Basal Metabolic Rate)
        val bmr: Float = if (profile.gender == "Male") {
            (10 * profile.weight) + (6.25f * profile.height) - (5 * profile.age) + 5
        } else {
            (10 * profile.weight) + (6.25f * profile.height) - (5 * profile.age) - 161
        }
        // Asumsikan tingkat aktivitas sedentary (BMR * 1.2). Ini bisa dibuat dinamis di masa depan.
        val tdee = bmr * 1.2f
        return tdee.toInt()
    }

    /**
     * @brief Menghitung Indeks Massa Tubuh (BMI).
     *
     * @param profile Profil pengguna.
     * @return Nilai BMI dalam format String "%.1f", atau "N/A" jika data tidak valid.
     */
    fun calculateBMI(profile: UserProfile): String {
        if (profile.weight <= 0 || profile.height <= 0) return "N/A"
        val heightInMeters = profile.height / 100f
        val bmi = profile.weight / (heightInMeters * heightInMeters)
        return String.format("%.1f", bmi)
    }
    
    // --- Pencatatan Makanan (Food Logging) ---

    /**
     * @brief Menyimpan catatan makan ke Firestore.
     *
     * @param uid ID pengguna.
     * @param detections Daftar hasil deteksi yang akan dicatat.
     * @param onSuccess Callback jika berhasil.
     * @param onError Callback jika gagal, dengan pesan error.
     */
    fun logFoodToFirestore(uid: String, detections: List<DetectionResult>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        val logsRef = db.collection("users").document(uid).collection("mealLogs")

        // 1. Siapkan daftar item makanan dari hasil deteksi.
        val foodItems = detections.map { 
            hashMapOf(
                "name" to it.className,
                "calories" to it.estimatedCalories
            )
        }
        
        // 2. Hitung total kalori untuk makanan ini.
        val totalCal = detections.sumOf { it.estimatedCalories.toDouble() }.toFloat()

        // 3. Buat dokumen makan (MealLog).
        val mealData = hashMapOf(
            "date" to today,
            "timestamp" to FieldValue.serverTimestamp(), // Gunakan timestamp server untuk konsistensi.
            "totalCalories" to totalCal,
            "items" to foodItems
        )

        // 4. Tambahkan dokumen ke Firestore.
        logsRef.add(mealData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Unknown error") }
    }
    
    /**
     * @brief Dipanggil saat Activity akan dihancurkan.
     *        Tempat untuk melepaskan sumber daya.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Tutup interpreter TFLite untuk menghindari kebocoran memori.
        yoloDetector?.close()
    }
}