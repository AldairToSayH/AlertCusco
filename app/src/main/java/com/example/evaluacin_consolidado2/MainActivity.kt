package com.example.evaluacin_consolidado2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.evaluacin_consolidado2.ui.theme.Caution
import com.example.evaluacin_consolidado2.ui.theme.Critical
import com.example.evaluacin_consolidado2.ui.theme.DeepTeal
import com.example.evaluacin_consolidado2.ui.theme.AlertCuscoTheme
import com.example.evaluacin_consolidado2.ui.theme.Ink
import com.example.evaluacin_consolidado2.ui.theme.PaleCaution
import com.example.evaluacin_consolidado2.ui.theme.PaleCritical
import com.example.evaluacin_consolidado2.ui.theme.PaleTeal
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlertCuscoTheme { AccountGate() } }
    }
}

@Composable
internal fun AlertCuscoScreen(userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember(userId) { ProtectionConfig(context, userId) }
    val sensors = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val hasAccel = remember { sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
    val hasNear = remember { sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null }
    val hasLight = remember { sensors.getDefaultSensor(Sensor.TYPE_LIGHT) != null }
    val snapshot = ProtectionState.snapshot

    var configured by remember { mutableStateOf(config.hasPin && ProtectionConfig.validNumber(config.contactNumber)) }
    var editing by rememberSaveable { mutableStateOf(!configured) }
    var name by rememberSaveable { mutableStateOf(config.contactName) }
    var number by rememberSaveable { mutableStateOf(ProtectionConfig.localNumber(config.contactNumber)) }
    var newPin by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val canCall = result[Manifest.permission.CALL_PHONE] == true || context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (canCall) {
            try {
                ProtectionService.command(context, ProtectionService.ACTION_START)
                message = "Protección activada. Los sensores vigilan el celular."
            } catch (exception: RuntimeException) {
                message = "Android no permitió iniciar la protección. Inténtalo con la app abierta."
            }
        } else message = "Concede el permiso de llamada para avisar a tu contacto."
    }

    fun activate() {
        if (!configured) { message = "Guarda un contacto y un PIN antes de activar."; return }
        if (!hasAccel) { message = "Este teléfono no tiene acelerómetro."; return }
        val needed = mutableListOf(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(needed.toTypedArray())
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null; enteredPin = ""; pinError = false },
            title = { Text("Introduce tu PIN") },
            text = {
                Column {
                    Text(when (pending) {
                        "dismiss" -> "Confirma que quieres detener la alerta."
                        "stop" -> "Confirma que quieres desactivar la protección."
                        else -> "Confirma que quieres cambiar el contacto."
                    })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { enteredPin = it.filter(Char::isDigit).take(4); pinError = false },
                        label = { Text("PIN de 4 dígitos") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    if (pinError) Text("PIN incorrecto.", color = Critical, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (config.checkPin(enteredPin)) {
                        when (pending) {
                            "dismiss" -> ProtectionService.command(context, ProtectionService.ACTION_DISMISS)
                            "stop" -> ProtectionService.command(context, ProtectionService.ACTION_STOP)
                            "edit" -> editing = true
                        }
                        pending = null
                        enteredPin = ""
                        pinError = false
                        message = ""
                    } else pinError = true
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { pending = null; enteredPin = ""; pinError = false }) { Text("Cancelar") } },
        )
    }

    if (snapshot.alert != null) {
        Scaffold(containerColor = PaleCritical) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("ALERTCUSCO", style = MaterialTheme.typography.labelLarge, color = Critical, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                AlertCard(snapshot) { pending = "dismiss" }
                if (snapshot.lastResult.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(snapshot.lastResult, style = MaterialTheme.typography.bodyLarge, color = Critical)
                }
            }
        }
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("ALERTCUSCO", style = MaterialTheme.typography.labelLarge, color = DeepTeal, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Protección a tu alcance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Actívala solo cuando la necesites.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedMark(R.raw.openingapp, Modifier.size(96.dp), repeat = false)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hola, ${config.ownerName}", style = MaterialTheme.typography.bodyMedium, color = DeepTeal)
                    TextButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        scope.launch { runCatching { GoogleAccess.clearSelection(context) } }
                    }, enabled = !snapshot.active) { Text("Cerrar sesión") }
                }
            }
            item { StatusCard(snapshot) }
            if (message.isNotBlank()) item {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = if (message.contains("incorrecto")) Critical else DeepTeal)
            }
            if (snapshot.lastResult.isNotBlank()) item {
                Text(snapshot.lastResult, style = MaterialTheme.typography.bodyMedium, color = DeepTeal)
            }
            item {
                SectionCard("Protección") {
                    Text("Detecta un posible arrebato y reconoce tu gesto de auxilio. Si se activa una alerta, llamará a ${name.ifBlank { "tu contacto" }}.")
                    Spacer(Modifier.height(14.dp))
                    if (snapshot.active) OutlinedButton(
                        onClick = { pending = "stop" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Desactivar protección") }
                    else Button(onClick = { activate() }, modifier = Modifier.fillMaxWidth()) { Text("Activar protección") }
                }
            }
            item { SensorsCard(snapshot, hasAccel, hasNear, hasLight) }
            item {
                SectionCard("Contacto de confianza") {
                    if (editing && !snapshot.active) {
                        Text("Elige a una persona de confianza. No uses un número de emergencias.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = number, onValueChange = { number = it.filter(Char::isDigit).take(9) }, label = { Text("Celular de 9 dígitos") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) }, label = { Text(if (configured) "Nuevo PIN (opcional)" else "PIN de 4 dígitos") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = {
                            when {
                                name.isBlank() -> message = "Escribe el nombre del contacto."
                                !ProtectionConfig.validNumber(number) -> message = "Escribe un celular peruano válido de 9 dígitos."
                                !config.hasPin && newPin.length != 4 -> message = "Crea un PIN de 4 dígitos."
                                newPin.isNotEmpty() && newPin.length != 4 -> message = "El nuevo PIN debe tener 4 dígitos."
                                else -> {
                                    config.contactName = name
                                    config.contactNumber = number
                                    if (newPin.isNotEmpty()) config.setPin(newPin)
                                    FirebaseProfiles.saveContact(userId, name.trim(), number.trim()) { saved ->
                                        if (!saved) message = "Contacto guardado en este teléfono; no se pudo sincronizar con Firebase."
                                    }
                                    newPin = ""
                                    configured = true
                                    editing = false
                                    message = "Contacto guardado."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar configuración") }
                    } else {
                        Text(name.ifBlank { "Sin contacto" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(number.ifBlank { "Añade un número" }, style = MaterialTheme.typography.bodyMedium)
                        if (!snapshot.active) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { pending = "edit" }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar configuración") }
                        }
                    }
                }
            }
            item {
                SectionCard("Cómo usar AlertCusco") {
                    Text("1. Activa la protección. Un tirón brusco puede activar la alerta si llevas el celular en la mano.")
                    Spacer(Modifier.height(10.dp))
                    Text("2. Si lo guardas, el bolsillo se arma tras 3 segundos. La extracción brusca activa sonido y vibración.")
                    Spacer(Modifier.height(10.dp))
                    Text("3. Tres sacudidas rápidas activan el SOS con sonido y vibración. Puedes cancelar la alerta con tu PIN antes de la llamada.")
                    if (!hasNear || !hasLight) {
                        Spacer(Modifier.height(12.dp))
                        Text("Falta un sensor para vigilar el bolsillo. El SOS por gesto sigue disponible.", color = Caution)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(snapshot: ProtectionSnapshot) {
    val alarm = snapshot.alert != null
    val guarded = snapshot.active && snapshot.phase == "Guardado y protegido"
    val color = when { alarm -> PaleCritical; guarded -> PaleTeal; snapshot.active -> PaleCaution; else -> MaterialTheme.colorScheme.surface }
    val dot = when { alarm -> Critical; guarded -> DeepTeal; snapshot.active -> Caution; else -> MaterialTheme.colorScheme.outline }
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ESTADO ACTUAL", style = MaterialTheme.typography.labelMedium, color = dot, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.size(12.dp).background(dot, CircleShape))
                    Text(snapshot.phase, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(if (!snapshot.active) "Activa la protección para vigilar el celular." else "Los sensores vigilan el celular durante esta sesión.")
            }
            if (snapshot.active) AnimatedMark(R.raw.alertaamarilla, Modifier.size(76.dp), repeat = true)
        }
    }
}

@Composable
private fun AlertCard(snapshot: ProtectionSnapshot, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PaleCritical), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedMark(
                if (snapshot.alert != DetectionEngine.Trigger.SOS) R.raw.alertaalarma else R.raw.alertaroja,
                Modifier.size(168.dp).align(Alignment.CenterHorizontally),
                repeat = true,
            )
            Text(when (snapshot.alert) {
                DetectionEngine.Trigger.SNATCH -> "Posible arrebato"
                DetectionEngine.Trigger.EXTRACTION -> "Posible extracción"
                else -> "SOS activado"
            }, style = MaterialTheme.typography.titleLarge, color = Critical, fontWeight = FontWeight.Bold)
            Text(if (snapshot.secondsUntilContact > 0) "Llamada al contacto en ${snapshot.secondsUntilContact} s." else "Consulta el resultado de la llamada debajo del estado.")
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Critical), modifier = Modifier.fillMaxWidth()) { Text("Desactivar alerta con PIN") }
        }
    }
}

@Composable
private fun SensorsCard(snapshot: ProtectionSnapshot, hasAccel: Boolean, hasNear: Boolean, hasLight: Boolean) {
    SectionCard("Sensores en tiempo real") {
        SensorLine("Proximidad", if (!hasNear) "No disponible" else if (!snapshot.active || snapshot.proximity == null) "En reposo" else if (snapshot.proximity) "Cubierto" else "Libre")
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SensorLine("Luz ambiental", if (!hasLight) "No disponible" else snapshot.lux?.let { String.format(Locale.getDefault(), "%.0f lx", it) } ?: "En reposo")
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SensorLine("Movimiento", if (!hasAccel) "No disponible" else if (snapshot.active) String.format(Locale.getDefault(), "%.1f m/s²", snapshot.motion) else "En reposo")
    }
}

@Composable
private fun SensorLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = DeepTeal, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun AnimatedMark(resource: Int, modifier: Modifier, repeat: Boolean) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resource))
    LottieAnimation(
        composition = composition,
        iterations = if (repeat) LottieConstants.IterateForever else 1,
        modifier = modifier,
    )
}
