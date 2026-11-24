package com.example.evaluacin_consolidado2

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.evaluacin_consolidado2.ui.theme.Critical
import com.example.evaluacin_consolidado2.ui.theme.DeepTeal
import com.google.firebase.auth.FirebaseAuth
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.launch

private object AccountRevision {
    var value by mutableIntStateOf(0)
}

@Composable
internal fun AccountGate() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var userId by remember { mutableStateOf(auth.currentUser?.uid) }
    var profileLoading by remember(userId) { mutableStateOf(userId != null) }
    val revision = AccountRevision.value

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { userId = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        FirebaseProfiles.load(uid) { profile ->
            if (profile != null) {
                val local = ProtectionConfig(context, uid)
                local.ownerName = profile.name
                local.ownerNumber = profile.phone
                if (local.contactNumber.isBlank() && profile.contactNumber.isNotBlank()) {
                    local.contactName = profile.contactName
                    local.contactNumber = profile.contactNumber
                }
                AccountRevision.value++
            }
            profileLoading = false
        }
    }

    val uid = userId
    if (uid == null) {
        AccessScreen(auth)
    } else {
        val local = remember(uid, revision) { ProtectionConfig(context, uid) }
        if (profileLoading && (local.ownerName.isBlank() || local.ownerNumber.isBlank())) {
            AccountLayout(title = "Cargando perfil") { Text("Espera un momento…") }
        } else if (local.ownerName.isBlank() || local.ownerNumber.isBlank()) {
            CompleteProfileScreen(uid)
        } else {
            key(revision) { AlertCuscoScreen(uid) }
        }
    }
}

@Composable
private fun AccessScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AccountLayout(title = if (register) "Crear cuenta" else "Bienvenido a AlertCusco") {
        Text(if (register) "Solo pedimos datos para identificarte y enviar una alerta útil." else "Entra para configurar tu protección y contacto de confianza.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        if (register) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(9) }, label = { Text("Tu celular de 9 dígitos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(value = email, onValueChange = { email = it.trim().take(120) }, label = { Text("Correo electrónico") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (register) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Repite la contraseña") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Critical, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val cleanEmail = email.trim()
                error = when {
                    register && name.trim().length < 2 -> "Escribe tu nombre."
                    register && !ProtectionConfig.validNumber(phone) -> "Escribe un celular peruano válido de 9 dígitos."
                    !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> "Escribe un correo válido."
                    password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
                    register && confirm != password -> "Las contraseñas no coinciden."
                    else -> ""
                }
                if (error.isNotBlank()) return@Button
                busy = true
                if (register) {
                    auth.createUserWithEmailAndPassword(cleanEmail, password).addOnCompleteListener { task ->
                        busy = false
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid
                            if (uid != null) {
                                val local = ProtectionConfig(context, uid)
                                local.ownerName = name
                                local.ownerNumber = phone
                                AccountRevision.value++
                                FirebaseProfiles.create(uid, PersonProfile(name.trim(), phone.trim())) { saved ->
                                    if (!saved) ProtectionState.snapshot = ProtectionState.snapshot.copy(lastResult = "Cuenta creada. El perfil aún no se sincronizó con Firebase.")
                                }
                            }
                        } else error = "No se pudo crear la cuenta. Revisa que Correo/contraseña esté activado en Firebase y que tengas conexión."
                    }
                } else {
                    auth.signInWithEmailAndPassword(cleanEmail, password).addOnCompleteListener { task ->
                        busy = false
                        if (!task.isSuccessful) error = "No se pudo iniciar sesión. Revisa el correo, la contraseña y la conexión."
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Espera un momento…" else if (register) "Crear cuenta" else "Entrar") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        val idToken = GoogleAccess.idToken(context, context.getString(R.string.default_web_client_id))
                        GoogleAccess.signIn(auth, idToken) { success ->
                            busy = false
                            if (!success) error = "No se pudo entrar con Google. Revisa la conexión y la configuración de Firebase."
                        }
                    } catch (exception: GetCredentialCancellationException) {
                        busy = false
                    } catch (exception: NoCredentialException) {
                        busy = false
                        error = "No hay una cuenta de Google disponible en este teléfono. Añade una cuenta e inténtalo de nuevo."
                    } catch (exception: Exception) {
                        busy = false
                        error = "No se pudo abrir el acceso con Google. Comprueba Google Play Services."
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continuar con Google") }
        TextButton(onClick = { register = !register; error = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Ya tengo cuenta" else "Crear una cuenta")
        }
    }
}

@Composable
private fun CompleteProfileScreen(uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    AccountLayout(title = "Completa tu perfil") {
        Text("Necesitamos tu nombre y número para identificar la alerta que recibirá tu contacto.")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(9) }, label = { Text("Tu celular de 9 dígitos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(error, color = Critical) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            if (name.trim().length < 2 || !ProtectionConfig.validNumber(phone)) { error = "Revisa el nombre y el celular peruano de 9 dígitos."; return@Button }
            busy = true
            FirebaseProfiles.create(uid, PersonProfile(name.trim(), phone.trim())) { saved ->
                busy = false
                if (saved) {
                    val local = ProtectionConfig(context, uid)
                    local.ownerName = name
                    local.ownerNumber = phone
                    AccountRevision.value++
                } else error = "No se pudo guardar el perfil en Firebase. Revisa la conexión y las reglas."
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar perfil") }
        TextButton(onClick = {
            FirebaseAuth.getInstance().signOut()
            scope.launch { runCatching { GoogleAccess.clearSelection(context) } }
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cerrar sesión") }
    }
}

@Composable
private fun AccountLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AnimatedMark(R.raw.openingapp, Modifier.size(142.dp), repeat = false)
                    Text("ALERTCUSCO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DeepTeal)
                    Spacer(Modifier.height(10.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        content()
                    }
                }
            }
        }
    }
}
