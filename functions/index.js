const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

// La clave secreta ya no vive en el código: se configura una vez con
// `firebase functions:secrets:set STRIPE_SECRET_KEY` y aquí solo se referencia.
const stripeSecretKey = defineSecret("STRIPE_SECRET_KEY");

exports.crearIntencionDePago = onCall({ secrets: [stripeSecretKey] }, async (request) => {
    // EL MÉTODO SEGURO: en la API v2 el auth viaja en "request.auth", no en un segundo parámetro.
    if (!request.auth) {
        throw new HttpsError('unauthenticated', 'Debes iniciar sesión.');
    }
    //Texto para forzar.
    // Si pasó el filtro, significa que es un usuario legítimo.
    // Podemos sacar su UID real del context por si lo necesitamos después.
    const uidSeguro = request.auth.uid;

    // Stripe maneja todo en centavos
    const monto = request.data.monto;

    const stripe = require("stripe")(stripeSecretKey.value());

    try {
        const paymentIntent = await stripe.paymentIntents.create({
            amount: monto,
            currency: 'mxn',
            payment_method_types: ['card', 'oxxo'], // Habilitamos Tarjeta y OXXO
        });

        return {
            clientSecret: paymentIntent.client_secret
        };
    } catch (error) {
        throw new HttpsError('internal', error.message);
    }
});
