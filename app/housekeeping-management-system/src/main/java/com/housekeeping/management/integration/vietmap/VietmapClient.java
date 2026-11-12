package com.housekeeping.management.integration.vietmap;

import com.housekeeping.management.config.VietmapProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VietmapClient {
    private final VietmapProperties properties;
    private final RestClient client;

    @Autowired
    public VietmapClient(VietmapProperties properties) {
        this(properties, defaultClient(properties));
    }

    private static RestClient defaultClient(VietmapProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) return null;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(8_000);
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    VietmapClient(VietmapProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    public List<Suggestion> autocomplete(String query) {
        requireKey();
        try {
            if (client == null) return fallbackSuggestions(query);
            List<Map<String, Object>> json = client.get().uri(uri -> uri.path("/api/autocomplete/v4")
                    .queryParam("apikey", properties.apiKey()).queryParam("text", query).build())
                    .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() { });
            if (json == null) return fallbackSuggestions(query);
            java.util.ArrayList<Suggestion> results = new java.util.ArrayList<>();
            json.forEach(row -> {
                String id = text(row.get("ref_id"));
                String display = text(row.get("display"));
                if (id != null && display != null) results.add(new Suggestion(id, display));
            });
            return results.isEmpty() ? fallbackSuggestions(query) : List.copyOf(results);
        } catch (RestClientException e) {
            return fallbackSuggestions(query);
        }
    }

    public Place place(String refId) {
        requireKey();
        try {
            if (client == null || (refId != null && refId.startsWith("ref-demo-"))) {
                return fallbackPlace(refId);
            }
            Map<String, Object> json = client.get().uri(uri -> uri.path("/api/place/v4").queryParam("apikey", properties.apiKey())
                    .queryParam("refid", refId).build()).retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() { });
            if (json == null || json.get("lat") == null || json.get("lng") == null) throw new IllegalArgumentException("Địa chỉ VietMap không có tọa độ hợp lệ.");
            String display = text(json.get("display"));
            BigDecimal latitude = new BigDecimal(json.get("lat").toString());
            BigDecimal longitude = new BigDecimal(json.get("lng").toString());
            if (display == null || display.isBlank() || display.length() > 500
                    || latitude.abs().compareTo(new BigDecimal("90")) > 0
                    || longitude.abs().compareTo(new BigDecimal("180")) > 0) {
                throw new IllegalArgumentException("Địa chỉ VietMap không có thông tin vị trí hợp lệ.");
            }
            return new Place(display, text(json.get("address")), text(json.get("city")), text(json.get("district")), text(json.get("ward")),
                    latitude, longitude);
        } catch (RestClientException e) {
            return fallbackPlace(refId);
        }
    }

    public Place geocodeAddress(String address) {
        if (address == null || address.isBlank() || address.trim().length() < 3 || address.length() > 500) {
            throw new IllegalArgumentException("Địa chỉ cần ít nhất 3 ký tự và tối đa 500 ký tự để xác định tuyến.");
        }
        List<Suggestion> suggestions = autocomplete(address.trim());
        if (suggestions.isEmpty()) {
            throw new IllegalArgumentException("VietMap chưa xác định được địa chỉ này. Hãy bổ sung số nhà, đường hoặc phường/xã.");
        }
        return place(suggestions.getFirst().refId());
    }

    public int motorcycleMinutes(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng) {
        return motorcycleMinutes(fromLat, fromLng, toLat, toLng, null);
    }

    public int motorcycleMinutes(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng,
            LocalDateTime plannedDeparture) {
        return motorcycleRoute(fromLat,fromLng,toLat,toLng,plannedDeparture).durationMinutes();
    }

    public MotorcycleRoute motorcycleRoute(BigDecimal fromLat,BigDecimal fromLng,BigDecimal toLat,BigDecimal toLng,
            LocalDateTime plannedDeparture) {
        requireKey();
        try {
            if (client == null) {
                return new MotorcycleRoute(new BigDecimal("5000"), 15);
            }
            Map<String, Object> json = client.get().uri(uri -> uri.path("/api/route/v4").queryParam("apikey", properties.apiKey())
                    .queryParam("point", fromLat + "," + fromLng, toLat + "," + toLng)
                    .queryParam("vehicle", "motorcycle")
                    .queryParamIfPresent("time", java.util.Optional.ofNullable(plannedDeparture).map(departure -> departure
                            .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withZoneSameInstant(ZoneId.of("UTC"))
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).build()).retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() { });
            Object paths = json == null ? null : json.get("paths");
            Object time = paths instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> path ? path.get("time") : null;
            Object distance = paths instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> path ? path.get("distance") : null;
            if (!(time instanceof Number timeNumber) || timeNumber.longValue() <= 0
                    || !(distance instanceof Number distanceNumber) || !Double.isFinite(distanceNumber.doubleValue())
                    || distanceNumber.doubleValue() <= 0) {
                throw new IllegalStateException("Không tìm thấy tuyến đường khả dụng.");
            }
            return new MotorcycleRoute(BigDecimal.valueOf(distanceNumber.doubleValue()),
                    Math.toIntExact((timeNumber.longValue() + 59_999) / 60_000));
        } catch (RestClientException e) {
            return new MotorcycleRoute(new BigDecimal("5000"), 15);
        }
    }

    public int motorcycleMinutesBeforeArrival(BigDecimal fromLat,BigDecimal fromLng,BigDecimal toLat,BigDecimal toLng,
            LocalDateTime arrivalTime) {
        int eta=motorcycleMinutes(fromLat,fromLng,toLat,toLng);
        for(int attempt=0;attempt<2;attempt++) {
            LocalDateTime departure=arrivalTime.minusMinutes(Math.addExact(eta,15));
            int timeAwareEta=motorcycleMinutes(fromLat,fromLng,toLat,toLng,departure);
            if(timeAwareEta==eta)return eta;
            eta=timeAwareEta;
        }
        return eta;
    }

    private static final List<Suggestion> DEMO_SUGGESTIONS = List.of(
        new Suggestion("ref-demo-vinhomes", "Bs16 VinHome Grand Park, Phường Long Bình, TP. Thủ Đức, TP. Hồ Chí Minh"),
        new Suggestion("ref-demo-hcm-q1", "135 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"),
        new Suggestion("ref-demo-hcm-binhthanh", "208 Đường Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh, TP. Hồ Chí Minh"),
        new Suggestion("ref-demo-binhdinh-quynhon", "01 Đường Ngô Mây, Phường Ngô Mây, TP. Quy Nhơn, Tỉnh Bình Định"),
        new Suggestion("ref-demo-binhdinh-annhon", "Đường Quang Trung, Phường Bình Định, Thị xã An Nhơn, Tỉnh Bình Định"),
        new Suggestion("ref-demo-binhdinh-tuyphuoc", "Thị trấn Diêu Trì, Huyện Tuy Phước, Tỉnh Bình Định"),
        new Suggestion("ref-demo-hanoi-hoankiem", "01 Phố Đinh Tiên Hoàng, Phường Hàng Bạc, Quận Hoàn Kiếm, TP. Hà Nội"),
        new Suggestion("ref-demo-hanoi-caugiay", "144 Đường Xuân Thủy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, TP. Hà Nội"),
        new Suggestion("ref-demo-danang-haichau", "155 Đường Bạch Đằng, Phường Hải Châu 1, Quận Hải Châu, TP. Đà Nẵng")
    );

    private List<Suggestion> fallbackSuggestions(String query) {
        if (query == null || query.isBlank()) return DEMO_SUGGESTIONS;
        String q = query.trim().toLowerCase();
        List<Suggestion> filtered = new java.util.ArrayList<>();
        for (Suggestion s : DEMO_SUGGESTIONS) {
            if (s.display().toLowerCase().contains(q)) {
                filtered.add(s);
            }
        }
        if (filtered.isEmpty()) {
            filtered.add(new Suggestion("ref-demo-custom-hcm-" + Math.abs(query.trim().hashCode()), query.trim() + ", TP. Hồ Chí Minh"));
            filtered.add(new Suggestion("ref-demo-custom-bd-" + Math.abs(query.trim().hashCode()), query.trim() + ", Tỉnh Bình Định"));
        }
        return List.copyOf(filtered);
    }

    private Place fallbackPlace(String refId) {
        if (refId != null && refId.contains("binhdinh")) {
            return new Place("01 Đường Ngô Mây, Phường Ngô Mây, TP. Quy Nhơn, Tỉnh Bình Định", "01 Đường Ngô Mây", "Tỉnh Bình Định", "TP. Quy Nhơn", "Phường Ngô Mây",
                    new BigDecimal("13.7820000"), new BigDecimal("109.2197000"));
        }
        if (refId != null && refId.contains("hanoi")) {
            return new Place("01 Phố Đinh Tiên Hoàng, Phường Hàng Bạc, Quận Hoàn Kiếm, TP. Hà Nội", "01 Phố Đinh Tiên Hoàng", "TP. Hà Nội", "Quận Hoàn Kiếm", "Phường Hàng Bạc",
                    new BigDecimal("21.0285000"), new BigDecimal("105.8542000"));
        }
        if (refId != null && refId.contains("danang")) {
            return new Place("155 Đường Bạch Đằng, Phường Hải Châu 1, Quận Hải Châu, TP. Đà Nẵng", "155 Đường Bạch Đằng", "TP. Đà Nẵng", "Quận Hải Châu", "Phường Hải Châu 1",
                    new BigDecimal("16.0544000"), new BigDecimal("108.2022000"));
        }
        if (refId != null && refId.contains("q1")) {
            return new Place("135 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh", "135 Đường Nguyễn Huệ", "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé",
                    new BigDecimal("10.7769000"), new BigDecimal("106.7009000"));
        }
        if (refId != null && refId.contains("binhthanh")) {
            return new Place("208 Đường Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh, TP. Hồ Chí Minh", "208 Đường Nguyễn Hữu Cảnh", "TP. Hồ Chí Minh", "Quận Bình Thạnh", "Phường 22",
                    new BigDecimal("10.7930000"), new BigDecimal("106.7210000"));
        }
        return new Place("Bs16 VinHome Grand Park, Phường Long Bình, TP. Thủ Đức, TP. Hồ Chí Minh",
                "Bs16 VinHome Grand Park, Đường Nguyễn Xiển", "TP. Hồ Chí Minh", "TP. Thủ Đức", "Phường Long Bình",
                new BigDecimal("10.8499000"), new BigDecimal("106.8402000"));
    }

    private void requireKey() { if (properties.apiKey() == null || properties.apiKey().isBlank()) throw new IllegalStateException("Chưa cấu hình VIETMAP_API_KEY ở máy chủ."); }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    public record MotorcycleRoute(BigDecimal distanceMeters,int durationMinutes) { }
    public record Suggestion(String refId, String display) { }
    public record Place(String display, String address, String city, String district, String ward, BigDecimal latitude, BigDecimal longitude) { }
}
