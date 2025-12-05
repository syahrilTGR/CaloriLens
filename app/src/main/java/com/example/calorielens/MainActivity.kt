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
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.calorielens.ui.theme.CalorieLensTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

// --- Data Models ---
data class NutritionData(
    val foodName: String,
    val proteins: Float,
    val fat: Float,
    val carbohydrate: Float,
    val calories: Float
)

data class DetectionResult(
    val boundingBox: RectF,
    val className: String,
    val confidence: Float,
    val estimatedCalories: Float
)

// Internal helper for NMS
data class RawDetection(
    val classId: Int,
    val confidence: Float,
    val rect: RectF
)

class MainActivity : ComponentActivity() {

    private var tfliteYolo: Interpreter? = null
    
    private val TAG = "CalorieLens"

    // --- Config ---
    private val MODEL_YOLO = "yolo_model.tflite"
    private val LABEL_YOLO = "labels_yolo.txt"
    private val SIZE_YOLO = 640
    
    private var labelsYolo: List<String> = emptyList()

    // Nutrition DB (Updated for 36 classes)
    private val nutritionDB = mapOf(
        "ayam_goreng" to NutritionData("Ayam Goreng", 25f, 15f, 5f, 0f),
        "bakso" to NutritionData("Bakso", 15f, 10f, 20f, 0f),
        "burger" to NutritionData("Burger", 18f, 15f, 35f, 0f),
        "dadar_jagung" to NutritionData("Dadar Jagung", 4f, 8f, 15f, 0f),
        "french_fries" to NutritionData("French Fries", 3f, 15f, 40f, 0f),
        "gado_gado" to NutritionData("Gado-Gado", 8f, 10f, 20f, 0f),
        "geprek_ijo" to NutritionData("Ayam Geprek Ijo", 25f, 18f, 5f, 0f),
        "geprek_ori" to NutritionData("Ayam Geprek Ori", 25f, 18f, 8f, 0f),
        "ikan_goreng" to NutritionData("Ikan Goreng", 20f, 12f, 0f, 0f),
        "kerupuk" to NutritionData("Kerupuk", 1f, 5f, 10f, 0f),
        "lele_goreng" to NutritionData("Lele Goreng", 18f, 12f, 5f, 0f),
        "mendol" to NutritionData("Mendol", 5f, 7f, 10f, 0f),
        "menjes" to NutritionData("Menjes", 5f, 8f, 10f, 0f),
        "mie_ayam" to NutritionData("Mie Ayam", 12f, 10f, 45f, 0f),
        "mie_goreng" to NutritionData("Mie Goreng", 8f, 12f, 50f, 0f),
        "mieso" to NutritionData("Mieso", 15f, 12f, 40f, 0f),
        "nasgor_jawa" to NutritionData("Nasi Goreng Jawa", 10f, 12f, 50f, 0f),
        "nasgor_merah" to NutritionData("Nasi Goreng Merah", 10f, 14f, 50f, 0f),
        "nasi" to NutritionData("Nasi Putih", 3f, 0.5f, 40f, 0f),
        "nasi_geprekori" to NutritionData("Nasi Geprek Ori", 28f, 18f, 45f, 0f),
        "nasi_padang" to NutritionData("Nasi Padang", 25f, 20f, 60f, 0f),
        "pangsit_goreng" to NutritionData("Pangsit Goreng", 3f, 5f, 10f, 0f),
        "pecel" to NutritionData("Pecel", 8f, 10f, 30f, 0f),
        "peyek" to NutritionData("Peyek", 5f, 12f, 10f, 0f),
        "pizza" to NutritionData("Pizza", 12f, 10f, 30f, 0f),
        "rawon" to NutritionData("Rawon", 20f, 15f, 5f, 0f),
        "rendang" to NutritionData("Rendang", 25f, 20f, 8f, 0f),
        "sate" to NutritionData("Sate", 20f, 10f, 5f, 0f),
        "siomay" to NutritionData("Siomay", 8f, 5f, 15f, 0f),
        "soto_ayam" to NutritionData("Soto Ayam", 15f, 8f, 10f, 0f),
        "tahu_goreng" to NutritionData("Tahu Goreng", 8f, 6f, 3f, 0f),
        "telur_ceplok" to NutritionData("Telur Ceplok", 6f, 5f, 1f, 0f),
        "telur_dadar" to NutritionData("Telur Dadar", 6f, 7f, 1f, 0f),
        "telur_rebus" to NutritionData("Telur Rebus", 6f, 5f, 1f, 0f),
        "tempe_goreng" to NutritionData("Tempe Goreng", 10f, 8f, 5f, 0f),
        "tempe_tepung" to NutritionData("Tempe Tepung", 8f, 10f, 12f, 0f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModels()
        setContent {
            CalorieLensTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(this)
                }
            }
        }
    }

    private fun loadModels() {
        try {
            tfliteYolo = Interpreter(loadModelFile(MODEL_YOLO))
            labelsYolo = try { assets.open(LABEL_YOLO).bufferedReader().useLines { it.toList() } } catch(e:Exception) { emptyList() }
            Log.d(TAG, "Models loaded. YOLO labels: ${labelsYolo.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading models", e)
            Toast.makeText(this, "Error loading models: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // --- PURE YOLO PIPELINE ---
    fun runYoloPipeline(bitmap: Bitmap): List<DetectionResult> {
        val detections = runYoloInference(bitmap)
        val results = ArrayList<DetectionResult>()

        for (det in detections) {
            val labelName = if (det.classId < labelsYolo.size) labelsYolo[det.classId] else "unknown"
            val cal = calculateCalorie(labelName)
            results.add(DetectionResult(det.rect, labelName, det.confidence, cal))
        }
        
        return results
    }

    // --- YOLO INFERENCE ---
    private fun runYoloInference(bitmap: Bitmap): List<RawDetection> {
        val interpreter = tfliteYolo ?: return emptyList()
        
        // 1. Letterbox Preprocess
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val scale = min(SIZE_YOLO.toFloat() / originalWidth, SIZE_YOLO.toFloat() / originalHeight)
        val newWidth = (originalWidth * scale).toInt()
        val newHeight = (originalHeight * scale).toInt()
        
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val inputBitmap = Bitmap.createBitmap(SIZE_YOLO, SIZE_YOLO, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        canvas.drawColor(GraphicsColor.GRAY)
        
        val dx = (SIZE_YOLO - newWidth) / 2f
        val dy = (SIZE_YOLO - newHeight) / 2f
        canvas.drawBitmap(resizedBitmap, dx, dy, null)

        // Prepare Input
        val inputBuffer = ByteBuffer.allocateDirect(1 * SIZE_YOLO * SIZE_YOLO * 3 * 4).order(ByteOrder.nativeOrder())
        val intValues = IntArray(SIZE_YOLO * SIZE_YOLO)
        inputBitmap.getPixels(intValues, 0, SIZE_YOLO, 0, 0, SIZE_YOLO, SIZE_YOLO)
        
        for (v in intValues) {
            // Normalized [0,1]
            inputBuffer.putFloat(((v shr 16 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((v shr 8 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((v and 0xFF) / 255.0f))
        }

        // Run
        val outputTensor = interpreter.getOutputTensor(0)
        val shape = outputTensor.shape()
        val rows = shape[1] // 28 + 4 = 32, or similar. YOLOv8 TFLite output: [1, 4 + num_classes, 8400]
        val cols = shape[2] // 8400
        
        val outputBuffer = Array(1) { Array(rows) { FloatArray(cols) } }
        interpreter.run(inputBuffer, outputBuffer)

        val rawDetections = ArrayList<RawDetection>()
        val rawOutput = outputBuffer[0]

        // Iterate anchors
        for (i in 0 until cols) {
            var maxScore = 0f
            var classId = -1
            
            // Check class scores (starts at index 4)
            // rows is typically 4 (box) + 36 (classes) = 40
            for (c in 4 until rows) {
                val score = rawOutput[c][i]
                if (score > maxScore) {
                    maxScore = score
                    classId = c - 4 // class ID is index relative to the start of classes
                }
            }

            if (maxScore > 0.25f) { // Reasonable threshold
                val xc = rawOutput[0][i]
                val yc = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                // Un-pad coordinates
                val xPos = xc - dx
                val yPos = yc - dy
                
                // Scale back to original
                val originalX = xPos / scale
                val originalY = yPos / scale
                val originalW = w / scale
                val originalH = h / scale

                val left = originalX - originalW / 2
                val top = originalY - originalH / 2
                val right = originalX + originalW / 2
                val bottom = originalY + originalH / 2
                
                rawDetections.add(RawDetection(classId, maxScore, RectF(left, top, right, bottom)))
            }
        }
        
        return applyNMS(rawDetections)
    }

    private fun applyNMS(detections: List<RawDetection>): List<RawDetection> {
        val finalDetections = ArrayList<RawDetection>()
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            finalDetections.add(best)
            
            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                // If high overlap, suppress
                if (iou(best.rect, other.rect) > 0.45f) {
                    iterator.remove()
                }
            }
        }
        return finalDetections
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        
        if (interRight < interLeft || interBottom < interTop) return 0f
        
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val unionArea = (a.width() * a.height()) + (b.width() * b.height()) - interArea
        return interArea / unionArea
    }

    private fun calculateCalorie(foodName: String): Float {
        val data = nutritionDB[foodName]
        if (data == null) {
            Log.w(TAG, "No nutrition data for $foodName")
            return 0f
        }
        return (data.proteins * 4) + (data.fat * 9) + (data.carbohydrate * 4)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tfliteYolo?.close()
    }
}

// --- UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var results by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var totalCalories by remember { mutableStateOf(0f) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { res ->
        if (res.isSuccessful) {
            selectedImageUri = res.uriContent
            results = emptyList()
            totalCalories = 0f
        } else {
            Toast.makeText(context, "Crop canceled", Toast.LENGTH_SHORT).show()
        }
    }
    
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            launchCrop(cropLauncher, tempCameraUri!!)
        }
    }
    
    val galLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchCrop(cropLauncher, it) }
    }

    fun processImage() {
        val uri = selectedImageUri ?: return
        Toast.makeText(context, "Thinking...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch(Dispatchers.IO) {
            val bmp = uriToBitmap(context, uri)
            if(bmp == null) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to load image", Toast.LENGTH_LONG).show() }
                return@launch
            }
            
            withContext(Dispatchers.Main) { displayBitmap = bmp }
            
            try {
                // Use the PURE YOLO pipeline
                val detections = activity.runYoloPipeline(bmp)
                
                withContext(Dispatchers.Main) {
                    results = detections
                    totalCalories = detections.sumOf { it.estimatedCalories.toDouble() }.toFloat()
                    if (detections.isEmpty()) Toast.makeText(context, "Nothing found. Try closer photo.", Toast.LENGTH_LONG).show()
                    else Toast.makeText(context, "Found ${detections.size} items!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CalorieLens", "Error", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CalorieLens AI", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Box(
            Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray.copy(alpha = 0.1f)).border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                key(selectedImageUri) {
                    Image(
                        painter = rememberAsyncImagePainter(selectedImageUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    val currentDisplayBitmap = displayBitmap
                    if (results.isNotEmpty() && currentDisplayBitmap != null) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val bitmapW = currentDisplayBitmap.width.toFloat()
                            val bitmapH = currentDisplayBitmap.height.toFloat()
                            val scale = min(size.width / bitmapW, size.height / bitmapH)
                            val offsetX = (size.width - bitmapW * scale) / 2
                            val offsetY = (size.height - bitmapH * scale) / 2

                            results.forEach { res ->
                                val r = res.boundingBox
                                drawRect(
                                    Color.Green, // Use Green for better visibility
                                    topLeft = Offset(r.left * scale + offsetX, r.top * scale + offsetY),
                                    size = Size(r.width() * scale, r.height() * scale),
                                    style = Stroke(width = 6f)
                                )
                            }
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
                    Text("Select Image", color = Color.Gray)
                }
            }
        }
        
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            Button(onClick = { 
                val uri = getTempUri(context)
                tempCameraUri = uri
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    camLauncher.launch(uri)
                } else {
                    Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Camera") }
            Button(onClick = { galLauncher.launch("image/*") }) { Text("Gallery") }
        }
        
        Button(onClick = { processImage() }, enabled = selectedImageUri != null, modifier = Modifier.fillMaxWidth()) {
            Text("Analyze Food")
        }
        
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
        }
    }
}

// --- Global Helpers ---
fun getTempUri(context: Context): Uri {
    val f = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
}

fun launchCrop(launcher: androidx.activity.result.ActivityResultLauncher<CropImageContractOptions>, uri: Uri) {
    launcher.launch(CropImageContractOptions(uri, CropImageOptions().apply {
        imageSourceIncludeGallery = false
        imageSourceIncludeCamera = false
        activityTitle = "Crop Image"
    }))
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                d.isMutableRequired = true
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        rotateBitmapIfRequired(context, originalBitmap, uri).copy(Bitmap.Config.ARGB_8888, true)
    } catch (e: Exception) { 
        Log.e("CalorieLens", "Error uriToBitmap", e)
        null
    }
}

private fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
    val input = context.contentResolver.openInputStream(uri) ?: return bitmap
    val exif = ExifInterface(input)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
        else -> bitmap
    }
}

private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}