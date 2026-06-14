package com.cimportal.lark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LarkClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private LarkClient client;

    @BeforeEach
    void setup() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LarkClient(builder.build(), MAPPER);
    }

    @Test
    void tokenSuccessReturnsTokenAndExpiry() {
        String json = """
            {"code":0,"msg":"ok","tenant_access_token":"t-abc123","expire":7200}
            """;
        server.expect(requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<LarkClient.TokenInfo> info =
            client.tenantAccessToken("https://open.feishu.cn", "app", "secret");

        assertThat(info).contains(new LarkClient.TokenInfo("t-abc123", 7200));
        server.verify();
    }

    @Test
    void tokenNonZeroCodeYieldsEmpty() {
        String json = """
            {"code":99991663,"msg":"app not found"}
            """;
        server.expect(requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<LarkClient.TokenInfo> info =
            client.tenantAccessToken("https://open.feishu.cn", "app", "secret");

        assertThat(info).isEmpty();
        server.verify();
    }

    @Test
    void tokenServerErrorYieldsEmptyAndDoesNotThrow() {
        server.expect(requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
            .andRespond(withServerError());

        Optional<LarkClient.TokenInfo> info =
            client.tenantAccessToken("https://open.feishu.cn", "app", "secret");

        assertThat(info).isEmpty();
        server.verify();
    }

    @Test
    void tokenBlankInputMakesNoHttpCall() {
        Optional<LarkClient.TokenInfo> info =
            client.tenantAccessToken("https://open.feishu.cn", "  ", "secret");

        assertThat(info).isEmpty();
        server.verify();
    }

    @Test
    void sendTextSuccessSendsBearerAndJsonContent() {
        server.expect(requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=email"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t-abc123"))
            .andExpect(jsonPath("$.receive_id").value("a@b.com"))
            .andExpect(jsonPath("$.msg_type").value("text"))
            .andExpect(request -> {
                byte[] raw = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsBytes();
                JsonNode body = MAPPER.readTree(raw);
                String content = body.get("content").asText();
                // content must itself be a JSON string of {"text": "..."}
                JsonNode parsed = MAPPER.readTree(content);
                assertThat(parsed.get("text").asText()).isEqualTo("hello \"world\"");
            })
            .andRespond(withSuccess("{\"code\":0,\"msg\":\"ok\"}", MediaType.APPLICATION_JSON));

        boolean ok = client.sendText(
            "https://open.feishu.cn", "t-abc123", "email", "a@b.com", "hello \"world\"");

        assertThat(ok).isTrue();
        server.verify();
    }

    @Test
    void sendTextNonZeroCodeYieldsFalse() {
        server.expect(requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=email"))
            .andRespond(withSuccess("{\"code\":230002,\"msg\":\"bad receive id\"}", MediaType.APPLICATION_JSON));

        boolean ok = client.sendText(
            "https://open.feishu.cn", "t-abc123", "email", "a@b.com", "hi");

        assertThat(ok).isFalse();
        server.verify();
    }
}
