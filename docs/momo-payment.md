# MoMo Test Payment

## Overview

Completed bookings can be paid by cash, manual bank transfer, or MoMo. Cash and bank transfer continue to require Admin verification. A MoMo payment is confirmed only by a signed IPN whose partner code, order ID, request ID, and amount match the stored payment attempt. The browser return URL is informational and never marks a payment paid.

The integration uses MoMo's one-time e-wallet checkout (`captureWallet`) on the Test endpoint. MoMo provides separate Test and Production environments; this project defaults to Test. Use the MoMo Test App and test credentials for demo transactions.

## Configure credentials

1. Register a MoMo for Business merchant profile and obtain its Test `partnerCode`, `accessKey`, and `secretKey`.
2. Copy `.env.example` to `.env` in `app/housekeeping-management-system/` and fill in the MoMo Test credentials.
3. Configure `MOMO_REDIRECT_URL` and `MOMO_IPN_URL` with HTTPS URLs reachable from MoMo. For local development, expose the app through a temporary HTTPS tunnel and use its URL for both callbacks.
4. Start the application. No MoMo credentials are needed for startup; checkout reports a configuration error until all required values are provided.

Never put actual merchant credentials in source files, screenshots, Postman exports, or Git.

## Payment flow

1. Staff completes a confirmed Booking; the system creates its single `UNPAID` Payment.
2. The owning Customer chooses `MOMO`. The backend locks the Payment, persists a unique `Payment_Attempt`, signs the request with HMAC-SHA256, and asks MoMo for checkout.
3. The Customer is redirected to MoMo. The saved checkout can be reopened while its attempt remains pending.
4. MoMo posts the result to `/payments/momo/ipn`. The endpoint validates the signature and matches the stored partner/order/request/amount before changing the Payment to `PAID`. Repeated valid IPNs are idempotent; failed transactions leave it `UNPAID`.
5. MoMo's browser redirect returns to `/payments/momo/return`; that route only shows a message and never changes payment state.

## Postman callback checks

The collection at `postman/MoMo-IPN.postman_collection.json` sends example IPNs to the local callback. It includes the valid, invalid-signature, mismatched-amount, and duplicate-delivery cases. A valid signature must be generated from the Test secret key; keep that value in a local Postman secret variable and never export it. The request's `orderId`, `requestId`, and `amount` must belong to a real pending attempt in the selected database.

## Production

Production is disabled by default. After MoMo grants Production credentials, configure the Production base URL and the corresponding Production credentials and callback URLs outside source control. Run end-to-end acceptance with the merchant account before accepting customer payments.
