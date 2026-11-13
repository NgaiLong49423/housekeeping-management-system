package com.housekeeping.management.controller;

import com.housekeeping.management.service.PaymentService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.OnlinePaymentService;
import com.housekeeping.management.web.PortalUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentController {
    private static final String PAID_MESSAGE = "Thanh toán thành công qua VNPay. Lịch hẹn đã hoàn tất.";

    private final PortalQueryService query;
    private final PaymentService payments;
    private final OnlinePaymentService onlinePayments;

    public PaymentController(PortalQueryService query, PaymentService payments, OnlinePaymentService onlinePayments) {
        this.query = query;
        this.payments = payments;
        this.onlinePayments = onlinePayments;
    }

    @PostMapping("/customer/bookings/{id}/payment")
    String onlinePayment(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, HttpServletRequest request,
            RedirectAttributes flash) {
        query.booking(id, user);
        OnlinePaymentService.Checkout checkout = onlinePayments.checkout(id, user.getAccountId(), clientIp(request));
        if (checkout.alreadyPaid()) {
            flash.addFlashAttribute("success", PAID_MESSAGE);
            return "redirect:/customer/bookings/" + id;
        }
        return "redirect:" + checkout.redirectUrl();
    }

    @GetMapping("/payments/vnpay/return")
    String vnpayReturn(@RequestParam Map<String, String> params, RedirectAttributes flash) {
        OnlinePaymentService.ReturnOutcome outcome;
        try {
            outcome = onlinePayments.handleReturn(params);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            // Dữ liệu trả về hoặc phản hồi QueryDR không hợp lệ: không đổi trạng thái thanh toán.
            flash.addFlashAttribute("error", "Dữ liệu trả về từ VNPay không hợp lệ. Trạng thái thanh toán chưa thay đổi.");
            return "redirect:/customer/bookings";
        }
        switch (outcome.status()) {
            case PAID -> flash.addFlashAttribute("success", PAID_MESSAGE);
            case FAILED -> flash.addFlashAttribute("error",
                    "Thanh toán VNPay chưa thành công hoặc đã bị hủy. Bạn có thể thanh toán lại.");
            case PENDING -> flash.addFlashAttribute("error",
                    "VNPay chưa xác nhận giao dịch. Hãy tải lại trang sau ít phút hoặc bấm thanh toán lại để hệ thống kiểm tra.");
        }
        return "redirect:/customer/bookings/" + outcome.bookingId();
    }

    @GetMapping("/payments/vnpay/ipn")
    ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        OnlinePaymentService.IpnResponse response = onlinePayments.handleIpn(params);
        return ResponseEntity.ok(Map.of("RspCode", response.rspCode(), "Message", response.message()));
    }

    @PostMapping("/staff/bookings/{id}/cash-payment")
    String confirmCashPayment(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            RedirectAttributes flash) {
        query.booking(id, user);
        payments.confirmCashReceived(id, user.getAccountId());
        flash.addFlashAttribute("success", "Đã xác nhận nhận tiền mặt; lịch hẹn đã hoàn tất.");
        return "redirect:/staff/bookings/" + id;
    }

    /** VNPay expects an IPv4 address; local IPv6 loopback requests are reported as 127.0.0.1. */
    private static String clientIp(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() || address.contains(":") ? "127.0.0.1" : address;
    }
}
