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

    const monto = request.data.monto;
    const stripe = require("stripe")(stripeSecretKey.value());

    try {
        const paymentIntent = await stripe.paymentIntents.create({
            amount: monto,
            currency: 'mxn',
            payment_method_types: ['card', 'oxxo'],
            // Opcional: Podemos guardar el UID del usuario en los "metadatos" del cobro 
            // para saber a quién pertenece cuando OXXO nos avise que ya pagó.
            metadata: {
                uidInquilino: request.auth.uid 
            }
        });

        return { clientSecret: paymentIntent.client_secret };
    } catch (error) {
        throw new HttpsError('internal', error.message);
    }
});

// ----------------------------------------------------------------------
// FUNCIÓN 2: NUEVO WEBHOOK (El "teléfono rojo" que Stripe llamará)
// ----------------------------------------------------------------------
exports.stripeWebhook = onRequest({ secrets: [stripeSecretKey, stripeWebhookSecret] }, (req, res) => {
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
        case 'payment_intent.succeeded':
            const paymentIntent = event.data.object;
            const uidInquilino = paymentIntent.metadata.uidInquilino;
            
            console.log(`✅ ¡PAGO EXITOSO CONFIRMADO POR STRIPE! Monto: ${paymentIntent.amount}`);
            console.log(`Usuario que pagó: ${uidInquilino}`);
            
            // AQUÍ PONDREMOS EL CÓDIGO PARA ACTUALIZAR FIRESTORE A "PAGADO"
            break;
            
        case 'payment_intent.payment_failed':
            console.log('❌ El pago falló o fue cancelado.');
            break;
            
        default:
            console.log(`Evento no manejado: ${event.type}`);
    }

    // Le respondemos a Stripe con un 200 (OK) para que sepa que recibimos el mensaje
    res.status(200).send('Recibido');
});