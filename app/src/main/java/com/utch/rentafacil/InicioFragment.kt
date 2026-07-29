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
import com.google.firebase.functions.FirebaseFunctions
import androidx.core.graphics.toColorInt
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class InicioFragment : Fragment() {

    // Se guarda para poder quitarlo en onDestroyView y no dejarlo escuchando de más
    private var listenerEstadoCuenta: com.google.firebase.firestore.ListenerRegistration? = null

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
        val btnPagarRenta = view.findViewById<MaterialButton>(R.id.btnPagarRenta)
        val btnCancelarPago = view.findViewById<MaterialButton>(R.id.btnCancelarPago)

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

        // 2. NAVEGACIÓN HACIA LA PASARELA DE STRIPE
        btnPagarRenta.setOnClickListener {
            // Le pedimos a la Actividad principal que lance el fragmento de pago en la capa superior
            (requireActivity() as MainActivity).replaceFragment(PagoFragment())
        }

        // Respaldo manual: por si la app se cerró o se perdió la conexión antes de
        // que el cierre normal de la hoja de pago pudiera limpiar el bloqueo solo.
        btnCancelarPago.setOnClickListener {
            btnCancelarPago.isEnabled = false
            FirebaseFunctions.getInstance()
                .getHttpsCallable("cancelarPagoPendiente")
                .call()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Pago pendiente cancelado", Toast.LENGTH_SHORT).show()
                    // El listener en tiempo real se encarga de refrescar la pantalla sola
                }
                .addOnFailureListener { error ->
                    btnCancelarPago.isEnabled = true
                    Toast.makeText(requireContext(), error.message ?: "No se pudo cancelar el pago", Toast.LENGTH_LONG).show()
                }
        }

        // Validación de sesión activa.
        // Usamos un listener en tiempo real (no una lectura única) para que la
        // tarjeta se actualice sola en cuanto cambie algo en Firestore —
        // por ejemplo, justo después de que un pago se confirme o se cancele —
        // sin que el usuario tenga que cerrar y volver a abrir la app.
        if (uid != null) {
            listenerEstadoCuenta = db.collection("usuarios").document(uid)
                .addSnapshotListener { documento, error ->
                    if (error != null) {
                        Toast.makeText(requireContext(), "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (documento != null && documento.exists()) {

                        // Extracción de datos básicos del usuario
                        val nombreBD = documento.getString("nombre(s)") ?: "Usuario"
                        val urlFoto = documento.getString("fotoUrl") ?: ""
                        val estadoPago = documento.getString("estado_pago") ?: "pendiente"
                        val montoRenta = documento.getLong("monto_renta") ?: 0

                        // Extracción de fechas nativas (Timestamp) de Firestore
                        val timestampVencimiento = documento.getTimestamp("fecha_vencimiento")
                        val timestampPago = documento.getTimestamp("fecha_ultimo_pago")

                        // Formateo de fechas para su visualización en la interfaz (DD/MM/AAAA)
                        val formatoFecha = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        val fechaVencimientoBD = if (timestampVencimiento != null) formatoFecha.format(timestampVencimiento.toDate()) else "--/--/----"
                        val fechaPagoBD = if (timestampPago != null) formatoFecha.format(timestampPago.toDate()) else "No registrado"

                        // Asignación de datos al encabezado del perfil
                        txtSaludo.text = "Hola, $nombreBD"
                        if (urlFoto.isNotEmpty() && isAdded) {
                            Glide.with(this).load(urlFoto).circleCrop().into(imgPerfil)
                        }

                        // Lógica de renderizado dinámico para la tarjeta de Estado de Cuenta.
                        // No confiamos únicamente en "estado_pago": calculamos el estado real
                        // comparando fecha_vencimiento contra la fecha de hoy del teléfono.
                        val hoy = java.util.Date()
                        val vencimiento = timestampVencimiento?.toDate()
                        val diasDeAvisoMs = 5L * 24 * 60 * 60 * 1000
                        val inicioVentanaAviso = vencimiento?.let { java.util.Date(it.time - diasDeAvisoMs) }

                        when {
                            estadoPago.lowercase() == "en_transito" -> {
                                txtPrincipal.text = "Procesando pago..."
                                txtPrincipal.setTextColor("#2196F3".toColorInt())
                                txtSecundario.text = "Esto puede tardar unas horas"
                                actualizarEstiloBoton(btnPagarRenta, bloqueado = true)
                                btnCancelarPago.visibility = View.VISIBLE
                                btnCancelarPago.isEnabled = true
                            }
                            vencimiento == null -> {
                                // Respaldo si todavía no hay fecha_vencimiento registrada
                                txtPrincipal.text = "$${montoRenta}"
                                txtPrincipal.setTextColor("#F44336".toColorInt())
                                txtSecundario.text = "Último pago registrado: $fechaPagoBD"
                                actualizarEstiloBoton(btnPagarRenta, bloqueado = false)
                                btnCancelarPago.visibility = View.GONE
                            }
                            hoy.before(inicioVentanaAviso) -> {
                                txtPrincipal.text = "¡Mes Pagado!"
                                txtPrincipal.setTextColor("#4CAF50".toColorInt())
                                txtSecundario.text = "Tu próximo vencimiento es el: $fechaVencimientoBD"
                                actualizarEstiloBoton(btnPagarRenta, bloqueado = true)
                                btnCancelarPago.visibility = View.GONE
                            }
                            !hoy.after(vencimiento) -> {
                                txtPrincipal.text = "$${montoRenta}"
                                txtPrincipal.setTextColor("#FF9800".toColorInt())
                                txtSecundario.text = "Tu fecha de corte se acerca: $fechaVencimientoBD"
                                actualizarEstiloBoton(btnPagarRenta, bloqueado = false)
                                btnCancelarPago.visibility = View.GONE
                            }
                            else -> {
                                txtPrincipal.text = "$${montoRenta}"
                                txtPrincipal.setTextColor("#F44336".toColorInt())
                                txtSecundario.text = "Pago pendiente. Último pago: $fechaPagoBD"
                                actualizarEstiloBoton(btnPagarRenta, bloqueado = false)
                                btnCancelarPago.visibility = View.GONE
                            }
                        }
                    }
                }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Evita que el listener se quede escuchando después de que la vista ya no existe
        listenerEstadoCuenta?.remove()
        listenerEstadoCuenta = null
    }

    // Da al botón una apariencia visualmente distinta cuando está bloqueado
    // (fondo blanco, letras y borde azules) en vez de solo desactivarlo.
    private fun actualizarEstiloBoton(boton: MaterialButton, bloqueado: Boolean) {
        boton.isEnabled = !bloqueado
        val azul = "#1976D2".toColorInt()
        if (bloqueado) {
            boton.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            boton.setTextColor(azul)
            boton.strokeColor = android.content.res.ColorStateList.valueOf(azul)
            boton.strokeWidth = 4
        } else {
            boton.backgroundTintList = android.content.res.ColorStateList.valueOf(azul)
            boton.setTextColor(android.graphics.Color.WHITE)
            boton.strokeWidth = 0
        }
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