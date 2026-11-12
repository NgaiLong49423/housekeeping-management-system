package com.housekeeping.management.enums;

import java.util.Arrays;
import java.util.List;

public enum FeedbackTopic {
    TIMELINESS("Đúng giờ"),
    CLEANING_QUALITY("Chất lượng dọn dẹp"),
    STAFF_ATTITUDE("Thái độ Staff"),
    COMMUNICATION("Giao tiếp"),
    VALUE_FOR_MONEY("Dịch vụ có xứng với giá tiền?"),
    OTHER("Mục khác");

    private final String label;
    FeedbackTopic(String label) { this.label = label; }
    public String label() { return label; }
    public static FeedbackTopic fromCode(String code) {
        return Arrays.stream(values()).filter(topic -> topic.name().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Có chủ đề phản hồi không hợp lệ."));
    }
    public static List<String> labels(List<String> codes) {
        return codes == null ? List.of() : codes.stream().map(FeedbackTopic::fromCode).map(FeedbackTopic::label).toList();
    }
}
