package com.example.calorielens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * @file LoginScreen.kt
 * @brief Berisi implementasi UI dan logika untuk layar Login dan Registrasi.
 *        Layar ini menangani autentikasi pengguna dan pembuatan profil baru.
 */

/**
 * @brief Composable utama untuk layar login dan registrasi.
 *
 * Fungsi ini mengelola dua mode: Login dan Registrasi (`isRegisterMode`).
 * Ini menangani input pengguna, validasi sederhana, proses loading, penanganan error,
 * dan komunikasi dengan Firebase Authentication dan Firestore.
 *
 * @param onLoginSuccess Lambda yang akan dipanggil ketika pengguna berhasil login atau mendaftar,
 *                       memberi sinyal ke `MainActivity` untuk melanjutkan ke layar utama.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    // Inisialisasi Firebase Auth, Firestore, dan konteks lokal.
    val auth = Firebase.auth
    val db = Firebase.firestore
    val context = LocalContext.current

    // --- States ---
    // State untuk beralih antara mode Login (false) dan Registrasi (true).
    var isRegisterMode by remember { mutableStateOf(false) }
    // State untuk input form.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") } // Nilai default
    
    // State untuk UI feedback.
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()), // Memungkinkan scroll jika konten melebihi layar.
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CalorieLens", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(if (isRegisterMode) "Create Profile" else "Welcome Back", fontSize = 18.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        // --- Form Registrasi (Hanya tampil jika isRegisterMode true) ---
        if (isRegisterMode) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (cm)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Gender:", modifier = Modifier.weight(0.3f))
                Row(Modifier.weight(0.7f), horizontalArrangement = Arrangement.SpaceAround) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = gender == "Male", onClick = { gender = "Male" })
                        Text("Male")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = gender == "Female", onClick = { gender = "Female" })
                        Text("Female")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // --- Form Email & Password (Tampil di kedua mode) ---
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        
        // Tampilkan pesan error jika ada.
        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(24.dp))

        // --- Tombol Aksi (Register atau Login) ---
        if (isRegisterMode) {
            Button(
                onClick = {
                    // Validasi input sederhana.
                    if (email.isNotEmpty() && password.isNotEmpty() && name.isNotEmpty()) {
                        isLoading = true
                        errorMessage = null
                        // 1. Buat pengguna baru di Firebase Auth.
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val uid = task.result.user?.uid ?: ""
                                    // 2. Buat objek profil pengguna.
                                    val userProfile = hashMapOf(
                                        "name" to name,
                                        "weight" to (weight.toFloatOrNull() ?: 0f),
                                        "height" to (height.toFloatOrNull() ?: 0f),
                                        "age" to (age.toIntOrNull() ?: 0),
                                        "gender" to gender,
                                        "email" to email
                                    )
                                    
                                    // 3. Simpan profil ke Firestore.
                                    db.collection("users").document(uid).set(userProfile)
                                        .addOnSuccessListener {
                                            isLoading = false
                                            Toast.makeText(context, "Profile Created!", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess() // Panggil callback sukses.
                                        }
                                        .addOnFailureListener { e ->
                                            isLoading = false
                                            errorMessage = "Save Profile Error: ${e.message}"
                                        }
                                } else {
                                    isLoading = false
                                    errorMessage = "Sign Up Failed: ${task.exception?.localizedMessage}"
                                }
                            }
                    } else {
                        errorMessage = "Please fill all fields"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Register & Save Profile")
            }
        } else { // Mode Login
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        errorMessage = null
                        // Coba login dengan email dan password yang ada.
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    onLoginSuccess() // Panggil callback sukses.
                                } else {
                                    errorMessage = "Login Failed: ${task.exception?.localizedMessage}"
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Login")
            }
        }

        // Tombol untuk beralih mode.
        TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
            Text(if (isRegisterMode) "Already have an account? Login" else "New here? Create Account")
        }
    }
}