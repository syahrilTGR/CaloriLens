package com.example.calorielens

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import android.graphics.Color as GraphicsColor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * @class YoloDetector
 * @brief Kelas ini bertanggung jawab untuk menangani semua logika terkait deteksi objek
 *        menggunakan model YOLOv5 (You Only Look Once) yang dijalankan dengan TensorFlow Lite.
 *
 * Kelas ini mencakup fungsionalitas untuk memuat model dan label dari aset,
 * melakukan pra-pemrosesan pada gambar input, menjalankan inferensi model,
 * pasca-pemrosesan hasil (termasuk Non-Maximum Suppression), dan mengembalikan
 * daftar objek yang terdeteksi.
 *
 * @param context Konteks aplikasi, diperlukan untuk mengakses folder 'assets' tempat
 *                model (.tflite) dan file label (.txt) disimpan.
 */
class YoloDetector(private val context: Context) {

    // Interpreter TensorFlow Lite untuk model YOLO. Bersifat nullable karena inisialisasi
    // bisa gagal. Ini adalah "mesin" yang menjalankan inferensi model.
    private var tfliteYolo: Interpreter? = null

    // Tag untuk logging, digunakan untuk memfilter pesan di Logcat.
    private val TAG = "YoloDetector"

    // Nama file model YOLOv5 TFLite yang disimpan di folder assets.
    private val MODEL_YOLO = "yolo_model.tflite"

    // Nama file teks yang berisi daftar label/kelas, disimpan di folder assets.
    private val LABEL_YOLO = "labels_yolo.txt"

    // Ukuran input yang diharapkan oleh model YOLO. Gambar akan diubah ukurannya ke 640x640.
    private val SIZE_YOLO = 640
    
    // List untuk menyimpan nama-nama kelas/label yang dibaca dari file LABEL_YOLO.
    private var labelsYolo: List<String> = emptyList()

    /**
     * @brief Blok inisialisasi yang akan dieksekusi saat sebuah instance dari YoloDetector dibuat.
     *        Tugas utamanya adalah memanggil fungsi `loadModel` untuk mempersiapkan model dan label.
     */
    init {
        loadModel()
    }

    /**
     * @brief Fungsi ini bertanggung jawab untuk memuat model pendeteksi objek YOLO (You Only Look Once)
     *        dan daftar label kelas yang terkait dari aset aplikasi.
     *
     * Fungsi ini diinisialisasi saat instance `YoloDetector` dibuat (melalui blok `init`).
     * Proses pemuatan mencakup inisialisasi interpreter TensorFlow Lite dan pembacaan file teks label.
     */
    private fun loadModel() {
        try {
            // 1. Inisialisasi Interpreter TensorFlow Lite untuk model YOLO.
            //    'tfliteYolo' adalah instance dari Interpreter, yang merupakan "mesin" untuk menjalankan
            //    inferensi model TensorFlow Lite.
            //    'loadModelFile(MODEL_YOLO)' adalah fungsi utilitas yang membaca file model '.tflite'
            //    dari folder 'assets' aplikasi dan mengembalikannya dalam format MappedByteBuffer,
            //    yang diperlukan oleh konstruktor Interpreter.
            //    'MODEL_YOLO' adalah konstanta string yang menyimpan nama file model YOLO (misalnya, "yolo_model.tflite").
            tfliteYolo = Interpreter(loadModelFile(MODEL_YOLO))

            // 2. Memuat label kelas yang digunakan oleh model YOLO.
            //    Blok 'try-catch' internal digunakan untuk menangani potensi kesalahan saat membaca file label.
            labelsYolo = try {
                // 'context.assets.open(LABEL_YOLO)' membuka file teks label dari folder 'assets' aplikasi.
                // 'LABEL_YOLO' adalah konstanta string yang menyimpan nama file label (misalnya, "labels_yolo.txt").
                // '.bufferedReader()' membuat BufferedReader untuk membaca file secara efisien baris per baris.
                // '.useLines { it.toList() }' adalah cara idiomatis Kotlin untuk membaca semua baris dari
                // reader dan mengumpulkannya ke dalam List<String>. Fungsi 'useLines' juga memastikan
                // bahwa BufferedReader ditutup secara otomatis setelah digunakan, mencegah kebocoran sumber daya.
                context.assets.open(LABEL_YOLO).bufferedReader().useLines { it.toList() }
            } catch (e: Exception) {
                // Jika terjadi kesalahan saat membaca file label (misalnya, file tidak ditemukan atau korup),
                // 'labelsYolo' akan diinisialisasi sebagai daftar kosong.
                Log.e(TAG, "Error loading labels for YOLO model", e)
                emptyList()
            }
            // Mencatat (logging) bahwa model telah berhasil dimuat, beserta jumlah label yang ditemukan.
            // Ini membantu dalam debugging dan verifikasi saat aplikasi berjalan.
            Log.d(TAG, "Model YOLO berhasil dimuat. Jumlah label: ${labelsYolo.size}")
        } catch (e: Exception) {
            // Menangkap setiap Exception yang mungkin terjadi selama proses pemuatan model atau label.
            // Contohnya bisa jadi file model tidak ditemukan, format tidak valid, atau masalah memori.
            // Pesan error akan dicatat ke Logcat dengan level ERROR, termasuk stack trace untuk detail debugging.
            Log.e(TAG, "Terjadi kesalahan saat memuat model YOLO atau label", e)
        }
    }

    /**
     * @brief Memuat file model TFLite dari folder 'assets' sebagai MappedByteBuffer.
     *
     * TensorFlow Lite Interpreter memerlukan model dalam bentuk MappedByteBuffer untuk efisiensi.
     * Fungsi ini menangani pembukaan file descriptor, mendapatkan input stream, dan memetakannya
     * ke dalam memori.
     *
     * @param modelPath Nama file model di dalam folder 'assets'.
     * @return MappedByteBuffer yang berisi data model.
     * @throws IOException jika file tidak dapat ditemukan atau dibaca.
     */
    @Throws(IOException::class)
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * @brief Fungsi utama untuk melakukan deteksi objek pada sebuah gambar (Bitmap).
     *
     * @param bitmap Gambar input yang akan dianalisis.
     * @return List dari [DetectionResult], di mana setiap elemen merepresentasikan
     *         satu objek yang terdeteksi, lengkap dengan kotak pembatas (bounding box) dan nama label.
     */
    fun detect(bitmap: Bitmap): List<DetectionResult> {
        // 1. Jalankan inferensi untuk mendapatkan hasil deteksi mentah (raw).
        val rawDetections = runInference(bitmap)
        val results = ArrayList<DetectionResult>()

        // 2. Iterasi melalui setiap deteksi mentah untuk memproses dan memformatnya.
        for (det in rawDetections) {
            // Dapatkan nama label dari ID kelas. Lakukan pengecekan untuk menghindari index out of bounds.
            val labelName = if (det.classId < labelsYolo.size) labelsYolo[det.classId] else "unknown"
            
            // Konversi RawDetection menjadi DetectionResult.
            // Informasi kalori diatur sebagai 0f sebagai placeholder; logika bisnis di luar kelas ini
            // (misalnya, di ViewModel) akan bertanggung jawab untuk menghitung dan mengisinya.
            results.add(DetectionResult(det.rect, labelName, det.confidence, 0f))
        }
        return results
    }

    /**
     * @brief Melakukan pra-pemrosesan gambar, menjalankan inferensi model, dan mengurai output mentah.
     *
     * Tahapan dalam fungsi ini:
     * 1.  Mengubah ukuran gambar input ke ukuran yang dibutuhkan model (640x640) dengan menjaga rasio aspek (letterboxing).
     * 2.  Menormalkan nilai piksel gambar dari [0, 255] menjadi [0.0, 1.0] dan menyimpannya dalam ByteBuffer.
     * 3.  Menjalankan interpreter TFLite dengan ByteBuffer sebagai input.
     * 4.  Mengurai tensor output mentah dari model menjadi daftar [RawDetection].
     * 5.  Menerapkan Non-Maximum Suppression (NMS) untuk menghilangkan kotak-kotak deteksi yang tumpang tindih.
     *
     * @param bitmap Gambar input.
     * @return Daftar [RawDetection] setelah NMS.
     */
    private fun runInference(bitmap: Bitmap): List<RawDetection> {
        val interpreter = tfliteYolo ?: return emptyList()

        // --- Pra-pemrosesan Gambar ---
        // 1. Skalakan gambar dengan menjaga rasio aspek.
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val scale = min(SIZE_YOLO.toFloat() / originalWidth, SIZE_YOLO.toFloat() / originalHeight)
        val newWidth = (originalWidth * scale).toInt()
        val newHeight = (originalHeight * scale).toInt()
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        // 2. Buat bitmap baru berukuran 640x640 dan gambar bitmap yang di-resize ke tengahnya (letterboxing).
        val inputBitmap = Bitmap.createBitmap(SIZE_YOLO, SIZE_YOLO, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        canvas.drawColor(GraphicsColor.GRAY) // Latar belakang abu-abu
        val dx = (SIZE_YOLO - newWidth) / 2f
        val dy = (SIZE_YOLO - newHeight) / 2f
        canvas.drawBitmap(resizedBitmap, dx, dy, null)

        // 3. Konversi bitmap ke ByteBuffer dan normalisasi.
        val inputBuffer = ByteBuffer.allocateDirect(1 * SIZE_YOLO * SIZE_YOLO * 3 * 4).order(ByteOrder.nativeOrder())
        val intValues = IntArray(SIZE_YOLO * SIZE_YOLO)
        inputBitmap.getPixels(intValues, 0, SIZE_YOLO, 0, 0, SIZE_YOLO, SIZE_YOLO)
        inputBuffer.rewind()
        for (v in intValues) {
            inputBuffer.putFloat(((v shr 16 and 0xFF) / 255.0f)) // R
            inputBuffer.putFloat(((v shr 8 and 0xFF) / 255.0f))  // G
            inputBuffer.putFloat(((v and 0xFF) / 255.0f))        // B
        }

        // --- Inferensi ---
        // Siapkan buffer output dan jalankan model.
        val outputTensor = interpreter.getOutputTensor(0)
        val shape = outputTensor.shape() // Shape biasanya [1, jumlah_kelas + 4, jumlah_deteksi]
        val rows = shape[1] // jumlah_kelas + 4 (x, y, w, h)
        val cols = shape[2] // jumlah_deteksi
        val outputBuffer = Array(1) { Array(rows) { FloatArray(cols) } }
        interpreter.run(inputBuffer, outputBuffer)

        // --- Pasca-pemrosesan ---
        val rawDetections = ArrayList<RawDetection>()
        val rawOutput = outputBuffer[0] // [rows][cols]

        // Iterasi melalui setiap kolom (setiap potensi deteksi)
        for (i in 0 until cols) {
            var maxScore = 0f
            var classId = -1

            // Cari kelas dengan skor kepercayaan (confidence) tertinggi
            for (c in 4 until rows) {
                val score = rawOutput[c][i]
                if (score > maxScore) {
                    maxScore = score
                    classId = c - 4 // ID kelas dimulai setelah 4 nilai (x,y,w,h)
                }
            }

            // Filter deteksi dengan skor di bawah ambang batas (threshold)
            if (maxScore > 0.10f) {
                // Ambil koordinat kotak pembatas
                val xc = rawOutput[0][i]
                val yc = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                // Konversi koordinat dari ruang gambar 640x640 kembali ke ruang gambar asli
                val xPos = xc - dx
                val yPos = yc - dy
                val originalX = xPos / scale
                val originalY = yPos / scale
                val originalW = w / scale
                val originalH = h / scale

                // Hitung koordinat pojok kiri atas dan kanan bawah (left, top, right, bottom)
                val left = originalX - originalW / 2
                val top = originalY - originalH / 2
                val right = originalX + originalW / 2
                val bottom = originalY + originalH / 2

                rawDetections.add(RawDetection(classId, maxScore, RectF(left, top, right, bottom)))
            }
        }

        // Terapkan Non-Maximum Suppression untuk menyaring deteksi yang tumpang tindih
        return applyNMS(rawDetections)
    }

    /**
     * @brief Menerapkan algoritma Non-Maximum Suppression (NMS) pada daftar deteksi.
     *
     * NMS digunakan untuk menghilangkan kotak-kotak deteksi yang redundan dan tumpang tindih untuk objek yang sama.
     * Algoritma:
     * 1. Urutkan deteksi berdasarkan skor kepercayaan (confidence) dari tertinggi ke terendah.
     * 2. Ambil deteksi dengan skor tertinggi dan tambahkan ke daftar hasil akhir.
     * 3. Hapus semua deteksi lain yang memiliki IoU (Intersection over Union) tinggi dengan deteksi terbaik.
     * 4. Ulangi hingga tidak ada deteksi yang tersisa.
     *
     * @param detections Daftar [RawDetection] mentah sebelum NMS.
     * @return Daftar [RawDetection] yang telah difilter setelah NMS.
     */
    private fun applyNMS(detections: List<RawDetection>): List<RawDetection> {
        val finalDetections = ArrayList<RawDetection>()
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            finalDetections.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(best.rect, other.rect) > 0.45f) { // Ambang batas IoU 0.45
                    iterator.remove()
                }
            }
        }
        return finalDetections
    }

    /**
     * @brief Menghitung Intersection over Union (IoU) antara dua kotak pembatas (bounding box).
     *
     * IoU adalah metrik yang mengukur sejauh mana dua kotak saling tumpang tindih.
     * Rumus: (Area Irisan) / (Area Gabungan)
     * Nilainya berkisar dari 0 (tidak tumpang tindih) hingga 1 (tumpang tindih sempurna).
     *
     * @param a Kotak pembatas pertama.
     * @param b Kotak pembatas kedua.
     * @return Nilai float yang merepresentasikan IoU.
     */
    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        if (interRight < interLeft || interBottom < interTop) return 0f // Tidak ada irisan
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val unionArea = (a.width() * a.height()) + (b.width() * b.height()) - interArea
        return interArea / unionArea
    }
    
    /**
     * @brief Melepaskan sumber daya yang digunakan oleh interpreter TensorFlow Lite.
     *
     * Penting untuk memanggil fungsi ini ketika YoloDetector tidak lagi digunakan (misalnya,
     * di dalam `onDestroy` atau `onCleared` dari ViewModel) untuk mencegah kebocoran memori.
     */
    fun close() {
        tfliteYolo?.close()
    }
}