package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Service.RazorpayHouseTaxPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** API-only controller. Angular owns /payment/... browser routes and renders the page. */
@RestController
public class RazorpayHouseTaxPaymentController {
    private final RazorpayHouseTaxPaymentService paymentService;

    public RazorpayHouseTaxPaymentController(RazorpayHouseTaxPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/api/v1/payments/house-tax/{token}/details")
    public ResponseEntity<Map<String, Object>> getByToken(@PathVariable String token) {
        return ResponseEntity.ok(paymentService.getPageDataByToken(token));
    }

    @GetMapping("/api/v1/payments/receipt/{receiptNumber}")
    public ResponseEntity<Map<String, Object>> getByReceipt(@PathVariable String receiptNumber) {
        return ResponseEntity.ok(paymentService.getPageDataByReceipt(receiptNumber));
    }

    @PostMapping("/api/v1/payments/house-tax/{token}/order")
    public ResponseEntity<Map<String, Object>> createOrder(@PathVariable String token) {
        return ResponseEntity.ok(paymentService.createOrder(token));
    }

    @PostMapping("/api/v1/payments/house-tax/{token}/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @PathVariable String token,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(paymentService.verifyCheckoutPayment(
                token,
                body.get("razorpay_order_id"),
                body.get("razorpay_payment_id"),
                body.get("razorpay_signature")
        ));
    }

    @PostMapping("/api/v1/payments/razorpay/webhook")
    public ResponseEntity<String> razorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        try {
            paymentService.handleRazorpayWebhook(rawPayload, signature);
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}
