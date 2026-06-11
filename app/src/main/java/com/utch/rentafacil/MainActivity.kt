package com.utch.rentafacil

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n") // <-- Esta línea silencia la advertencia amarilla
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Atrapamos el pase VIP que nos manda el LoginActivity
        val vieneDelLogin = intent.getBooleanExtra("LOGIN_EXITOSO", false)

        if (!vieneDelLogin) {
            // Si el usuario acaba de abrir la app, lo mandamos al Login
            val intentLogin = Intent(this, LoginActivity::class.java)
            startActivity(intentLogin)
            finish()
            return
        }

        // Si llega a esta línea, es porque ingresó "admin" y "1234" correctamente
        setContentView(R.layout.activity_main)

        val tvHolaMundo = findViewById<TextView>(R.id.tvHolaMundo)
        // Puedes concatenar variables o poner el texto directo sin que te marque advertencia
        tvHolaMundo.text = "¡Hola Mundo! Has iniciado sesión exitosamente con clave fija."
    }
}