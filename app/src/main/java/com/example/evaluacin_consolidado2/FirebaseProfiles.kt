package com.example.evaluacin_consolidado2

import com.google.firebase.database.FirebaseDatabase

internal data class PersonProfile(
    val name: String,
    val phone: String,
    val contactName: String = "",
    val contactNumber: String = "",
)

internal object FirebaseProfiles {
    private const val DATABASE_URL = "https://alertacusco-default-rtdb.firebaseio.com/"

    private val database by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL).apply { setPersistenceEnabled(true) }
    }

    fun create(uid: String, profile: PersonProfile, result: (Boolean) -> Unit) {
        database.getReference("users").child(uid).setValue(
            mapOf("name" to profile.name, "phone" to profile.phone),
        ).addOnCompleteListener { result(it.isSuccessful) }
    }

    fun load(uid: String, result: (PersonProfile?) -> Unit) {
        database.getReference("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.child("name").getValue(String::class.java).orEmpty()
                val phone = snapshot.child("phone").getValue(String::class.java).orEmpty()
                result(if (name.isBlank() || phone.isBlank()) null else PersonProfile(
                    name = name,
                    phone = phone,
                    contactName = snapshot.child("contactName").getValue(String::class.java).orEmpty(),
                    contactNumber = snapshot.child("contactNumber").getValue(String::class.java).orEmpty(),
                ))
            }
            .addOnFailureListener { result(null) }
    }

    fun saveContact(uid: String, name: String, number: String, result: (Boolean) -> Unit) {
        database.getReference("users").child(uid).updateChildren(
            mapOf("contactName" to name, "contactNumber" to number),
        ).addOnCompleteListener { result(it.isSuccessful) }
    }
}
