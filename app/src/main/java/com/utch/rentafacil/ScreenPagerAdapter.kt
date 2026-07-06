package com.utch.rentafacil

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ScreenPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    // Define la cantidad total de pestañas/fragmentos en el menú inferior
    override fun getItemCount(): Int = 3

    // Asigna el fragmento correspondiente a cada posición del deslizamiento
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> InicioFragment()
            1 -> HistorialFragment()
            2 -> PerfilFragment()
            else -> InicioFragment()
        }
    }
}