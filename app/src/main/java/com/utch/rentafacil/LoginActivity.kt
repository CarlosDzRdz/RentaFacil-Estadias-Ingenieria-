package com.utch.rentafacil

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    // Declaramos la variable de autenticación de Firebase
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializamos la instancia de Firebase Auth
        auth = FirebaseAuth.getInstance()

        val etCorreo = findViewById<TextInputEditText>(R.id.etCorreo)
        val etContrasena = findViewById<TextInputEditText>(R.id.etContrasena)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)

        btnEntrar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val contrasena = etContrasena.text.toString().trim()

            // Validamos que el usuario no deje los campos en blanco
            if (correo.isNotEmpty() && contrasena.isNotEmpty()) {

                // Le preguntamos a Firebase si las credenciales existen y son correctas
                auth.signInWithEmailAndPassword(correo, contrasena)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // ¡Éxito! Firebase confirmó los datos.
                            val intent = Intent(this, MainActivity::class.java)
                            intent.putExtra("LOGIN_EXITOSO", true)
                            startActivity(intent)
                            finish()
                        } else {
                            // Firebase rechazó los datos (contraseña mal, no existe el correo, etc.)
                            Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Por favor, ingresa tu correo y contraseña", Toast.LENGTH_SHORT).show()
            }
        }
    }
}