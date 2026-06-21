package com.utch.rentafacil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PerfilFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_perfil, container, false)

        // Vinculación de los elementos visuales
        val imgPerfil = view.findViewById<ShapeableImageView>(R.id.imgPerfilDetalle)
        val txtNombreCorto = view.findViewById<TextView>(R.id.txtPerfilNombre)
        val txtCorreo = view.findViewById<TextView>(R.id.txtPerfilCorreo)

        // Elementos de la parte inferior (Detalles)
        val txtNombreCompleto = view.findViewById<TextView>(R.id.txtPerfilNombreCompleto)
        val txtDepartamento = view.findViewById<TextView>(R.id.txtPerfilDepartamento)
        val txtContrato = view.findViewById<TextView>(R.id.txtPerfilContrato)

        val btnCerrarSesion = view.findViewById<Button>(R.id.btnCerrarSesion)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid

        // Configuración del botón Cerrar Sesión
        btnCerrarSesion.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java) // Cambia a tu Activity de Login si se llama distinto
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Recuperación de datos
        if (uid != null) {
            txtCorreo.text = auth.currentUser?.email ?: "Sin correo registrado"

            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { documento ->
                    if (documento.exists()) {

                        // 1. Extraemos los campos crudos de Firestore
                        val nombresBD = documento.getString("nombre(s)") ?: ""
                        val apellidosBD = documento.getString("apellido(s)") ?: ""
                        val departamentoBD = documento.getString("departamento") ?: "No asignado"
                        val contratoBD = documento.getString("tipo_contrato") ?: "No asignado"
                        val urlFoto = documento.getString("fotoUrl") ?: ""

                        // 2. Lógica para el Nombre Completo (Inferior)
                        val nombreCompleto = "$nombresBD $apellidosBD".trim()

                        // 3. Lógica para el Nombre Corto del Encabezado (Ej. "Juan Perez")
                        // Dividimos por espacios y tomamos el primer elemento
                        val primerNombre = nombresBD.split(" ").firstOrNull() ?: "Usuario"
                        val primerApellido = apellidosBD.split(" ").firstOrNull() ?: ""
                        val nombreCorto = "$primerNombre $primerApellido".trim()

                        // 4. Asignamos los textos a la vista
                        txtNombreCorto.text = nombreCorto
                        txtNombreCompleto.text = nombreCompleto.ifEmpty { "Usuario" }
                        txtDepartamento.text = departamentoBD
                        txtContrato.text = contratoBD

                        // 5. Cargamos la foto con Glide
                        if (urlFoto.isNotEmpty() && isAdded) {
                            Glide.with(this).load(urlFoto).circleCrop().into(imgPerfil)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al cargar los datos", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }
}