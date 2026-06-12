package com.cimportal.dutyline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Proxies the external "on-duty management" system. Base URL and API key are
 * editable at runtime (stored in security_setting) and therefore passed per call
 * rather than fixed at bean creation time.
 */
@Component
public class OnDutyClient {

    private static final Logger log = LoggerFactory.getLogger(OnDutyClient.class);

    private final RestClient restClient;

    /** Production constructor: applies a request factory with short timeouts. */
    @Autowired
    public OnDutyClient(RestClient.Builder builder) {
        this(builder.requestFactory(timeoutRequestFactory()).build());
    }

    /** Seam for tests: inject a RestClient (e.g. bound to MockRestServiceServer). */
    OnDutyClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        return f;
    }

    /**
     * Returns the first on-duty person for the given section code, or empty when
     * any input is blank, the external array is empty, or the call fails. Never throws.
     */
    public Optional<OnDutyPerson> primaryByScheduleName(String baseUrl, String apiKey, String scheduleName) {
        if (isBlank(baseUrl) || isBlank(apiKey) || isBlank(scheduleName)) {
            return Optional.empty();
        }
        try {
            List<ExternalUser> users = restClient.get()
                .uri(baseUrl + "/api/external/on-duty?scheduleName={n}", scheduleName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<ExternalUser>>() { });

            if (users == null || users.isEmpty()) {
                return Optional.empty();
            }
            ExternalUser first = users.get(0);
            if (first == null || isBlank(first.phone())) {
                return Optional.empty();
            }
            return Optional.of(new OnDutyPerson(first.nickname(), first.phone()));
        } catch (Exception e) {
            log.warn("On-duty lookup failed for scheduleName={}: {}", scheduleName, e.toString());
            return Optional.empty();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExternalUser(String nickname, String phone) { }
}
