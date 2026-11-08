package com.housekeeping.management.service;

import com.housekeeping.management.config.VietmapProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
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
            List<Map<String, Object>> json = client.get().uri(uri -> uri.path("/api/autocomplete/v4")
                    .queryParam("apikey", properties.apiKey()).queryParam("text", query).build())
                    .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() { });
            if (json == null) return List.of();
            java.util.ArrayList<Suggestion> results = new java.util.ArrayList<>();
            json.forEach(row -> {
                String id = text(row.get("ref_id"));
                String display = text(row.get("display"));
                if (id != null && display != null) results.add(new Suggestion(id, display));
            });
            return List.copyOf(results);
        } catch (RestClientException e) { throw new IllegalStateException("VietMap đang bận. Vui lòng thử lại."); }
    }

    public Place place(String refId) {
        requireKey();
        try {
            Map<String, Object> json = client.get().uri(uri -> uri.path("/api/place/v4").queryParam("apikey", properties.apiKey())
                    .queryParam("refid", refId).build()).retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() { });
            if (json == null || json.get("lat") == null || json.get("lng") == null) throw new IllegalArgumentException("Địa chỉ VietMap không có tọa độ hợp lệ.");
            return new Place(text(json.get("display")), text(json.get("address")), text(json.get("city")), text(json.get("district")), text(json.get("ward")),
                    new BigDecimal(json.get("lat").toString()), new BigDecimal(json.get("lng").toString()));
        } catch (RestClientException e) { throw new IllegalStateException("Không xác minh được địa chỉ VietMap. Vui lòng thử lại."); }
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
        } catch (RestClientException e) { throw new IllegalStateException("Không tính được tuyến VietMap. Vui lòng thử lại."); }
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

    private void requireKey() { if (properties.apiKey() == null || properties.apiKey().isBlank()) throw new IllegalStateException("Chưa cấu hình VIETMAP_API_KEY ở máy chủ."); }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    public record MotorcycleRoute(BigDecimal distanceMeters,int durationMinutes) { }
    public record Suggestion(String refId, String display) { }
    public record Place(String display, String address, String city, String district, String ward, BigDecimal latitude, BigDecimal longitude) { }
}
