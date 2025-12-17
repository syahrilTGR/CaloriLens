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

// --- Data Models (Moved to DataModels.kt) ---
// --- UI Components (Moved to LoginScreen.kt, MainScreen.kt) ---
// --- Helper Functions (Moved to Utils.kt) ---

// --- User Profile Data Class ---
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val weight: Float = 0f,
    val height: Float = 0f,
    val age: Int = 0,
    val gender: String = "Male" // "Male" or "Female"
)

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var yoloDetector: YoloDetector? = null
    
    private val TAG = "CalorieLens"

    // Mutable State for Nutrition DB
    var nutritionDB = mutableStateMapOf<String, NutritionData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = Firebase.auth
        yoloDetector = YoloDetector(this)
        initializeNutritionData()
        
        setContent {
            CalorieLensTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppContent(this)
                }
            }
        }
    }

    @Composable
    fun AppContent(activity: MainActivity) {
        var currentUser by remember { mutableStateOf(auth.currentUser) }
        var currentScreen by remember { mutableStateOf(0) } // 0: Scan, 1: History

        if (currentUser == null) {
            LoginScreen(onLoginSuccess = { currentUser = auth.currentUser })
        } else {
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
                            currentUser = null
                        })
                        1 -> HistoryScreen()
                    }
                }
            }
        }
    }

    private fun initializeNutritionData() {
        val defaults = mapOf(
            "ayam_goreng" to NutritionData("Ayam Goreng", 25f, 15f, 5f),
            "bakso" to NutritionData("Bakso", 15f, 10f, 20f),
            "burger" to NutritionData("Burger", 18f, 15f, 35f),
            "dadar_jagung" to NutritionData("Dadar Jagung", 4f, 8f, 15f),
            "french_fries" to NutritionData("French Fries", 3f, 15f, 40f),
            "gado_gado" to NutritionData("Gado-Gado", 8f, 10f, 20f),
            "geprek_ijo" to NutritionData("Ayam Geprek Ijo", 25f, 18f, 5f),
            "geprek_ori" to NutritionData("Ayam Geprek Ori", 25f, 18f, 8f),
            "ikan_goreng" to NutritionData("Ikan Goreng", 20f, 12f, 0f),
            "kerupuk" to NutritionData("Kerupuk", 1f, 5f, 10f),
            "lele_goreng" to NutritionData("Lele Goreng", 18f, 12f, 5f),
            "mendol" to NutritionData("Mendol", 5f, 7f, 10f),
            "menjes" to NutritionData("Menjes", 5f, 8f, 10f),
            "mie_ayam" to NutritionData("Mie Ayam", 12f, 10f, 45f),
            "mie_goreng" to NutritionData("Mie Goreng", 8f, 12f, 50f),
            "mieso" to NutritionData("Mieso", 15f, 12f, 40f),
            "nasgor_jawa" to NutritionData("Nasi Goreng Jawa", 10f, 12f, 50f),
            "nasgor_merah" to NutritionData("Nasi Goreng Merah", 10f, 14f, 50f),
            "nasi" to NutritionData("Nasi Putih", 3f, 0.5f, 40f),
            "nasi_geprekori" to NutritionData("Nasi Geprek Ori", 28f, 18f, 45f),
            "nasi_padang" to NutritionData("Nasi Padang", 25f, 20f, 60f),
            "pangsit_goreng" to NutritionData("Pangsit Goreng", 3f, 5f, 10f),
            "pecel" to NutritionData("Pecel", 8f, 10f, 30f),
            "peyek" to NutritionData("Peyek", 5f, 12f, 10f),
            "pizza" to NutritionData("Pizza", 12f, 10f, 30f),
            "rawon" to NutritionData("Rawon", 20f, 15f, 5f),
            "rendang" to NutritionData("Rendang", 25f, 20f, 8f),
            "sate" to NutritionData("Sate", 20f, 10f, 5f),
            "siomay" to NutritionData("Siomay", 8f, 5f, 15f),
            "soto_ayam" to NutritionData("Soto Ayam", 15f, 8f, 10f),
            "tahu_goreng" to NutritionData("Tahu Goreng", 8f, 6f, 3f),
            "telur_ceplok" to NutritionData("Telur Ceplok", 6f, 5f, 1f),
            "telur_dadar" to NutritionData("Telur Dadar", 6f, 7f, 1f),
            "telur_rebus" to NutritionData("Telur Rebus", 6f, 5f, 1f),
            "tempe_goreng" to NutritionData("Tempe Goreng", 10f, 8f, 5f),
            "tempe_tepung" to NutritionData("Tempe Tepung", 8f, 10f, 12f)
        )

        val prefs = getSharedPreferences("NutritionCache", Context.MODE_PRIVATE)
        nutritionDB.clear()
        
        defaults.keys.forEach { key ->
            val name = prefs.getString("$key.name", null)
            if (name != null) {
                val p = prefs.getFloat("$key.p", 0f)
                val f = prefs.getFloat("$key.f", 0f)
                val c = prefs.getFloat("$key.c", 0f)
                nutritionDB[key] = NutritionData(name, p, f, c)
            } else {
                nutritionDB[key] = defaults[key]!!
            }
        }
    }

    suspend fun syncWithFirebase() {
        try {
            val db = Firebase.firestore
            val result = db.collection("foods").get().await()
            val prefs = getSharedPreferences("NutritionCache", Context.MODE_PRIVATE)
            
            prefs.edit {
                for (document in result) {
                    val id = document.id
                    val name = document.getString("name") ?: id
                    val p = document.getDouble("proteins")?.toFloat() ?: 0f
                    val f = document.getDouble("fat")?.toFloat() ?: 0f
                    val c = document.getDouble("carbs")?.toFloat() ?: 0f
                    
                    nutritionDB[id] = NutritionData(name, p, f, c)
                    
                    putString("$id.name", name)
                    putFloat("$id.p", p)
                    putFloat("$id.f", f)
                    putFloat("$id.c", c)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Nutrition Data Updated!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun detectFood(bitmap: Bitmap): List<DetectionResult> {
        val rawResults = yoloDetector?.detect(bitmap) ?: emptyList()
        val finalResults = ArrayList<DetectionResult>()

        for (res in rawResults) {
            val cal = calculateCalorie(res.className)
            // Update estimatedCalories in the DetectionResult
            finalResults.add(DetectionResult(res.boundingBox, res.className, res.confidence, cal))
        }
        return finalResults
    }

    private fun calculateCalorie(foodName: String): Float {
        val data = nutritionDB[foodName]
        if (data == null) {
            return 0f
        }
        return (data.proteins * 4) + (data.fat * 9) + (data.carbohydrate * 4)
    }

    // --- User Profile Functions ---
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

    fun calculateDailyCalories(profile: UserProfile): Int {
        val bmr: Float = if (profile.gender == "Male") {
            (10 * profile.weight) + (6.25f * profile.height) - (5 * profile.age) + 5
        } else {
            (10 * profile.weight) + (6.25f * profile.height) - (5 * profile.age) - 161
        }

        // Assume sedentary (BMR * 1.2) for now. Can be made dynamic later.
        val tdee = bmr * 1.2f
        return tdee.toInt()
    }

    fun calculateBMI(profile: UserProfile): String {
        if (profile.weight <= 0 || profile.height <= 0) return "N/A"
        val heightInMeters = profile.height / 100f
        val bmi = profile.weight / (heightInMeters * heightInMeters)
        return String.format("%.1f", bmi)
    }
    
    // --- Food Logging ---
    fun logFoodToFirestore(uid: String, detections: List<DetectionResult>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        val logsRef = db.collection("users").document(uid).collection("mealLogs")

        // 1. Prepare the list of items
        val foodItems = detections.map { 
            hashMapOf(
                "name" to it.className,
                "calories" to it.estimatedCalories
            )
        }
        
        // 2. Calculate total for this meal
        val totalCal = detections.sumOf { it.estimatedCalories.toDouble() }.toFloat()

        // 3. Create Meal Document
        val mealData = hashMapOf(
            "date" to today,
            "timestamp" to FieldValue.serverTimestamp(),
            "totalCalories" to totalCal,
            "items" to foodItems
        )

        logsRef.add(mealData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Unknown error") }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        yoloDetector?.close()
    }
}