package com.cimportal.dutyline;

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

class OnDutyClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OnDutyClient client;

    @BeforeEach
    void setup() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OnDutyClient(builder.build());
    }

    @Test
    void returnsFirstPersonAndIgnoresUnknownFields() {
        String json = """
            [
              {"id":49954,"username":"E0026701","nickname":"韩逸水","role":"IT",
               "email":"a@b.com","phone":"16601822375","gender":"FEMALE","level":null,
               "reportTo":{"id":1,"username":"boss"},
               "department":{"id":7,"name":"IT"},"active":true},
              {"id":49955,"nickname":"someone else","phone":"13900000000"}
            ]
            """;
        server.expect(requestTo("https://duty.example.com/api/external/on-duty?scheduleName=IT"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-key"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<OnDutyPerson> p =
            client.primaryByScheduleName("https://duty.example.com", "secret-key", "IT");

        assertThat(p).contains(new OnDutyPerson("韩逸水", "16601822375"));
        server.verify();
    }

    @Test
    void emptyArrayYieldsEmpty() {
        server.expect(requestTo("https://duty.example.com/api/external/on-duty?scheduleName=IT"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<OnDutyPerson> p =
            client.primaryByScheduleName("https://duty.example.com", "secret-key", "IT");

        assertThat(p).isEmpty();
        server.verify();
    }

    @Test
    void serverErrorYieldsEmptyAndDoesNotThrow() {
        server.expect(requestTo("https://duty.example.com/api/external/on-duty?scheduleName=IT"))
            .andRespond(withServerError());

        Optional<OnDutyPerson> p =
            client.primaryByScheduleName("https://duty.example.com", "secret-key", "IT");

        assertThat(p).isEmpty();
        server.verify();
    }

    @Test
    void blankScheduleNameMakesNoHttpCall() {
        // No server.expect(...) registered → any HTTP call would fail verification.
        Optional<OnDutyPerson> p =
            client.primaryByScheduleName("https://duty.example.com", "secret-key", "  ");

        assertThat(p).isEmpty();
        server.verify();
    }
}
