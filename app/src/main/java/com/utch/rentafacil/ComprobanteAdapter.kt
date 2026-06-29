package com.utch.rentafacil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

// 1. Definimos una pequeña estructura de datos
data class Comprobante(
    val urlImagen: String = "",
    val fechaSubida: Timestamp? = null
)

// 2. Creamos el adaptador
class ComprobanteAdapter(private val listaComprobantes: List<Comprobante>) :
    RecyclerView.Adapter<ComprobanteAdapter.ComprobanteViewHolder>() {

    class ComprobanteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgRecibo: ImageView = itemView.findViewById(R.id.imgReciboHistorial)
        val txtMes: TextView = itemView.findViewById(R.id.txtMesRecibo)
        val txtFecha: TextView = itemView.findViewById(R.id.txtFechaRecibo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComprobanteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comprobante, parent, false)
        return ComprobanteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ComprobanteViewHolder, position: Int) {
        val comprobante = listaComprobantes[position]

        // Cargar imagen con Glide
        Glide.with(holder.itemView.context)
            .load(comprobante.urlImagen)
            .centerCrop()
            .into(holder.imgRecibo)

        // Formateo de fechas
        if (comprobante.fechaSubida != null) {
            val fechaDate = comprobante.fechaSubida.toDate()

            // Extraer solo el nombre del mes en español (ej. "junio")
            val formatoMes = SimpleDateFormat("MMMM", Locale("es", "MX"))
            val nombreMes = formatoMes.format(fechaDate).replaceFirstChar { it.uppercase() }

            // Extraer la fecha exacta (ej. "22/06/2026")
            val formatoExacto = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val fechaExacta = formatoExacto.format(fechaDate)

            // Asignar los textos a la vista
            holder.txtMes.text = "Comprobante de $nombreMes"
            holder.txtFecha.text = "Subido el: $fechaExacta"
        } else {
            holder.txtMes.text = "Comprobante reciente"
            holder.txtFecha.text = "Fecha no disponible"
        }
    }

    override fun getItemCount(): Int {
        return listaComprobantes.size
    }
}