package com.utch.rentafacil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.graphics.toColorInt
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class InicioFragment : Fragment() {

    // Contrato moderno para abrir la galería y seleccionar una imagen
    private val seleccionarImagen = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            // Inicialización del proceso de subida con la ruta del archivo seleccionado
            subirComprobanteAStorage(uri)
        } else {
            Toast.makeText(requireContext(), "No se seleccionó ninguna imagen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflado del diseño base del fragmento
        val view = inflater.inflate(R.layout.fragment_inicio, container, false)

        // Vinculación de los elementos visuales de la interfaz
        val cardPerfil = view.findViewById<androidx.cardview.widget.CardView>(R.id.cardPerfil)
        val cardEstadoCuenta = view.findViewById<androidx.cardview.widget.CardView>(R.id.cardEstadoCuenta)
        val txtSaludo = view.findViewById<TextView>(R.id.txtSaludoUsuario)
        val imgPerfil = view.findViewById<ShapeableImageView>(R.id.imgPerfilUsuario)
        val txtPrincipal = view.findViewById<TextView>(R.id.txtPrincipalEstado)
        val txtSecundario = view.findViewById<TextView>(R.id.txtSecundarioFecha)
        val btnSubirComprobante = view.findViewById<MaterialButton>(R.id.btnSubirComprobante)

        // Inicialización de instancias de autenticación y base de datos
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()

        // Evento de escucha para redireccionar al fragmento de Perfil
        cardPerfil.setOnClickListener {
            activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_perfil
        }

        // Evento de escucha para redireccionar al fragmento del historial
        cardEstadoCuenta.setOnClickListener {
            activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_historial
        }

        // Configuración del botón para activar la selección de archivos de la galería
        btnSubirComprobante.setOnClickListener {
            seleccionarImagen.launch("image/*")
        }

        // Validación de sesión activa
        if (uid != null) {
            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { documento ->
                    if (documento.exists()) {

                        // Extracción de datos básicos del usuario
                        val nombreBD = documento.getString("nombre(s)") ?: "Usuario"
                        val urlFoto = documento.getString("fotoUrl") ?: ""
                        val estadoPago = documento.getString("estado_pago") ?: "pendiente"
                        val montoRenta = documento.getLong("monto_renta") ?: 0

                        // Extracción de fechas nativas (Timestamp) de Firestore
                        val timestampVencimiento = documento.getTimestamp("fecha_vencimiento")
                        val timestampPago = documento.getTimestamp("fecha_pago")

                        // Formateo de fechas para su visualización en la interfaz (DD/MM/AAAA)
                        val formatoFecha = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        val fechaVencimientoBD = if (timestampVencimiento != null) formatoFecha.format(timestampVencimiento.toDate()) else "--/--/----"
                        val fechaPagoBD = if (timestampPago != null) formatoFecha.format(timestampPago.toDate()) else "No registrado"

                        // Asignación de datos al encabezado del perfil
                        txtSaludo.text = "Hola, $nombreBD"
                        if (urlFoto.isNotEmpty() && isAdded) {
                            Glide.with(this).load(urlFoto).circleCrop().into(imgPerfil)
                        }

                        // Lógica de renderizado dinámico para la tarjeta de Estado de Cuenta
                        if (estadoPago.lowercase() == "pagado") {
                            txtPrincipal.text = "¡Mes Pagado!"
                            txtPrincipal.setTextColor("#4CAF50".toColorInt())
                            txtSecundario.text = "Tu próximo vencimiento es el: $fechaVencimientoBD"
                        } else {
                            txtPrincipal.text = "$${montoRenta}"
                            txtPrincipal.setTextColor("#F44336".toColorInt())
                            txtSecundario.text = "Último pago registrado: $fechaPagoBD"
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }

    // Transfiere el archivo seleccionado a la carpeta del usuario en Firebase Storage
    private fun subirComprobanteAStorage(uriImagen: android.net.Uri) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val timestamp = System.currentTimeMillis()

        Toast.makeText(requireContext(), "Subiendo comprobante, por favor espera...", Toast.LENGTH_LONG).show()

        // Definición de la ruta estructurada por el identificador del inquilino
        val rutaStorage = "usuarios/$uid/comprobantes/$timestamp.jpg"
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().getReference(rutaStorage)

        storageRef.putFile(uriImagen)
            .addOnSuccessListener {
                // Recuperación del enlace de descarga al concluir la transferencia física
                storageRef.downloadUrl.addOnSuccessListener { uriDescarga ->
                    registrarComprobanteEnFirestore(uriDescarga.toString(), rutaStorage)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al subir la imagen", Toast.LENGTH_SHORT).show()
            }
    }

    // Asigna el enlace de la imagen dentro de la subcolección cronológica del usuario
    private fun registrarComprobanteEnFirestore(urlDescarga: String, rutaStorage: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Estructura de metadatos del recibo
        val datosComprobante = hashMapOf(
            "url_almacenamiento" to urlDescarga,
            "ruta_storage" to rutaStorage,
            "fecha_subida" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // Inserción en la subcolección "comprobantes"
        db.collection("usuarios").document(uid).collection("comprobantes")
            .add(datosComprobante)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "¡Comprobante subido y registrado con éxito!", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al registrar en la base de datos", Toast.LENGTH_SHORT).show()
            }
    }
}