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

/**
 * @file Utils.kt
 * @brief Berisi kumpulan fungsi utilitas (pembantu) yang digunakan di berbagai bagian aplikasi.
 *        Fungsi-fungsi ini menangani tugas-tugas umum seperti manipulasi file/URI dan pemrosesan gambar.
 */

/**
 * @brief Membuat dan mengembalikan sebuah URI sementara (temporary) untuk menyimpan output dari kamera.
 *
 * Menggunakan `FileProvider` adalah cara modern dan aman untuk berbagi file antar aplikasi,
 * yang diperlukan saat memberikan path file ke aplikasi kamera agar ia bisa menulis gambar.
 *
 * @param context Konteks aplikasi.
 * @return URI yang menunjuk ke file kosong di direktori cache.
 */
fun getTempUri(context: Context): Uri {
    // Buat file di cache directory agar tidak tersimpan permanen.
    val f = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
    // Dapatkan URI untuk file tersebut melalui FileProvider. Authority harus cocok dengan yang
    // didefinisikan di AndroidManifest.xml.
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
}

/**
 * @brief Membuka activity untuk cropping gambar menggunakan ActivityResultLauncher.
 *
 * Fungsi ini menyederhanakan pemanggilan library cropper dengan opsi yang sudah dikonfigurasi.
 *
 * @param launcher Launcher yang sudah dibuat dengan `rememberLauncherForActivityResult(CropImageContract())`.
 * @param uri URI dari gambar yang ingin di-crop.
 */
fun launchCrop(launcher: ActivityResultLauncher<CropImageContractOptions>, uri: Uri) {
    launcher.launch(CropImageContractOptions(uri, CropImageOptions().apply {
        // Nonaktifkan opsi untuk memilih dari galeri/kamera di dalam cropper itu sendiri
        // karena kita sudah menanganinya di luar.
        imageSourceIncludeGallery = false
        imageSourceIncludeCamera = false
        activityTitle = "Crop Image"
    }))
}

/**
 * @brief Mengonversi URI gambar menjadi objek Bitmap.
 *
 * Fungsi ini juga menangani rotasi gambar yang mungkin salah berdasarkan data EXIF.
 *
 * @param context Konteks aplikasi.
 * @param uri URI dari gambar.
 * @return Objek Bitmap yang sudah benar rotasinya, atau null jika terjadi kesalahan.
 */
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        // Gunakan ImageDecoder untuk Android P (API 28) ke atas, karena lebih modern.
        val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                d.isMutableRequired = true // Diperlukan agar bitmap bisa diubah (misalnya dirotasi).
            }
        } else {
            // Gunakan MediaStore untuk versi Android yang lebih lama.
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        // Periksa dan perbaiki rotasi bitmap sebelum mengembalikannya.
        rotateBitmapIfRequired(context, originalBitmap, uri).copy(Bitmap.Config.ARGB_8888, true)
    } catch (e: Exception) {
        Log.e("CalorieLens", "Error uriToBitmap", e)
        null
    }
}

/**
 * @brief Memeriksa data EXIF dari sebuah gambar dan memutarnya jika diperlukan.
 *
 * Beberapa perangkat menyimpan gambar dalam posisi landscape dan menambahkan tag EXIF
 * untuk orientasi yang benar. Fungsi ini membaca tag tersebut dan menerapkan rotasi
 * yang sesuai pada Bitmap.
 *
 * @param context Konteks aplikasi.
 * @param bitmap Bitmap yang akan diperiksa.
 * @param uri URI asli dari gambar, diperlukan untuk membaca data EXIF.
 * @return Bitmap yang sudah dirotasi dengan benar.
 */
private fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
    val input = context.contentResolver.openInputStream(uri) ?: return bitmap
    val exif = ExifInterface(input)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    
    // Terapkan rotasi berdasarkan nilai orientasi dari EXIF.
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
        else -> bitmap // Tidak perlu rotasi.
    }
}

/**
 * @brief Fungsi pembantu tingkat rendah untuk memutar sebuah Bitmap.
 *
 * @param source Bitmap sumber.
 * @param angle Sudut rotasi dalam derajat (misalnya, 90.0f).
 * @return Bitmap baru yang merupakan hasil rotasi.
 */
private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}