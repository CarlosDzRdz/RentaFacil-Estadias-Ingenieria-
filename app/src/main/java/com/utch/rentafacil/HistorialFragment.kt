package com.utch.rentafacil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistorialFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_historial, container, false)

        val recyclerHistorial = view.findViewById<RecyclerView>(R.id.recyclerHistorial)

        // Configuramos la lista para que se deslice verticalmente
        recyclerHistorial.layoutManager = LinearLayoutManager(requireContext())

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()

        if (uid != null) {
            // Consulta poderosa: Ordenar por fecha descendente (más nuevos arriba) y limitar a 3
            db.collection("usuarios").document(uid).collection("comprobantes")
                .orderBy("fecha_subida", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .addOnSuccessListener { documentos ->
                    val listaRecibos = mutableListOf<Comprobante>()

                    for (doc in documentos) {
                        val url = doc.getString("url_almacenamiento") ?: ""
                        val fecha = doc.getTimestamp("fecha_subida")

                        // Añadimos cada recibo encontrado a nuestra lista en memoria
                        listaRecibos.add(Comprobante(url, fecha))
                    }

                    // Conectamos la información con la interfaz usando nuestro Adaptador
                    val adaptador = ComprobanteAdapter(listaRecibos)
                    recyclerHistorial.adapter = adaptador
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al cargar el historial", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }
}