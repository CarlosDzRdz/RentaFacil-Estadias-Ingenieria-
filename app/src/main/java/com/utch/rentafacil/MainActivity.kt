package com.utch.rentafacil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Fuerza a la aplicación a utilizar exclusivamente el modo claro
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 2. Buscamos si hay una sesión activa guardada en el teléfono
        val usuarioActual = FirebaseAuth.getInstance().currentUser
        if (usuarioActual == null) {
            val intentLogin = Intent(this, LoginActivity::class.java)
            startActivity(intentLogin)
            finish()
            return
        }

        // 3. Pintamos la interfaz en la pantalla
        setContentView(R.layout.activity_main)

        // 4. Enlazamos las variables con el diseño XML (OJO: Aquí cambió el ID a viewPager_tabs)
        viewPager = findViewById(R.id.viewPager_tabs)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        // 5. Configurar el adaptador en el ViewPager2
        val adapter = ScreenPagerAdapter(this)
        viewPager.adapter = adapter

        // 6. Sincronización 1: Al deslizar con el dedo
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNavigation.menu.getItem(position).isChecked = true
            }
        })

        // 7. Sincronización 2: Al tocar el menú inferior
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> {
                    viewPager.currentItem = 0
                    true
                }
                R.id.nav_historial -> {
                    viewPager.currentItem = 1
                    true
                }
                R.id.nav_perfil -> {
                    viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }
    }

    // Lanza pantallas (como PagoFragment) hacia la Capa 2, por encima de tus pestañas.
    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            // Animación de aparición
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // Permite volver atrás con el botón físico del celular
            .commit()
    }
}