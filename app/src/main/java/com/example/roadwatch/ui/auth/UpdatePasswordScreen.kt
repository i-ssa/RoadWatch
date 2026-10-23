package com.example.roadwatch.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun UpdatePasswordScreen(loading: Boolean, error: String?, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Choose a new password", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(password, { password = it }, label = { Text("New password") },
            enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = attempted && password.length < 8,
            supportingText = { Text("Use at least 8 characters.") })
        OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") },
            enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = attempted && confirm != password,
            supportingText = { if (attempted && confirm != password) Text("Passwords must match.") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { attempted = true; if (password.length >= 8 && confirm == password) onSave(password) },
            enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (loading) "Saving…" else "Update password")
        }
        TextButton(onClick = onCancel, enabled = !loading) { Text("Cancel and sign out") }
    }
}
