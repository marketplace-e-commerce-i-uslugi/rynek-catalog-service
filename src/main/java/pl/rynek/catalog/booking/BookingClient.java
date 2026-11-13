package pl.rynek.catalog.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Najbliższe wolne terminy usług z booking-service ({@code GET /internal/services/next-slots}). Bez booking: brak terminów. */
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    private final RestClient http;

    public BookingClient(@Value("${rynek.booking.url}") String bookingUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().baseUrl(bookingUrl).requestFactory(requests).build();
    }

    /** Mapa oferta → najbliższy termin; oferty bez wolnego terminu (i awaria booking) są pominięte. */
    public Map<UUID, Instant> nextSlots(Collection<UUID> offerIds) {
        List<UUID> ids = offerIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<UUID, Instant> found = http.get()
                    .uri(uri -> uri.path("/internal/services/next-slots").queryParam("ids", ids).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return found == null ? Map.of() : found;
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Brak najbliższych terminów usług: {}", e.getMessage());
            return Map.of();
        }
    }
}
