package com.example.evaluacin_consolidado2

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class ProtectionSnapshot(
    val active: Boolean = false,
    val phase: String = "Desactivado",
    val proximity: Boolean? = null,
    val lux: Float? = null,
    val motion: Float = 0f,
    val alert: DetectionEngine.Trigger? = null,
    val secondsUntilContact: Int = 0,
    val lastResult: String = "",
    val testMode: Boolean = false,
)

internal object ProtectionState {
    var snapshot by mutableStateOf(ProtectionSnapshot())
}

class ProtectionService : Service(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var notifications: NotificationManager
    private val engine = DetectionEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var accelerationSensor: Sensor? = null
    private var alert: DetectionEngine.Trigger? = null
    private var alertDeadline = 0L
    private var lastPublish = 0L
    private var tone: ToneGenerator? = null
    private var systemAlarm: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var previousAlarmVolume: Int? = null
    private var contacting = false
    private var testMode = false
    private var stopMessage = "Protección desactivada."

    private val pulse = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (alert == null) engine.tick(now)?.let { triggerAlert(it, now) }
            if (alert != null && !contacting && now >= alertDeadline) {
                contacting = true
                sendAlert()
            }
            publish(now)
            handler.postDelayed(this, 250L)
        }
    }

    private val sirenPulse = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            handler.postDelayed(this, 750L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        accelerationSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProtection(intent.getBooleanExtra(EXTRA_TEST_MODE, false))
            ACTION_DISMISS -> dismissAlert()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProtection(test: Boolean) {
        if (ProtectionState.snapshot.active) return
        if (accelerationSensor == null) {
            stopMessage = "Este teléfono no tiene acelerómetro."
            stopSelf()
            return
        }
        if (!test) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                stopMessage = "Inicia sesión para activar la protección."
                stopSelf()
                return
            }
            val config = ProtectionConfig(this, uid)
            if (!config.hasPin || !ProtectionConfig.validNumber(config.contactNumber)) {
                stopMessage = "Configura el contacto y el PIN antes de activar."
                stopSelf()
                return
            }
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                stopMessage = "Concede el permiso de llamada para activar la protección."
                stopSelf()
                return
            }
        }
        val notification = protectionNotification("Protección activa")
        testMode = test
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (sensors.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_GAME).not()) {
            stopMessage = "No se pudo iniciar el acelerómetro en este teléfono."
            stopSelf()
            return
        }
        proximitySensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        ProtectionState.snapshot = ProtectionSnapshot(active = true, phase = "Vigilando", testMode = test)
        handler.post(pulse)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (alert != null) return
        val now = SystemClock.elapsedRealtime()
        val trigger = when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> engine.onProximity(event.values[0] < event.sensor.maximumRange, now)
            Sensor.TYPE_LIGHT -> engine.onLight(event.values[0], now)
            Sensor.TYPE_ACCELEROMETER -> engine.onAcceleration(event.values[0], event.values[1], event.values[2], now)
            else -> null
        }
        if (trigger != null) triggerAlert(trigger, now)
        if (now - lastPublish >= 200L) publish(now)
    }

    private fun triggerAlert(kind: DetectionEngine.Trigger, now: Long) {
        alert = kind
        alertDeadline = now + 7_000L
        contacting = false
        startSiren()
        notifications.notify(ALERT_NOTIFICATION_ID, alertNotification(kind))
        publish(now)
    }

    private fun sendAlert() {
        if (testMode) {
            publishResult("Alerta comprobada sin realizar una llamada.")
            return
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            publishResult("No se avisó: la sesión terminó.")
            return
        }
        val config = ProtectionConfig(this, uid)
        val contact = config.contactNumber
        if (!ProtectionConfig.validNumber(contact)) {
            publishResult("No se avisó: el número configurado no es válido.")
            return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callResult = try {
                stopSiren()
                val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
                telecom.placeCall(Uri.fromParts("tel", ProtectionConfig.dialNumber(contact), null), Bundle())
                "Se solicitó la llamada a ${config.contactName.ifBlank { "tu contacto" }}."
            } catch (exception: Exception) {
                "No se pudo iniciar la llamada. Revisa la SIM y la cobertura."
            }
            publishResult(callResult)
        } else {
            publishResult("No se pudo llamar: falta el permiso de teléfono.")
        }
    }

    private fun publishResult(result: String) {
        ProtectionState.snapshot = ProtectionState.snapshot.copy(lastResult = result)
    }

    private fun dismissAlert() {
        val reason = when (alert) {
            DetectionEngine.Trigger.EXTRACTION -> "extracción del bolsillo"
            DetectionEngine.Trigger.SNATCH -> "posible arrebato"
            DetectionEngine.Trigger.SOS -> "SOS por tres sacudidas"
            null -> "alerta"
        }
        stopSiren()
        notifications.cancel(ALERT_NOTIFICATION_ID)
        alert = null
        alertDeadline = 0L
        contacting = false
        engine.reset()
        ProtectionState.snapshot = ProtectionState.snapshot.copy(alert = null, lastResult = "Se canceló la alerta por $reason.")
    }

    private fun publish(now: Long) {
        lastPublish = now
        val previous = ProtectionState.snapshot
        val phase = when {
            alert == DetectionEngine.Trigger.EXTRACTION -> "Alarma por extracción"
            alert == DetectionEngine.Trigger.SNATCH -> "Posible arrebato"
            alert == DetectionEngine.Trigger.SOS -> "SOS por gesto"
            engine.shakeCount > 0 -> "Gesto SOS · ${engine.shakeCount}/3"
            engine.pocketArmed -> "Guardado y protegido"
            engine.near && (engine.lux ?: Float.MAX_VALUE) <= 10f -> "Confirmando bolsillo"
            else -> "Expuesto · vigilando gesto"
        }
        ProtectionState.snapshot = previous.copy(
            active = true,
            phase = phase,
            proximity = proximitySensor?.let { engine.near },
            lux = if (lightSensor == null) null else engine.lux,
            motion = engine.motion,
            alert = alert,
            secondsUntilContact = if (alert != null && !contacting) ((alertDeadline - now).coerceAtLeast(0L) / 1000L + 1L).toInt() else 0,
            testMode = testMode,
        )
    }

    private fun startSiren() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            previousAlarmVolume = runCatching { audio.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrNull()
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            } catch (exception: RuntimeException) {
                publishResult("Android no permitió subir el volumen de alarma. Revisa No molestar.")
            }
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (alarmUri != null) {
                val player = MediaPlayer()
                try {
                    player.apply {
                        setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                        setDataSource(this@ProtectionService, alarmUri)
                        isLooping = true
                        prepare()
                        start()
                    }
                    systemAlarm = player
                } catch (exception: Exception) {
                    player.release()
                }
            }
            if (systemAlarm == null) {
                tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                handler.post(sirenPulse)
            }
        } catch (exception: RuntimeException) {
            publishResult("No se pudo iniciar la sirena; la vibración sigue activa.")
        }
        @Suppress("DEPRECATION")
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 250), 0))
    }

    private fun stopSiren() {
        handler.removeCallbacks(sirenPulse)
        systemAlarm?.release()
        systemAlarm = null
        tone?.stopTone()
        tone?.release()
        tone = null
        vibrator?.cancel()
        vibrator = null
        previousAlarmVolume?.let { old ->
            runCatching { (getSystemService(AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, old, 0) }
        }
        previousAlarmVolume = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handler.removeCallbacks(pulse)
        sensors.unregisterListener(this)
        stopSiren()
        notifications.cancel(ALERT_NOTIFICATION_ID)
        ProtectionState.snapshot = ProtectionSnapshot(lastResult = stopMessage)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL_PROTECTION, "Protección activa", NotificationManager.IMPORTANCE_LOW))
        val alerts = NotificationChannel(CHANNEL_ALERT, "Alertas", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        notifications.createNotificationChannel(alerts)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun protectionNotification(message: String): Notification =
        Notification.Builder(this, CHANNEL_PROTECTION)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("AlertCusco · protección activa")
            .setContentText(message)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()

    private fun alertNotification(kind: DetectionEngine.Trigger): Notification {
        val message = when (kind) {
            DetectionEngine.Trigger.EXTRACTION -> "Posible extracción del celular"
            DetectionEngine.Trigger.SNATCH -> "Posible arrebato del celular"
            DetectionEngine.Trigger.SOS -> "SOS activado por gesto"
        }
        return Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("AlertCusco · alerta activada")
            .setContentText("$message. Abre la app para cancelar.")
            .setContentIntent(openAppIntent())
            .setAutoCancel(false)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.evaluacin_consolidado2.START"
        const val ACTION_STOP = "com.example.evaluacin_consolidado2.STOP"
        const val ACTION_DISMISS = "com.example.evaluacin_consolidado2.DISMISS"
        private const val EXTRA_TEST_MODE = "test_mode"
        private const val CHANNEL_PROTECTION = "protection"
        private const val CHANNEL_ALERT = "alerts_silent_v2"
        private const val NOTIFICATION_ID = 101
        private const val ALERT_NOTIFICATION_ID = 102

        fun command(context: Context, action: String, testMode: Boolean = false) {
            val intent = Intent(context, ProtectionService::class.java).setAction(action).putExtra(EXTRA_TEST_MODE, testMode)
            if (action == ACTION_START && Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
