package com.example.calorielens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.File
import java.io.IOException

fun getTempUri(context: Context): Uri {
    val f = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
}

fun launchCrop(launcher: ActivityResultLauncher<CropImageContractOptions>, uri: Uri) {
    launcher.launch(CropImageContractOptions(uri, CropImageOptions().apply {
        imageSourceIncludeGallery = false
        imageSourceIncludeCamera = false
        activityTitle = "Crop Image"
    }))
}

fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
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