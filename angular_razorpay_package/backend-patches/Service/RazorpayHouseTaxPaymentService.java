package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.HouseTaxDetailsRequest;
import com.tg.mytgmygp.Dto.HouseTaxDetailsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RazorpayHouseTaxPaymentService {
    private final JdbcTemplate jdbcTemplate;
    private final HouseTaxService houseTaxService;
    private final WhatsappService whatsappService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.frontend-base-url:${app.public-base-url}}")
    private String publicBaseUrl;

    public RazorpayHouseTaxPaymentService(
            JdbcTemplate jdbcTemplate,
            HouseTaxService houseTaxService,
            WhatsappService whatsappService,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.houseTaxService = houseTaxService;
        this.whatsappService = whatsappService;
        this.objectMapper = objectMapper;
    }

    public String createPaymentSession(String phone, HouseTaxDetailsRequest request) {
        HouseTaxDetailsResponse details = houseTaxService.getDetails(request);
        BigDecimal amount = details.totalPayable() == null ? BigDecimal.ZERO : details.totalPayable();
        long amountPaise = amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("The payable amount must be greater than zero.");
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256Hex(token);
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(30 * 60);

        jdbcTemplate.update("""
            INSERT INTO house_tax_payment_sessions
              (token_hash, whatsapp_phone, district_id, mandal_id, gram_panchayat_id,
               assessment_no, service_code, search_method, search_value, amount_paise,
               currency, status, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'INR', 'CREATED', ?, ?)
            """,
            tokenHash, phone, request.districtId(), request.mandalId(), request.gramPanchayatId(),
            request.assessmentNo(), request.serviceCode(), request.searchMethod(), request.searchValue(),
            amountPaise, Timestamp.from(now), Timestamp.from(expires));

        return cleanBaseUrl() + "/payment/" + token;
    }

    public Map<String, Object> getPageDataByToken(String token) {
        PaymentSession session = findByToken(token);
        if (!"PAID".equals(session.status()) && Instant.now().isAfter(session.expiresAt())) {
            Map<String, Object> expired = baseResponse(session);
            expired.put("status", "EXPIRED");
            expired.put("message", "This payment link has expired. Return to WhatsApp and request a new link.");
            return expired;
        }
        return makePageData(session);
    }

    public Map<String, Object> getPageDataByReceipt(String receiptNumber) {
        PaymentSession session;
        try {
            session = jdbcTemplate.queryForObject("""
                SELECT token_hash, whatsapp_phone, district_id, mandal_id, gram_panchayat_id,
                       assessment_no, service_code, search_method, search_value, amount_paise,
                       currency, status, razorpay_order_id, razorpay_payment_id, receipt_number,
                       created_at, expires_at, paid_at
                FROM house_tax_payment_sessions WHERE receipt_number = ?
                """, (rs, rowNum) -> mapSession(rs), receiptNumber);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Receipt not found.");
        }
        if (!"PAID".equals(session.status())) {
            throw new IllegalArgumentException("This receipt has not been paid.");
        }
        return makePageData(session);
    }

    public Map<String, Object> createOrder(String token) {
        PaymentSession session = findByToken(token);
        ensureSessionUsable(session);
        if ("PAID".equals(session.status())) {
            throw new IllegalArgumentException("This property tax has already been paid.");
        }

        HouseTaxDetailsResponse details = detailsFor(session);
        long freshAmount = toPaise(details.totalPayable());
        if (freshAmount != session.amountPaise()) {
            throw new IllegalArgumentException(
                    "The payable amount has changed. Please restart from WhatsApp to get an updated payment link.");
        }

        if (session.razorpayOrderId() != null && !session.razorpayOrderId().isBlank()) {
            return orderResponse(session, session.razorpayOrderId());
        }

        String receiptRef = "HT" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        Map<String, Object> orderRequest = new LinkedHashMap<>();
        orderRequest.put("amount", session.amountPaise());
        orderRequest.put("currency", session.currency());
        orderRequest.put("receipt", receiptRef);
        orderRequest.put("notes", Map.of(
                "assessment_no", String.valueOf(session.assessmentNo()),
                "service_code", session.serviceCode(),
                "application", "MY_TG_MY_GP"
        ));

        Map<?, ?> razorpayResponse = restClient.post()
                .uri("https://api.razorpay.com/v1/orders")
                .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                .body(orderRequest)
                .retrieve()
                .body(Map.class);

        if (razorpayResponse == null || razorpayResponse.get("id") == null) {
            throw new IllegalStateException("Razorpay did not return an order ID.");
        }
        String orderId = String.valueOf(razorpayResponse.get("id"));
        jdbcTemplate.update("""
            UPDATE house_tax_payment_sessions
               SET razorpay_order_id = ?, status = 'ORDER_CREATED'
             WHERE token_hash = ? AND status <> 'PAID'
            """, orderId, session.tokenHash());

        return orderResponse(session, orderId);
    }

    public Map<String, Object> verifyCheckoutPayment(
            String token, String submittedOrderId, String paymentId, String signature) {
        PaymentSession session = findByToken(token);
        if ("PAID".equals(session.status())) {
            return successResponse(session);
        }
        ensureSessionUsable(session);

        if (session.razorpayOrderId() == null
                || !session.razorpayOrderId().equals(submittedOrderId)) {
            throw new IllegalArgumentException("The Razorpay order does not match this payment session.");
        }
        if (paymentId == null || paymentId.isBlank() || signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Razorpay payment verification details are incomplete.");
        }
        if (!verifyCheckoutSignature(session.razorpayOrderId(), paymentId, signature)) {
            throw new IllegalArgumentException("Payment signature verification failed.");
        }

        Map<?, ?> payment = restClient.get()
                .uri("https://api.razorpay.com/v1/payments/{paymentId}", paymentId)
                .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                .retrieve()
                .body(Map.class);

        if (payment == null) {
            throw new IllegalStateException("Unable to confirm payment status with Razorpay.");
        }
        String actualOrderId = String.valueOf(payment.get("order_id"));
        long actualAmount = Long.parseLong(String.valueOf(payment.get("amount")));
        String paymentStatus = String.valueOf(payment.get("status"));

        if (!session.razorpayOrderId().equals(actualOrderId) || actualAmount != session.amountPaise()) {
            throw new IllegalArgumentException("The payment amount or order does not match this property tax.");
        }

        if ("captured".equalsIgnoreCase(paymentStatus)) {
            markPaid(session, paymentId);
            return successResponse(findByToken(token));
        }

        if ("authorized".equalsIgnoreCase(paymentStatus)) {
            return Map.of(
                    "status", "PENDING",
                    "message", "Payment is authorised but not yet captured. Please wait for confirmation.",
                    "receiptUrl", ""
            );
        }

        return Map.of(
                "status", "FAILED",
                "message", "Razorpay has not confirmed a successful captured payment. You can retry payment.",
                "receiptUrl", ""
        );
    }

    public void handleRazorpayWebhook(String rawPayload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("razorpay.webhook.secret is not configured.");
        }
        if (!verifyHmac(rawPayload, signature, webhookSecret)) {
            throw new IllegalArgumentException("Invalid Razorpay webhook signature.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText("");
            if (!"payment.captured".equals(event)) {
                return;
            }
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String paymentId = payment.path("id").asText("");
            String orderId = payment.path("order_id").asText("");
            long amountPaise = payment.path("amount").asLong(-1);
            if (paymentId.isBlank() || orderId.isBlank() || amountPaise < 0) {
                throw new IllegalArgumentException("Incomplete payment.captured webhook payload.");
            }

            PaymentSession session = findByOrderId(orderId);
            if (session.amountPaise() != amountPaise) {
                throw new IllegalArgumentException("Webhook amount does not match the stored payment amount.");
            }
            markPaid(session, paymentId);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to process Razorpay webhook payload.", ex);
        }
    }

    private void markPaid(PaymentSession session, String paymentId) {
        String receipt = "HTX-" + UUID.randomUUID().toString().toUpperCase();
        int updated = jdbcTemplate.update("""
            UPDATE house_tax_payment_sessions
               SET status = 'PAID',
                   razorpay_payment_id = ?,
                   receipt_number = ?,
                   paid_at = CURRENT_TIMESTAMP
             WHERE token_hash = ? AND status <> 'PAID'
            """, paymentId, receipt, session.tokenHash());

        if (updated == 1) {
            String receiptUrl = cleanBaseUrl() + "/payment/receipt/" + receipt;
            try {
                whatsappService.sendTextMessage(
                        session.whatsappPhone(),
                        "PAYMENT SUCCESSFUL - MY TG / MY GP\\n\\n"
                                + "House Tax payment has been verified successfully.\\n"
                                + "Assessment No.: " + session.assessmentNo() + "\\n"
                                + "Amount Paid: ₹" + formatRupees(session.amountPaise()) + "\\n"
                                + "Receipt No.: " + receipt + "\\n"
                                + "Razorpay Payment ID: " + paymentId + "\\n\\n"
                                + "View your receipt: " + receiptUrl
                );
            } catch (Exception ex) {
                // Payment status must remain PAID even if WhatsApp temporarily fails.
                System.err.println("Payment succeeded, but WhatsApp receipt notification failed: " + ex.getMessage());
            }
        }
    }

    private Map<String, Object> makePageData(PaymentSession session) {
        HouseTaxDetailsResponse details = detailsFor(session);
        Map<String, Object> response = baseResponse(session);
        response.put("details", details);
        response.put("status", session.status());
        response.put("message", "Payment details loaded.");
        return response;
    }

    private Map<String, Object> baseResponse(PaymentSession session) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", session.status());
        response.put("amountPaise", session.amountPaise());
        response.put("currency", session.currency());
        response.put("receiptNumber", session.receiptNumber());
        response.put("paymentId", session.razorpayPaymentId());
        response.put("paidAt", session.paidAt() == null ? null : session.paidAt().toString());
        return response;
    }

    private Map<String, Object> orderResponse(PaymentSession session, String orderId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("keyId", keyId);
        response.put("orderId", orderId);
        response.put("amount", session.amountPaise());
        response.put("currency", session.currency());
        response.put("name", "MY TG / MY GP");
        response.put("description", "House Tax - Assessment " + session.assessmentNo());
        response.put("prefillContact", session.whatsappPhone());
        return response;
    }

    private Map<String, Object> successResponse(PaymentSession session) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "PAID");
        response.put("message", "Payment successfully completed.");
        response.put("receiptNumber", session.receiptNumber());
        response.put("paymentId", session.razorpayPaymentId());
        response.put("receiptUrl", cleanBaseUrl() + "/payment/receipt/" + session.receiptNumber());
        return response;
    }

    private HouseTaxDetailsResponse detailsFor(PaymentSession s) {
        return houseTaxService.getDetails(new HouseTaxDetailsRequest(
                s.districtId(), s.mandalId(), s.gramPanchayatId(),
                s.serviceCode(), s.assessmentNo(), s.searchMethod(), s.searchValue()));
    }

    private PaymentSession findByToken(String token) {
        if (token == null || token.length() < 30 || token.length() > 100) {
            throw new IllegalArgumentException("Invalid or expired payment link.");
        }
        try {
            return jdbcTemplate.queryForObject("""
                SELECT token_hash, whatsapp_phone, district_id, mandal_id, gram_panchayat_id,
                       assessment_no, service_code, search_method, search_value, amount_paise,
                       currency, status, razorpay_order_id, razorpay_payment_id, receipt_number,
                       created_at, expires_at, paid_at
                FROM house_tax_payment_sessions WHERE token_hash = ?
                """, (rs, rowNum) -> mapSession(rs), sha256Hex(token));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Invalid or expired payment link.");
        }
    }

    private PaymentSession findByOrderId(String orderId) {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT token_hash, whatsapp_phone, district_id, mandal_id, gram_panchayat_id,
                       assessment_no, service_code, search_method, search_value, amount_paise,
                       currency, status, razorpay_order_id, razorpay_payment_id, receipt_number,
                       created_at, expires_at, paid_at
                FROM house_tax_payment_sessions WHERE razorpay_order_id = ?
                """, (rs, rowNum) -> mapSession(rs), orderId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("No local payment session found for this Razorpay order.");
        }
    }

    private PaymentSession mapSession(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp paid = rs.getTimestamp("paid_at");
        return new PaymentSession(
                rs.getString("token_hash"), rs.getString("whatsapp_phone"),
                rs.getInt("district_id"), rs.getInt("mandal_id"), rs.getInt("gram_panchayat_id"),
                rs.getLong("assessment_no"), rs.getString("service_code"),
                rs.getString("search_method"), rs.getString("search_value"),
                rs.getLong("amount_paise"), rs.getString("currency"), rs.getString("status"),
                rs.getString("razorpay_order_id"), rs.getString("razorpay_payment_id"),
                rs.getString("receipt_number"),
                created == null ? Instant.EPOCH : created.toInstant(),
                expires == null ? Instant.EPOCH : expires.toInstant(),
                paid == null ? null : paid.toInstant()
        );
    }

    private void ensureSessionUsable(PaymentSession session) {
        if ("PAID".equals(session.status())) {
            throw new IllegalArgumentException("This payment has already been completed.");
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            throw new IllegalArgumentException("This payment link has expired. Return to WhatsApp and request a new link.");
        }
    }

    private boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return verifyHmac(orderId + "|" + paymentId, signature, keySecret);
    }

    private boolean verifyHmac(String payload, String expectedHex, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] actual = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : actual) hex.append(String.format("%02x", b));
            return MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.US_ASCII),
                    (expectedHex == null ? "" : expectedHex).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify Razorpay signature.", ex);
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash payment session token.", ex);
        }
    }

    private long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private String formatRupees(long paise) {
        return BigDecimal.valueOf(paise, 2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String cleanBaseUrl() {
        return publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    private record PaymentSession(
            String tokenHash, String whatsappPhone, Integer districtId, Integer mandalId,
            Integer gramPanchayatId, Long assessmentNo, String serviceCode, String searchMethod,
            String searchValue, long amountPaise, String currency, String status,
            String razorpayOrderId, String razorpayPaymentId, String receiptNumber,
            Instant createdAt, Instant expiresAt, Instant paidAt) {}
}
