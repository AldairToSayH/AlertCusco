package com.example.evaluacin_consolidado2

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal class ProtectionConfig(context: Context, userId: String) {
    private val preferences = context.getSharedPreferences("protection_$userId", Context.MODE_PRIVATE)

    var ownerName: String
        get() = preferences.getString("owner_name", "") ?: ""
        set(value) = preferences.edit().putString("owner_name", value.trim()).apply()

    var ownerNumber: String
        get() = preferences.getString("owner_number", "") ?: ""
        set(value) = preferences.edit().putString("owner_number", value.trim()).apply()

    var contactName: String
        get() = preferences.getString("contact_name", "") ?: ""
        set(value) = preferences.edit().putString("contact_name", value.trim()).apply()

    var contactNumber: String
        get() = preferences.getString("contact_number", "") ?: ""
        set(value) = preferences.edit().putString("contact_number", value.trim()).apply()

    val hasPin: Boolean get() = preferences.contains("pin_hash")

    fun setPin(pin: String) {
        require(pin.matches(Regex("[0-9]{4}")))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        preferences.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = preferences.getString("pin_salt", null) ?: return false
        val stored = preferences.getString("pin_hash", null) ?: return false
        val actual = hash(pin, Base64.decode(salt, Base64.NO_WRAP))
        return MessageDigest.isEqual(stored.toByteArray(), actual.toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return Base64.encodeToString(digest.digest(pin.toByteArray()), Base64.NO_WRAP)
    }

    companion object {
        fun localNumber(value: String): String = value.removePrefix("+51")

        fun validNumber(value: String) = localNumber(value).matches(Regex("9[0-9]{8}"))

        fun dialNumber(value: String): String = "+51${localNumber(value)}"
    }
}
