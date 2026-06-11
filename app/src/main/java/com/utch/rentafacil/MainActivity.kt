package com.utch.rentafacil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Verificamos si viene del Login
        val vieneDelLogin = intent.getBooleanExtra("LOGIN_EXITOSO", false)

        if (!vieneDelLogin) {
            // Si no viene del Login, lo mandamos para allá
            val intentLogin = Intent(this, LoginActivity::class.java)
            startActivity(intentLogin)
            finish()
            return
        }

        // 2. Si el login fue exitoso, cargamos el marco vacío
        setContentView(R.layout.activity_main)

        // 3. Inyectamos el Fragmento inicial solo la primera vez que se crea la pantalla
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedorRuteo, InicioFragment())
                .commit()
        }
    }
}