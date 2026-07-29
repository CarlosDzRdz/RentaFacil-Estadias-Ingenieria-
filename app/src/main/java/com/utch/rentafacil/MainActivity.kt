package com.utch.rentafacil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView

    // Si el usuario niega el permiso, simplemente no le llegarán notificaciones;
    // no hay nada más que reaccionar aquí.
    private val solicitarPermisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        // 2.5. Notificaciones push: pedimos permiso (Android 13+) y guardamos el token del dispositivo
        pedirPermisoNotificaciones()
        guardarTokenFcm()

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
            // Si hay una pantalla superpuesta (ej. PagoFragment) en fragment_container,
            // la quitamos primero para que vuelva a verse el ViewPager2 de abajo.
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }

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

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val yaConcedido = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!yaConcedido) {
                solicitarPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Guarda el token actual de FCM en el expediente del usuario para que la
    // Cloud Function programada sepa a qué dispositivo mandarle los recordatorios.
    private fun guardarTokenFcm() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .update("fcm_token", token)
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