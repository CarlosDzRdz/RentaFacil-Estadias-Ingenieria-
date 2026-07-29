package com.utch.rentafacil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheet.Builder
import com.stripe.android.paymentsheet.PaymentSheetResult

class PagoFragment : Fragment() {

    private lateinit var paymentSheet: PaymentSheet
    private lateinit var functions: FirebaseFunctions

    // Almacena el monto en centavos requerido por la API de Stripe
    private var montoParaStripe: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización del SDK de Stripe con tu clave pública de pruebas
        PaymentConfiguration.init(requireContext(), "pk_test_51TqGAICZopSxCgM1uMRkrsN4wM0Ho2QxwZxvtz5sSVCb5PPV5lTMp2wboB8MvT51JzCpLMOQTmidVt7e0DGvVphx00IyL4b7C8")
        paymentSheet = Builder(::onPaymentSheetResult).build(this)
        functions = FirebaseFunctions.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pago, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnProcesarPago = view.findViewById<MaterialButton>(R.id.btnProcesarPago)
        val progressPago = view.findViewById<ProgressBar>(R.id.progressPago)
        val txtTipoContrato = view.findViewById<TextView>(R.id.txtTipoContrato)
        val txtMontoTotal = view.findViewById<TextView>(R.id.txtMontoTotal)

        // Deshabilitar acción de pago durante la carga de datos
        btnProcesarPago.isEnabled = false

        // Consultar información financiera del usuario en Firestore
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = FirebaseFirestore.getInstance()

            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { documento ->
                    if (documento.exists()) {
                        val tipoContrato = documento.getString("tipo_contrato") ?: "No definido"
                        val montoRenta = documento.getDouble("monto_renta") ?: 0.0

                        // Actualizar UI con datos obtenidos
                        txtTipoContrato.text = tipoContrato
                        txtMontoTotal.text = String.format("$%,.2f MXN", montoRenta)

                        // Convertir a centavos
                        montoParaStripe = (montoRenta * 100).toInt()
                        btnProcesarPago.isEnabled = true
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
        }

        btnProcesarPago.setOnClickListener {
            if (montoParaStripe > 0) {
                progressPago.visibility = View.VISIBLE
                btnProcesarPago.isEnabled = false
                pedirPermisoDeCobro(btnProcesarPago, progressPago)
            } else {
                Toast.makeText(requireContext(), "Monto inválido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pedirPermisoDeCobro(btn: MaterialButton, progress: ProgressBar) {
        val datos = hashMapOf("monto" to montoParaStripe)

        // Generar PaymentIntent en Cloud Functions
        functions
            .getHttpsCallable("crearIntencionDePago")
            .call(datos)
            .addOnSuccessListener { result ->
                progress.visibility = View.GONE
                btn.isEnabled = true

                val datosRespuesta = result.data as HashMap<*, *>
                val clientSecret = datosRespuesta["clientSecret"] as String

                // Configurar Google Pay para entorno de pruebas
                val googlePayConfig = PaymentSheet.GooglePayConfiguration(
                    environment = PaymentSheet.GooglePayConfiguration.Environment.Test,
                    countryCode = "MX",
                    currencyCode = "MXN"
                )

                // Forzar recolección de nombre y correo para habilitar OXXO.
                // Esto genera campos vacíos con texto difuminado (placeholders nativos).
                val billingCollection = PaymentSheet.BillingDetailsCollectionConfiguration(
                    name = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Always,
                    email = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Always
                )

                // Ensamblar configuración final de PaymentSheet
                val configuration = PaymentSheet.Configuration(
                    merchantDisplayName = "Georgina Manuela Pacheco",
                    googlePay = googlePayConfig,
                    billingDetailsCollectionConfiguration = billingCollection,
                    allowsDelayedPaymentMethods = true
                )

                // Desplegar pasarela de pagos
                paymentSheet.presentWithPaymentIntent(clientSecret, configuration)
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                btn.isEnabled = true
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        // Manejar el resultado de la transacción
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                Toast.makeText(requireContext(), "Pago cancelado", Toast.LENGTH_SHORT).show()
                // El usuario cerró la hoja de pago sin confirmar nada: intentamos
                // liberar el bloqueo de inmediato en vez de esperar un webhook que,
                // si no llegó a generarse un vale de OXXO, nunca va a llegar.
                cancelarPagoPendiente()
            }
            is PaymentSheetResult.Failed -> {
                Toast.makeText(requireContext(), "Error: ${paymentSheetResult.error.message}", Toast.LENGTH_LONG).show()
            }
            is PaymentSheetResult.Completed -> {
                Toast.makeText(requireContext(), "Pago procesado exitosamente", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cancelarPagoPendiente() {
        functions
            .getHttpsCallable("cancelarPagoPendiente")
            .call()
            .addOnFailureListener {
                // Si ya se generó un vale de OXXO, Stripe rechaza la cancelación
                // y el pago se queda "en tránsito" hasta que se resuelva solo.
            }
    }
}