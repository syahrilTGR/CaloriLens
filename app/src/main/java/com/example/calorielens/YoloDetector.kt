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

class YoloDetector(private val context: Context) {

    private var tfliteYolo: Interpreter? = null
    private val TAG = "YoloDetector"
    private val MODEL_YOLO = "yolo_model.tflite"
    private val LABEL_YOLO = "labels_yolo.txt"
    private val SIZE_YOLO = 640
    
    private var labelsYolo: List<String> = emptyList()

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            tfliteYolo = Interpreter(loadModelFile(MODEL_YOLO))
            labelsYolo = try {
                context.assets.open(LABEL_YOLO).bufferedReader().useLines { it.toList() }
            } catch (e: Exception) {
                emptyList()
            }
            Log.d(TAG, "Model loaded. Labels: ${labelsYolo.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val rawDetections = runInference(bitmap)
        val results = ArrayList<DetectionResult>()

        for (det in rawDetections) {
            val labelName = if (det.classId < labelsYolo.size) labelsYolo[det.classId] else "unknown"
            // Calorie calculation will be handled by caller/ViewModel, here we just return detections
            // For now, passing 0f as placeholder, main logic can update it
            results.add(DetectionResult(det.rect, labelName, det.confidence, 0f))
        }
        return results
    }

    private fun runInference(bitmap: Bitmap): List<RawDetection> {
        val interpreter = tfliteYolo ?: return emptyList()

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

        val inputBuffer = ByteBuffer.allocateDirect(1 * SIZE_YOLO * SIZE_YOLO * 3 * 4).order(ByteOrder.nativeOrder())
        val intValues = IntArray(SIZE_YOLO * SIZE_YOLO)
        inputBitmap.getPixels(intValues, 0, SIZE_YOLO, 0, 0, SIZE_YOLO, SIZE_YOLO)

        for (v in intValues) {
            inputBuffer.putFloat(((v shr 16 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((v shr 8 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((v and 0xFF) / 255.0f))
        }

        val outputTensor = interpreter.getOutputTensor(0)
        val shape = outputTensor.shape()
        val rows = shape[1]
        val cols = shape[2]
        val outputBuffer = Array(1) { Array(rows) { FloatArray(cols) } }
        interpreter.run(inputBuffer, outputBuffer)

        val rawDetections = ArrayList<RawDetection>()
        val rawOutput = outputBuffer[0]

        for (i in 0 until cols) {
            var maxScore = 0f
            var classId = -1

            for (c in 4 until rows) {
                val score = rawOutput[c][i]
                if (score > maxScore) {
                    maxScore = score
                    classId = c - 4
                }
            }

            if (maxScore > 0.10f) {
                val xc = rawOutput[0][i]
                val yc = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                val xPos = xc - dx
                val yPos = yc - dy

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
    
    fun close() {
        tfliteYolo?.close()
    }
}