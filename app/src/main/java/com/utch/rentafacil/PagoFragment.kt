package com.utch.rentafacil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.functions.FirebaseFunctions
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheet.Builder
import com.stripe.android.paymentsheet.PaymentSheetResult

class PagoFragment : Fragment() {

    private lateinit var paymentSheet: PaymentSheet
    private lateinit var functions: FirebaseFunctions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INICIALIZAR STRIPE
        PaymentConfiguration.init(requireContext(), "pk_test_51TnUasAff8ArF2JZmUV2EKb8tUeqZSzeHhDNHN50XGoYznTgKQauqXDXhZJd3YnOyoUu273qZdvGM1YfR3slVswJ00wshhN45b")

        // 2. Preparar la "Hoja de Pago" utilizando tu método actualizado
        paymentSheet = Builder(::onPaymentSheetResult).build(this)

        // 3. Conectar con nuestro servidor de Firebase
        functions = FirebaseFunctions.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pago, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnProcesarPago = view.findViewById<MaterialButton>(R.id.btnProcesarPago)
        val progressPago = view.findViewById<ProgressBar>(R.id.progressPago)

        btnProcesarPago.setOnClickListener {
            // Mostramos que está cargando y desactivamos el botón temporalmente
            progressPago.visibility = View.VISIBLE
            btnProcesarPago.isEnabled = false

            pedirPermisoDeCobro(btnProcesarPago, progressPago)
        }
    }

    private fun pedirPermisoDeCobro(btn: MaterialButton, progress: ProgressBar) {
        // MÉTODO SEGURO: Ya no mandamos el UID. Firebase inyecta el token de sesión automáticamente.
        val datos = hashMapOf(
            "monto" to 450000
        )

        functions
            .getHttpsCallable("crearIntencionDePago")
            .call(datos)
            .addOnSuccessListener { result ->
                progress.visibility = View.GONE
                btn.isEnabled = true

                // Extraemos el secreto con la corrección de HashMap que hiciste
                val datosRespuesta = result.data as HashMap<*, *>
                val clientSecret = datosRespuesta["clientSecret"] as String

                // ¡Abrimos la pantalla bonita de Stripe!
                val configuration = PaymentSheet.Configuration("Georgina Manuela Pacheco")
                paymentSheet.presentWithPaymentIntent(clientSecret, configuration)
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                btn.isEnabled = true
                Toast.makeText(requireContext(), "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                Toast.makeText(requireContext(), "Cancelaste el pago", Toast.LENGTH_SHORT).show()
            }
            is PaymentSheetResult.Failed -> {
                Toast.makeText(requireContext(), "Error en la tarjeta: ${paymentSheetResult.error.message}", Toast.LENGTH_LONG).show()
            }
            is PaymentSheetResult.Completed -> {
                Toast.makeText(requireContext(), "¡PAGO EXITOSO!", Toast.LENGTH_LONG).show()
                // MÁS ADELANTE AQUÍ CAMBIAREMOS EL ESTADO EN FIRESTORE A "PAGADO"
            }
        }
    }
}