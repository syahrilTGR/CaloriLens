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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = Firebase.auth
    val db = Firebase.firestore
    val context = LocalContext.current

    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") } // Default gender
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CalorieLens", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(if (isRegisterMode) "Create Profile" else "Welcome Back", fontSize = 18.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("Weight (kg)") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = height, onValueChange = { height = it },
                    label = { Text("Height (cm)") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = age, onValueChange = { age = it },
                label = { Text("Age") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
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

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        
        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (isRegisterMode) {
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty() && name.isNotEmpty()) {
                        isLoading = true
                        errorMessage = null
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val uid = task.result.user?.uid ?: ""
                                    val userProfile = hashMapOf(
                                        "name" to name,
                                        "weight" to (weight.toFloatOrNull() ?: 0f),
                                        "height" to (height.toFloatOrNull() ?: 0f),
                                        "age" to (age.toIntOrNull() ?: 0),
                                        "gender" to gender, // Add gender
                                        "email" to email
                                    )
                                    
                                    db.collection("users").document(uid).set(userProfile)
                                        .addOnSuccessListener {
                                            isLoading = false
                                            Toast.makeText(context, "Profile Created!", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
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
        } else {
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        errorMessage = null
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    onLoginSuccess()
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

        TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
            Text(if (isRegisterMode) "Already have an account? Login" else "New here? Create Account")
        }
    }
}