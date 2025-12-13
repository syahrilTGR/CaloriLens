package com.example.calorielens

import android.graphics.RectF

data class NutritionData(
    val foodName: String = "",
    val proteins: Float = 0f,
    val fat: Float = 0f,
    val carbohydrate: Float = 0f,
    val calories: Float = 0f
)

data class DetectionResult(
    val boundingBox: RectF,
    val className: String,
    val confidence: Float,
    val estimatedCalories: Float
)

data class RawDetection(
    val classId: Int,
    val confidence: Float,
    val rect: RectF
)