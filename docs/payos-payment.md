# PayOS Checkout and Webhook

## Environment and credentials

PayOS currently provides a Production API and no separate sandbox/staging environment. Creating links and paying them use the real service and can move real money. The project does not send live transactions during automated checks.

Create and verify a PayOS account, create a payment channel, and copy its **Client ID**, **API Key**, and **Checksum Key** into the local `.env` as `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, and `PAYOS_CHECKSUM_KEY`. Keep these secrets out of source, screenshots, exported Postman files, and Git.

Set these public HTTPS URLs in the same `.env`:

```dotenv
PAYOS_RETURN_URL=https://YOUR_PUBLIC_HOST/payments/payos/return
PAYOS_CANCEL_URL=https://YOUR_PUBLIC_HOST/payments/payos/cancel
PAYOS_WEBHOOK_URL=https://YOUR_PUBLIC_HOST/payments/payos/webhook
```

Configure `PAYOS_WEBHOOK_URL` in the PayOS payment channel so PayOS can reach the local app through a public HTTPS tunnel. Return and cancel URLs only display an informational message; neither can mark a Payment paid. Do not use a tunnel URL as a credential.

## Payment flow

1. Staff completes a Booking, and the application creates its single `UNPAID` Payment.
2. The owning Customer selects `PAYOS`. The backend locks the Payment, allocates a unique integer `orderCode`, stores a `Payment_Attempt`, signs the payment-link request with HMAC-SHA256, and calls `POST /v2/payment-requests` with `x-client-id` and `x-api-key`.
3. The application validates PayOS's signed response and exact order/amount/currency before redirecting to the HTTPS checkout URL. A pending checkout can be reopened; an uncertain create is recovered by querying PayOS with its order code.
4. PayOS sends `POST /payments/payos/webhook`. The application checks the signature and matches order code, amount, currency, and payment-link ID with the stored attempt. Only a signed successful transaction (`data.code=00`) marks the Payment `PAID`. Repeated webhooks are idempotent.
5. `/payments/payos/return` and `/payments/payos/cancel` never update payment state. A webhook is the payment confirmation source.

Cash and manual bank transfer remain available and continue to require Admin verification. Historical `MOMO` Payment values remain readable but the MoMo checkout flow is removed.

## Postman webhook collection

`postman/PayOS-webhook.postman_collection.json` contains valid, duplicate, invalid-signature, and mismatched-amount callback cases. Set `PAYOS_CHECKSUM_KEY` as a local Postman secret variable and use the order code, amount, and payment-link ID of a pending attempt. Never export the secret with the collection.

## References

- [PayOS API: payment links and webhook](https://payos.vn/docs/api/)
- [PayOS signature verification](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/)
- [PayOS test environment notice](https://payos.vn/docs/moi-truong-test/)
