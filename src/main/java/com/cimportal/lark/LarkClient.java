package com.cimportal.lark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client for the Lark (Feishu) open APIs. Base URL and app credentials are
 * editable at runtime (stored in security_setting) and therefore passed per call.
 * Mirrors {@code OnDutyClient}: short timeouts, never throws (catch → warn → empty/false).
 */
@Component
public class LarkClient {

    private static final Logger log = LoggerFactory.getLogger(LarkClient.class);
    private static final int DEFAULT_EXPIRE_SECONDS = 7200;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** Production constructor: applies a request factory with short timeouts. */
    @Autowired
    public LarkClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this(builder.requestFactory(timeoutRequestFactory()).build(), objectMapper);
    }

    /** Seam for tests: inject a RestClient (e.g. bound to MockRestServiceServer). */
    LarkClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        return f;
    }

    /**
     * Fetches a tenant_access_token. Returns empty when any input is blank, the
     * response code is non-zero, the token is missing, or the call fails. Never throws.
     */
    public Optional<TokenInfo> tenantAccessToken(String baseUrl, String appId, String appSecret) {
        if (isBlank(baseUrl) || isBlank(appId) || isBlank(appSecret)) {
            return Optional.empty();
        }
        try {
            TokenResp r = restClient.post()
                .uri(baseUrl + "/open-apis/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("app_id", appId, "app_secret", appSecret))
                .retrieve()
                .body(TokenResp.class);

            if (r != null && r.code() == 0 && r.tenant_access_token() != null) {
                int expire = r.expire() != null ? r.expire() : DEFAULT_EXPIRE_SECONDS;
                return Optional.of(new TokenInfo(r.tenant_access_token(), expire));
            }
            log.warn("Lark tenant_access_token failed: code={}", r == null ? "null" : r.code());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Lark tenant_access_token call failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Sends a text message. Returns true only when the response code is 0.
     * Returns false on a non-zero code or any failure. Never throws.
     */
    public boolean sendText(String baseUrl, String token, String receiveIdType, String receiveId, String text) {
        try {
            String content = objectMapper.writeValueAsString(Map.of("text", text));
            SendResp r = restClient.post()
                .uri(baseUrl + "/open-apis/im/v1/messages?receive_id_type={t}", receiveIdType)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("receive_id", receiveId, "msg_type", "text", "content", content))
                .retrieve()
                .body(SendResp.class);

            if (r != null && r.code() == 0) {
                return true;
            }
            log.warn("Lark sendText failed: code={}, msg={}",
                r == null ? "null" : r.code(), r == null ? null : r.msg());
            return false;
        } catch (Exception e) {
            log.warn("Lark sendText call failed: {}", e.toString());
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record TokenInfo(String token, int expireSeconds) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResp(int code, String tenant_access_token, Integer expire) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SendResp(int code, String msg) { }
}
