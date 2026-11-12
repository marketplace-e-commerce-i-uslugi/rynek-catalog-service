package pl.rynek.catalog.engagement;

import java.time.Duration;
import java.util.List;
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

/** Liczba pytań pod ofertą z engagement-service ({@code GET /internal/questions/counts}). Bez engagement: 0. */
@Component
public class QuestionCountClient {

    private static final Logger log = LoggerFactory.getLogger(QuestionCountClient.class);

    private final RestClient http;

    public QuestionCountClient(@Value("${rynek.engagement.url}") String engagementUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().baseUrl(engagementUrl).requestFactory(requests).build();
    }

    public int of(UUID offerId) {
        try {
            List<Count> found = http.get().uri(uri -> uri.path("/internal/questions/counts").queryParam("offerIds", offerId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return found == null || found.isEmpty() ? 0 : found.getFirst().count();
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Brak liczby pytań oferty {}: {}", offerId, e.getMessage());
            return 0;
        }
    }

    public record Count(UUID offerId, int count) {
    }
}
