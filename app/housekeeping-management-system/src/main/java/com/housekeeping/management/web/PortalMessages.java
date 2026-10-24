package com.housekeeping.management.web;

import java.util.Map;

public final class PortalMessages {
    private PortalMessages() { }
    private static final Map<String, String> MESSAGES = Map.ofEntries(
        Map.entry("The selected Staff is already booked in this interval", "Nhân viên vừa có lịch trùng giờ. Vui lòng chọn khoảng khác."),
        Map.entry("A detail falls outside its referenced work schedule", "Tổng thời lượng dịch vụ vượt khoảng sẵn sàng. Hãy đổi giờ hoặc chọn khoảng dài hơn."),
        Map.entry("Every detail must use the selected Staff's schedule", "Khoảng sẵn sàng không thuộc nhân viên đã chọn."),
        Map.entry("Every detail must have between one and five valid evidences", "Mỗi dịch vụ cần từ 1 đến 5 ảnh bằng chứng trước khi hoàn thành."),
        Map.entry("Only a confirmed booking can be completed", "Chỉ có thể hoàn thành lịch đang được xác nhận."),
        Map.entry("Evidence must be a non-empty image no larger than 5 MB", "Chọn ảnh không rỗng, tối đa 5 MB."),
        Map.entry("Only JPEG and PNG evidence files are accepted", "Chỉ nhận ảnh JPEG hoặc PNG."),
        Map.entry("File extension does not match the image content", "Nội dung ảnh không khớp phần mở rộng của file."),
        Map.entry("Evidence is not a decodable image", "File không phải ảnh JPEG/PNG hợp lệ."),
        Map.entry("Evidence image could not be decoded", "Không thể đọc ảnh. Vui lòng chọn ảnh JPEG/PNG khác."),
        Map.entry("Only JPEG and PNG evidence content is accepted", "Nội dung file phải là ảnh JPEG/PNG."),
        Map.entry("A detail cannot have more than five evidences", "Dịch vụ này đã đủ 5 ảnh bằng chứng."),
        Map.entry("Evidence can only be uploaded for a confirmed booking", "Chỉ tải ảnh cho lịch đang được xác nhận."),
        Map.entry("Only an unpaid payment with a selected method can be confirmed", "Khách phải chọn phương thức và thanh toán phải còn chưa trả."),
        Map.entry("Receipt must be verified before confirming payment", "Hãy xác minh thực tế đã nhận tiền trước khi đánh dấu đã trả."),
        Map.entry("A method can only be selected for an unpaid completed booking", "Chỉ chọn phương thức cho lịch hoàn thành và chưa thanh toán.")
    );
    public static String business(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && (message.startsWith("Chỉ ") || message.startsWith("Lịch ") || message.startsWith("Bạn ")
                || message.startsWith("Khoảng ") || message.startsWith("Hãy ") || message.startsWith("Giờ ")
                || message.startsWith("Nhân viên ") || message.startsWith("Dịch vụ "))) { return message; }
        return MESSAGES.getOrDefault(message == null ? "" : message, "Không thể thực hiện thao tác. Vui lòng kiểm tra dữ liệu hoặc tải lại trang.");
    }
}
