package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housekeeping.management.config.VietmapProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class VietmapClientTest {
    @Test
    void requiresBackendKeyAndRejectsInvalidAutocompleteInputAtServiceBoundary() {
        VietmapClient client = new VietmapClient(new VietmapProperties("", "https://example.invalid"));
        assertThatThrownBy(() -> client.autocomplete("Quận 1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("VIETMAP_API_KEY");
        assertThatThrownBy(() -> client.motorcycleMinutes(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("VIETMAP_API_KEY");
    }

    @Test
    void parsesVietmapAutocompletePlaceAndMotorcycleRouteResponsesWithMockedHttp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://maps.vietmap.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VietmapClient client = new VietmapClient(new VietmapProperties("server-test-key", "https://maps.vietmap.vn"), builder.build());

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/autocomplete/v4"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"ref_id\":\"ref-1\",\"display\":\"12 Example Street\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/place/v4"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"display\":\"12 Example Street\",\"address\":\"12 Example Street\",\"city\":\"HCMC\",\"ward\":\"Ward 1\",\"lat\":10.1,\"lng\":106.2}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/route/v4"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"paths\":[{\"time\":3600000}]}", MediaType.APPLICATION_JSON));
        assertThat(client.autocomplete("12 Example")).containsExactly(new VietmapClient.Suggestion("ref-1", "12 Example Street"));
        assertThat(client.place("ref-1").latitude()).isEqualByComparingTo("10.1");
        assertThat(client.motorcycleMinutes(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE)).isEqualTo(60);
        server.verify();
    }
}
