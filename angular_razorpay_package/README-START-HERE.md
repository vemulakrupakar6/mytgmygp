# MY TG / MY GP — Angular House Tax Payment Page + Spring Boot API patches

This package creates a standalone Angular payment/receipt page and provides backend integration files for the existing Spring Boot project. It is a starter integration and has not been compiled against your complete backend project because your full pom.xml and Security configuration were not supplied.

## What belongs where

### Angular frontend
The `angular-payment-frontend` folder is a separate Angular application. It owns browser routes `/payment/:token` and `/payment/receipt/:receiptNumber`, and it calls Spring Boot using relative `/api/...` requests.

### Existing Spring Boot backend
Use the files under `backend-patches` as follows. Do not create duplicate Java classes:

1. Add `Controller/RazorpayHouseTaxPaymentController.java` to `src/main/java/com/tg/mytgmygp/Controller/`, replacing the earlier generated controller that returned HTML. This version exposes JSON APIs only.
2. Add `Service/RazorpayHouseTaxPaymentService.java` to `src/main/java/com/tg/mytgmygp/Service/` if that service is not already present. If you already copied the same payment service from the earlier ZIP, do not add another copy; keep one class.
3. The files ending `-payment-link-integration.java` are integration versions of your existing WhatsApp files. They are named this way only to make the ZIP unambiguous. Copy their contents into the existing `WhatsappWebhookController.java` and `WhatsappService.java` after backing up the originals, or use them as replacements only if they match your current files. Never put the `-payment-link-integration.java` names directly into your source tree because Java public class names must match the original names.
4. Apply the one-line `app.frontend-base-url` property change described in `backend-patches/Service/RazorpayHouseTaxPaymentService-config-change.txt`. This makes the WhatsApp payment link and receipt link point to Angular, not the backend HTML page.
5. Run `backend-patches/db/house_tax_payment_sessions.sql` against your `my_tg_my_gp` PostgreSQL database.
6. Merge the properties and Security matcher snippets. Do not replace your entire security configuration.

## Start the Angular app

Install Node.js/npm if needed, then open a terminal:

```bash
cd angular-payment-frontend
npm install
npm start
```

Angular listens on `http://localhost:4200`. Its `proxy.conf.json` forwards `/api/...` requests to `http://localhost:8080`, so keep Spring Boot running on port 8080.

## Cloudflare Quick Tunnel for WhatsApp phone testing

For this development setup, create ONE tunnel to Angular port 4200 (not directly to Spring Boot):

```bash
cloudflared tunnel --url http://localhost:4200
```

Copy the HTTPS URL printed by cloudflared, for example `https://random-name.trycloudflare.com`.

Set the backend environment variable before starting/restarting Spring Boot:

```text
APP_FRONTEND_BASE_URL=https://random-name.trycloudflare.com
```

Keep the frontend and backend running. The Angular dev server receives browser requests and proxies `/api/...` to Spring Boot. Therefore the same public tunnel URL can serve the Angular page and forward API calls, including the WhatsApp webhook path.

Configure the Meta webhook callback URL as:

```text
https://random-name.trycloudflare.com/api/v1/whatsapp/webhook
```

Configure Razorpay's webhook URL as:

```text
https://random-name.trycloudflare.com/api/v1/payments/razorpay/webhook
```

Subscribe to `payment.captured`. Configure the same webhook secret in the backend environment variable `RAZORPAY_WEBHOOK_SECRET`.

Quick Tunnel URLs change when restarted. Update `APP_FRONTEND_BASE_URL`, restart Spring Boot, and update both provider webhook callback URLs whenever the tunnel URL changes.

## Razorpay Test Mode environment variables

Set these in the environment used to start Spring Boot. Use Razorpay **Test Mode** credentials only:

```text
RAZORPAY_KEY_ID=rzp_test_your_test_key_id
RAZORPAY_KEY_SECRET=your_test_key_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
APP_FRONTEND_BASE_URL=https://your-current-tunnel.trycloudflare.com
```

Never put the Razorpay secret in Angular code or commit secrets to Git.

## Test the end-to-end flow

1. Start PostgreSQL and Spring Boot.
2. Start Angular with `npm start`.
3. Start cloudflared to port 4200 and configure the current tunnel URL as above.
4. Send `Hi` to your WhatsApp test number.
5. Select District → Mandal → Gram Panchayat → House Tax.
6. Search for assessment number `1704`.
7. Open the payment link from WhatsApp. It should display the Angular page, not Spring Boot HTML.
8. Confirm the displayed property/tax details and click **Complete Payment**.
9. Complete a Razorpay Test Mode payment. The receipt is shown only after backend verification.

## Important compatibility/security notes

- Backend code is based on the integration files from the earlier payment ZIP and Spring Boot 4 / Jackson 3 imports. It has not been compiled against your complete current project.
- If your current WhatsApp controller or service has changed since those integration files were generated, merge only the payment-link changes instead of replacing newer code.
- Preserve the existing `HouseTaxService`, DTOs, entity names, and package structure. Do not create duplicate controllers/services.
- The `ConcurrentHashMap` WhatsApp session map is only suitable for a demo and is not persistent across restarts.
- The included receipt is rendered in Angular and can be printed or saved using the browser's Print dialog; it is not a generated PDF file.
- Do not report a payment as successful based only on the browser redirect. The backend signature/order/amount/captured-status checks and signed Razorpay webhook must remain enabled.
