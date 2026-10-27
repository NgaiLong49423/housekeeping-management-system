package com.housekeeping.management.controller;

import com.housekeeping.management.service.PayosPaymentService;
import com.housekeeping.management.service.PayosWebhookRequest;
import com.housekeeping.management.service.PaymentService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.web.PortalUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentController {
    private final PortalQueryService query;
    private final PaymentService payments;
    private final PayosPaymentService payosPayments;

    public PaymentController(PortalQueryService query, PaymentService payments, PayosPaymentService payosPayments) {
        this.query = query;
        this.payments = payments;
        this.payosPayments = payosPayments;
    }

    @PostMapping("/customer/bookings/{id}/payment")
    String onlinePayment(@PathVariable Long id, @AuthenticationPrincipal PortalUser user) {
        query.booking(id, user);
        return "redirect:" + payosPayments.checkout(id, user.getAccountId());
    }

    @GetMapping({"/payments/payos/return", "/payments/payos/cancel"})
    String payosReturn(RedirectAttributes flash) {
        flash.addFlashAttribute("success", "Đã quay lại từ PayOS. Trạng thái thanh toán chỉ cập nhật sau khi hệ thống xác minh webhook.");
        return "redirect:/customer/bookings";
    }

    @PostMapping("/payments/payos/webhook")
    ResponseEntity<Void> payosWebhook(@RequestBody PayosWebhookRequest webhook) {
        payosPayments.receiveWebhook(webhook);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/staff/bookings/{id}/cash-payment")
    String confirmCashPayment(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            RedirectAttributes flash) {
        query.booking(id, user);
        payments.confirmCashReceived(id, user.getAccountId());
        flash.addFlashAttribute("success", "Đã xác nhận nhận tiền mặt; lịch hẹn đã hoàn tất.");
        return "redirect:/staff/bookings/" + id;
    }
}
