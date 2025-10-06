package com.example.touristquiz.ui.login

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.clip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.touristquiz.data.AuthRepository

@Composable
fun RegisterScreen(
    onLoginClick: () -> Unit,
    authRepository: AuthRepository,
    onRegisterSuccess: () -> Unit
) {
    var korisnickoIme by remember { mutableStateOf("") }
    var ime by remember { mutableStateOf("") }
    var prezime by remember { mutableStateOf("") }
    var brTelefona by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri
    }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        TextField(value = korisnickoIme, onValueChange = { korisnickoIme = it }, label = { Text("Korisničko ime") })
        TextField(value = ime, onValueChange = { ime = it }, label = { Text("Ime") })
        TextField(value = prezime, onValueChange = { prezime = it }, label = { Text("Prezime") })
        TextField(value = brTelefona, onValueChange = { brTelefona = it }, label = { Text("Broj telefona") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        TextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))

        Spacer(modifier = Modifier.height(16.dp))

        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            )
        }

        Button(onClick = { launcher.launch("image/*") }) {
            Text("Izaberi profilnu sliku")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Validate required fields before attempting registration
                val anyBlank = listOf(korisnickoIme, ime, prezime, brTelefona, email, password).any { it.trim().isEmpty() }
                if (anyBlank) {
                    errorMessage = "Sva polja su obavezna."
                    return@Button
                }
                errorMessage = ""
                isLoading = true
                coroutineScope.launch {
                    authRepository.registerUser(
                        email = email.trim(),
                        password = password,
                        username = korisnickoIme.trim(),
                        firstName = ime.trim(),
                        lastName = prezime.trim(),
                        phone = brTelefona.trim(),
                        imageUri = imageUri
                    ) { success: Boolean, error: String? ->
                        isLoading = false
                        if (success) {
                            // Auto-login: navigate onward; Firebase usually signs in after create
                            onRegisterSuccess()
                        } else {
                            errorMessage = error ?: "Registracija nije uspela"
                        }
                    }
                }
            },
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Registrujem..." else "Register")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onLoginClick) {
            Text("Već imate nalog? Login")
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}