package com.example.calorielens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * @file MainScreen.kt
 * @brief Berisi implementasi UI untuk layar utama aplikasi (tab "Scan").
 *        Ini adalah layar interaktif utama tempat pengguna memilih gambar,
 *        menganalisis makanan, melihat hasil, dan mencatatnya.
 */

/**
 * @brief Composable utama untuk layar "Scan".
 *
 * Fungsi ini menangani:
 * 1.  Manajemen state untuk UI (URI gambar, hasil deteksi, profil pengguna, dll.).
 * 2.  Pengambilan dan penampilan data profil pengguna.
 * 3.  Interaksi dengan pengguna untuk memilih gambar (kamera/galeri) dan cropping.
 * 4.  Memulai proses analisis gambar dan menampilkan hasilnya.
 * 5.  Menggambar kotak pembatas (bounding box) di atas gambar.
 * 6.  Menampilkan daftar makanan yang terdeteksi beserta estimasi kalorinya.
 * 7.  Tombol untuk sinkronisasi data, logout, dan mencatat makanan.
 *
 * @param activity Instance dari `MainActivity` untuk mengakses fungsi-fungsi logika bisnis
 *                 seperti `detectFood`, `syncWithFirebase`, `getUserProfile`, dll.
 * @param onLogout Lambda yang dipanggil saat tombol logout ditekan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity, onLogout: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = Firebase.auth
    val currentUserId = auth.currentUser?.uid
    
    // --- States untuk UI ---
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var results by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var totalCalories by remember { mutableStateOf(0f) }
    // URI sementara untuk menyimpan gambar dari kamera sebelum di-crop.
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    var isSyncing by remember { mutableStateOf(false) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var dailyCaloriesNeeded by remember { mutableStateOf(0) }
    var bmiValue by remember { mutableStateOf("N/A") }

    // Efek untuk mengambil data profil pengguna saat layar pertama kali ditampilkan atau saat user ID berubah.
    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            val profile = activity.getUserProfile(currentUserId)
            userProfile = profile
            if (profile != null) {
                dailyCaloriesNeeded = activity.calculateDailyCalories(profile)
                bmiValue = activity.calculateBMI(profile)
            }
        }
    }

    // --- Activity Result Launchers ---
    // Launcher untuk activity cropping gambar.
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { res ->
        if (res.isSuccessful) {
            // Jika cropping berhasil, perbarui URI gambar yang dipilih dan reset hasil sebelumnya.
            selectedImageUri = res.uriContent
            results = emptyList()
            totalCalories = 0f
        } else {
            Toast.makeText(context, "Crop canceled", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Launcher untuk mengambil gambar dari kamera.
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            // Jika berhasil mengambil gambar, langsung luncurkan activity crop.
            launchCrop(cropLauncher, tempCameraUri!!)
        }
    }
    
    // Launcher untuk memilih gambar dari galeri.
    val galLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchCrop(cropLauncher, it) }
    }

    /**
     * @brief Fungsi yang memulai proses analisis gambar.
     *        Dijalankan di dalam coroutine dengan Dispatchers.IO untuk tugas berat.
     */
    fun processImage() {
        val uri = selectedImageUri ?: return
        Toast.makeText(context, "Thinking...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch(Dispatchers.IO) { // Beralih ke thread I/O
            // 1. Ubah URI menjadi Bitmap.
            val bmp = uriToBitmap(context, uri)
            if(bmp == null) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to load image", Toast.LENGTH_LONG).show() }
                return@launch
            }
            
            // Simpan bitmap untuk ditampilkan di UI (terutama untuk menggambar canvas).
            withContext(Dispatchers.Main) { displayBitmap = bmp }
            
            try {
                // 2. Panggil fungsi deteksi dari MainActivity.
                val detections = activity.detectFood(bmp)
                // 3. Kembali ke Main thread untuk memperbarui UI.
                withContext(Dispatchers.Main) {
                    results = detections
                    totalCalories = detections.sumOf { it.estimatedCalories.toDouble() }.toFloat()
                    if (detections.isEmpty()) Toast.makeText(context, "No food detected", Toast.LENGTH_LONG).show()
                    else Toast.makeText(context, "Found ${detections.size} items!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CalorieLens", "Error", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // --- UI Layout ---
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CalorieLens AI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row {
                IconButton(onClick = {
                    isSyncing = true
                    coroutineScope.launch {
                        activity.syncWithFirebase()
                        isSyncing = false
                    }
                }) {
                    if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Icon(Icons.Default.Refresh, contentDescription = "Sync")
                }
                IconButton(onClick = onLogout) { Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout") }
            }
        }
        
        // --- Kartu Profil Pengguna ---
        userProfile?.let { profile ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Welcome, ${profile.name}!", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("BMI: $bmiValue (${profile.weight}kg / ${profile.height}cm)")
                    Text("Daily Calories Needed: $dailyCaloriesNeeded kcal")
                }
            }
        } ?: CircularProgressIndicator(modifier = Modifier.padding(8.dp)) // Tampilkan loading jika profil belum termuat.
        
        // --- Area Gambar dan Bounding Box ---
        Box(
            Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray.copy(alpha = 0.1f)).border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                key(selectedImageUri) { // `key` untuk memastikan recomposition saat URI berubah.
                    Image(painter = rememberAsyncImagePainter(selectedImageUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    
                    val currentDisplayBitmap = displayBitmap
                    if (results.isNotEmpty() && currentDisplayBitmap != null) {
                        // Canvas untuk menggambar bounding box di atas gambar.
                        Canvas(modifier = Modifier.matchParentSize()) {
                            // Kalkulasi untuk menyesuaikan koordinat dari bitmap ke ukuran Composable.
                            val bitmapW = currentDisplayBitmap.width.toFloat()
                            val bitmapH = currentDisplayBitmap.height.toFloat()
                            val scale = min(size.width / bitmapW, size.height / bitmapH) // Skala gambar (Fit center)
                            val offsetX = (size.width - bitmapW * scale) / 2 // Offset horizontal
                            val offsetY = (size.height - bitmapH * scale) / 2 // Offset vertikal

                            results.forEach { res ->
                                drawRect(
                                    color = Color.Green, 
                                    topLeft = Offset(res.boundingBox.left * scale + offsetX, res.boundingBox.top * scale + offsetY),
                                    size = Size(res.boundingBox.width() * scale, res.boundingBox.height() * scale),
                                    style = Stroke(width = 6f) // Gambar garis kotak
                                )
                            }
                        }
                    }
                }
            } else {
                // Tampilan placeholder jika tidak ada gambar yang dipilih.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
                    Text("Select Image", color = Color.Gray)
                }
            }
        }
        
        // --- Tombol Aksi (Kamera & Galeri) ---
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            Button(onClick = { 
                val uri = getTempUri(context)
                tempCameraUri = uri
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    camLauncher.launch(uri)
                } else {
                    Toast.makeText(context, "Permission needed", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Camera") }
            Button(onClick = { galLauncher.launch("image/*") }) { Text("Gallery") }
        }
        
        Button(onClick = { processImage() }, enabled = selectedImageUri != null, modifier = Modifier.fillMaxWidth()) {
            Text("Analyze Food")
        }
        
        // --- Area Hasil Deteksi ---
        if (results.isNotEmpty()) {
            Text("Total: ${String.format("%.0f", totalCalories)} kcal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column {
                results.forEach { res ->
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                            Column {
                                Text(res.className, fontWeight = FontWeight.Bold)
                                Text("Conf: ${String.format("%.1f", res.confidence * 100)}%", fontSize = 12.sp)
                            }
                            Text("${String.format("%.0f", res.estimatedCalories)} kcal")
                        }
                    }
                }
            }
            // Tombol untuk mencatat makanan ke riwayat.
            Button(
                onClick = {
                    if (currentUserId != null) {
                        activity.logFoodToFirestore(
                            currentUserId,
                            results,
                            onSuccess = { Toast.makeText(context, "Food Logged!", Toast.LENGTH_SHORT).show() },
                            onError = { errorMsg -> Toast.makeText(context, "Failed to log food: $errorMsg", Toast.LENGTH_LONG).show() }
                        )
                    } else {
                        Toast.makeText(context, "Login first to log food", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = currentUserId != null && results.isNotEmpty()
            ) {
                Text("I Ate This!")
            }
        }
    }
}