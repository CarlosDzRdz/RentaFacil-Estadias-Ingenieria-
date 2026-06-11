package com.utch.rentafacil

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etCorreo = findViewById<TextInputEditText>(R.id.etCorreo)
        val etContrasena = findViewById<TextInputEditText>(R.id.etContrasena)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)

        btnEntrar.setOnClickListener {
            val usuario = etCorreo.text.toString().trim()
            val contrasena = etContrasena.text.toString().trim()

            // Validación de claves fijas temporales
            if (usuario == "admin" && contrasena == "1234") {
                // ¡Éxito! Lo mandamos al Main con un "pase VIP"
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("LOGIN_EXITOSO", true) // Le avisamos al Main que sí pasó
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}