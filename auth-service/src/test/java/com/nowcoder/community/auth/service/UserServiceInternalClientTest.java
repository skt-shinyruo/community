package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.UserServiceClientProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceInternalClientTest {

    @Test
    void activateShouldReturnDownstreamResult() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        UserServiceClientProperties props = new UserServiceClientProperties();
        props.setBaseUrl("http://user-service");

        UserServiceInternalClient client = new UserServiceInternalClient(restTemplate, new SimpleMeterRegistry(), props);

        String body = """
                {
                  "code": 0,
                  "message": "OK",
                  "data": { "result": 1 },
                  "traceId": "trace-1",
                  "timestamp": 1700000000000
                }
                """;

        server.expect(requestTo("http://user-service/internal/users/123/activate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        int r = client.activate(123, "code");
        assertThat(r).isEqualTo(1);
        server.verify();
    }

    @Test
    void activateShouldReturn2WhenDownstreamDataIsNull() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        UserServiceClientProperties props = new UserServiceClientProperties();
        props.setBaseUrl("http://user-service");

        UserServiceInternalClient client = new UserServiceInternalClient(restTemplate, new SimpleMeterRegistry(), props);

        String body = """
                {
                  "code": 0,
                  "message": "OK",
                  "data": null,
                  "traceId": "trace-2",
                  "timestamp": 1700000000000
                }
                """;

        server.expect(requestTo("http://user-service/internal/users/123/activate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        int r = client.activate(123, "code");
        assertThat(r).isEqualTo(2);
        server.verify();
    }
}
