package com.utch.rentafacil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RentaFacilMessagingService : FirebaseMessagingService() {

    companion object {
        const val CANAL_RECORDATORIOS = "recordatorios_pago"
    }

    // Firebase genera un token nuevo si el anterior se invalida; lo guardamos
    // en Firestore para que la Cloud Function programada sepa a qué dispositivo avisar.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        guardarTokenEnFirestore(token)
    }

    // Con la app en primer plano, FCM no muestra la notificación automáticamente:
    // hay que construirla y mostrarla nosotros mismos.
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val titulo = message.notification?.title ?: "RentaFacil"
        val cuerpo = message.notification?.body ?: return

        mostrarNotificacion(titulo, cuerpo)
    }

    private fun guardarTokenEnFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .update("fcm_token", token)
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_RECORDATORIOS,
                "Recordatorios de pago",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(canal)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(this, CANAL_RECORDATORIOS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notificacion)
    }
}
