package com.utch.rentafacil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Buscamos si hay una sesión activa guardada en el teléfono
        val usuarioActual = FirebaseAuth.getInstance().currentUser

        if (usuarioActual == null) {
            // No hay sesión. Lo mandamos al Login y destruimos el Main.
            val intentLogin = Intent(this, LoginActivity::class.java)
            startActivity(intentLogin)
            finish()
            return
        }
        // --------------------------------------------------

        // Si pasó el filtro, cargamos la arquitectura Single Activity
        setContentView(R.layout.activity_main)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            replaceFragment(InicioFragment())
            bottomNavigation.selectedItemId = R.id.nav_inicio
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> {
                    replaceFragment(InicioFragment())
                    true
                }

                R.id.nav_historial -> {
                    replaceFragment(HistorialFragment())
                    true
                }
                R.id.nav_perfil -> {
                    replaceFragment(PerfilFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}