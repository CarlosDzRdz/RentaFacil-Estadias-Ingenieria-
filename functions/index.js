const functions = require("firebase-functions");
const stripe = require("stripe")("sk_test_51TnUasAff8ArF2JZ7Bn0zeWn6lnngCLwQDyw4UbKClsXa7vaYhOqCLofz168EXTm97uxXweT7n2Qyiy5GUgdfdKn006aqpUk03");

exports.crearIntencionDePago = functions.https.onCall(async (data, context) => {
    // EL MÉTODO SEGURO: Verificamos el token encriptado que manda Firebase Auth
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Debes iniciar sesión.');
    }

    // Si pasó el filtro, significa que es un usuario legítimo. 
    // Podemos sacar su UID real del context por si lo necesitamos después.
    const uidSeguro = context.auth.uid; 
    
    // Stripe maneja todo en centavos
    const monto = data.monto; 

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
        throw new functions.https.HttpsError('internal', error.message);
    }
});
