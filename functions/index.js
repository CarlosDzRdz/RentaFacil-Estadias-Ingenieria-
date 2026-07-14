const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

// Inicializamos Admin para poder modificar la base de datos Firestore más adelante
const admin = require("firebase-admin");
admin.initializeApp();

// Secretos
const stripeSecretKey = defineSecret("STRIPE_SECRET_KEY");
const stripeWebhookSecret = defineSecret("STRIPE_WEBHOOK_SECRET");

// ----------------------------------------------------------------------
// FUNCIÓN 1: La que ya teníamos (Llamada desde el celular para crear el cobro)
// ----------------------------------------------------------------------
exports.crearIntencionDePago = onCall({ secrets: [stripeSecretKey] }, async (request) => {
    if (!request.auth) {
        throw new HttpsError('unauthenticated', 'Debes iniciar sesión.');
    }

    const uid = request.auth.uid;
    const usuarioRef = admin.firestore().collection('usuarios').doc(uid);

    // No confiamos en el monto que manda el cliente: lo leemos del expediente en Firestore.
    const usuarioSnap = await usuarioRef.get();
    if (!usuarioSnap.exists) {
        throw new HttpsError('not-found', 'No se encontró tu expediente.');
    }

    const datosUsuario = usuarioSnap.data();

    // Si ya hay un PaymentIntent sin resolver, no dejamos generar otro para el mismo periodo.
    if (datosUsuario.payment_intent_pendiente) {
        throw new HttpsError(
            'failed-precondition',
            'Ya tienes un pago en proceso. Espera a que se confirme o cancele antes de generar uno nuevo.'
        );
    }

    const montoRenta = datosUsuario.monto_renta;
    if (typeof montoRenta !== 'number' || montoRenta <= 0) {
        throw new HttpsError('failed-precondition', 'Monto de renta inválido.');
    }

    const montoCentavos = Math.round(montoRenta * 100);
    const stripe = require("stripe")(stripeSecretKey.value());

    try {
        const paymentIntent = await stripe.paymentIntents.create({
            amount: montoCentavos,
            currency: 'mxn',
            payment_method_types: ['card', 'oxxo'],
            // Guardamos el UID del usuario en los "metadatos" del cobro
            // para saber a quién pertenece cuando el webhook nos avise que ya pagó.
            metadata: {
                uidInquilino: uid
            }
        });

        // Marcamos el pago como "en tránsito" para bloquear la generación de otro
        // hasta que el webhook confirme éxito o fallo/cancelación.
        await usuarioRef.update({
            payment_intent_pendiente: paymentIntent.id,
            estado_pago: 'en_transito'
        });

        return { clientSecret: paymentIntent.client_secret };
    } catch (error) {
        throw new HttpsError('internal', error.message);
    }
});

// Cuántos meses le corresponde sumar a fecha_vencimiento según el contrato
const MESES_POR_TIPO_CONTRATO = {
    'Mensual': 1,
    'Semestral': 6,
    'Anual': 12
};

// Suma "meses" a una fecha (UTC), respetando el día fijo del contrato y
// recortando al último día del mes destino cuando ese día no existe
// (ej. día 30 en febrero), sin que el recorte se quede pegado en meses futuros.
function calcularSiguienteVencimiento(fechaVencimientoActual, diaPagoContrato, meses) {
    const anioBase = fechaVencimientoActual.getUTCFullYear();
    const mesBase = fechaVencimientoActual.getUTCMonth();

    const mesDestinoAbsoluto = mesBase + meses;
    const anioDestino = anioBase + Math.floor(mesDestinoAbsoluto / 12);
    const mesDestino = mesDestinoAbsoluto % 12;

    // Día 0 del mes siguiente al destino = último día del mes destino
    const ultimoDiaMesDestino = new Date(Date.UTC(anioDestino, mesDestino + 1, 0)).getUTCDate();
    const diaFinal = Math.min(diaPagoContrato, ultimoDiaMesDestino);

    // Medianoche UTC-6 (convención usada en toda la app) equivale a las 06:00 UTC
    return new Date(Date.UTC(anioDestino, mesDestino, diaFinal, 6, 0, 0));
}

// Aplica los efectos de un pago exitoso sobre el expediente del usuario:
// marca "pagado", registra la fecha del pago y avanza fecha_vencimiento.
// Es idempotente: si este mismo PaymentIntent ya fue procesado, no hace nada.
async function procesarPagoExitoso(uid, paymentIntentId) {
    const db = admin.firestore();
    const usuarioRef = db.collection('usuarios').doc(uid);
    const idempotenciaRef = usuarioRef.collection('pagos_procesados').doc(paymentIntentId);

    await db.runTransaction(async (transaction) => {
        const idempotenciaSnap = await transaction.get(idempotenciaRef);
        if (idempotenciaSnap.exists) {
            console.log(`El pago ${paymentIntentId} ya había sido procesado, se ignora.`);
            return;
        }

        const usuarioSnap = await transaction.get(usuarioRef);
        if (!usuarioSnap.exists) {
            console.error(`No se encontró el usuario ${uid} para aplicar el pago.`);
            return;
        }

        const datos = usuarioSnap.data();
        const meses = MESES_POR_TIPO_CONTRATO[datos.tipo_contrato];

        if (!datos.dia_pago_contrato || !datos.fecha_vencimiento || !meses) {
            console.error(`Datos incompletos para calcular el vencimiento de ${uid}.`);
            return;
        }

        const diaPagoContrato = datos.dia_pago_contrato.toDate().getUTCDate();
        const fechaVencimientoActual = datos.fecha_vencimiento.toDate();
        const nuevaFechaVencimiento = calcularSiguienteVencimiento(fechaVencimientoActual, diaPagoContrato, meses);

        transaction.update(usuarioRef, {
            estado_pago: 'pagado',
            fecha_ultimo_pago: admin.firestore.FieldValue.serverTimestamp(),
            fecha_vencimiento: admin.firestore.Timestamp.fromDate(nuevaFechaVencimiento),
            payment_intent_pendiente: admin.firestore.FieldValue.delete()
        });

        transaction.set(idempotenciaRef, {
            procesado_en: admin.firestore.FieldValue.serverTimestamp()
        });
    });
}

// Cuando un PaymentIntent falla o se cancela (ej. un voucher de OXXO que expiró
// sin pagarse), quitamos el bloqueo para que el usuario pueda intentar de nuevo.
async function revertirPagoPendiente(uid) {
    const usuarioRef = admin.firestore().collection('usuarios').doc(uid);
    await usuarioRef.update({
        payment_intent_pendiente: admin.firestore.FieldValue.delete(),
        estado_pago: 'pendiente'
    });
}

// ----------------------------------------------------------------------
// FUNCIÓN 2: NUEVO WEBHOOK (El "teléfono rojo" que Stripe llamará)
// ----------------------------------------------------------------------
exports.stripeWebhook = onRequest({ secrets: [stripeSecretKey, stripeWebhookSecret] }, async (req, res) => {
    const stripe = require("stripe")(stripeSecretKey.value());
    
    // Stripe nos manda una firma secreta en los encabezados para demostrar que son ellos
    const signature = req.headers['stripe-signature'];
    const endpointSecret = stripeWebhookSecret.value();

    let event;

    try {
        // Verificamos que el mensaje es auténtico usando req.rawBody (magia de Firebase v2)
        event = stripe.webhooks.constructEvent(req.rawBody, signature, endpointSecret);
    } catch (err) {
        console.error(`⚠️ Error de Webhook de Stripe: ${err.message}`);
        res.status(400).send(`Webhook Error: ${err.message}`);
        return;
    }

    // Si la firma es correcta, revisamos qué fue lo que pasó
    switch (event.type) {
        case 'payment_intent.succeeded': {
            const paymentIntent = event.data.object;
            const uidInquilino = paymentIntent.metadata.uidInquilino;

            console.log(`✅ ¡PAGO EXITOSO CONFIRMADO POR STRIPE! Monto: ${paymentIntent.amount}`);
            console.log(`Usuario que pagó: ${uidInquilino}`);

            try {
                await procesarPagoExitoso(uidInquilino, paymentIntent.id);
            } catch (err) {
                console.error(`Error actualizando Firestore para ${uidInquilino}:`, err);
            }
            break;
        }

        case 'payment_intent.payment_failed':
        case 'payment_intent.canceled': {
            const paymentIntent = event.data.object;
            const uidInquilino = paymentIntent.metadata.uidInquilino;

            console.log(`❌ Pago fallido/cancelado (${event.type}). Usuario: ${uidInquilino}`);

            try {
                if (uidInquilino) {
                    await revertirPagoPendiente(uidInquilino);
                }
            } catch (err) {
                console.error(`Error revirtiendo pago pendiente de ${uidInquilino}:`, err);
            }
            break;
        }

        default:
            console.log(`Evento no manejado: ${event.type}`);
    }

    // Le respondemos a Stripe con un 200 (OK) para que sepa que recibimos el mensaje
    res.status(200).send('Recibido');
});