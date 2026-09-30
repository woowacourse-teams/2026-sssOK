package com.sssok.infrastructure.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0"
)
@AutoConfigureObservability
@ActiveProfiles("test")
class ActuatorEndpointTest {

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int applicationPort;

    @LocalManagementPort
    int managementPort;

    @Test
    void liveness_상태를_확인할_수_있다() {
        ResponseEntity<String> response = getManagement("/actuator/health/liveness");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readiness_상태를_확인할_수_있다() {
        ResponseEntity<String> response = getManagement("/actuator/health/readiness");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void Prometheus_형식의_메트릭을_확인할_수_있다() {
        ResponseEntity<String> response = getManagement("/actuator/prometheus");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    void HTTP_응답_시간을_Histogram으로_확인할_수_있다() {
        ResponseEntity<String> apiResponse = restTemplate.getForEntity(
            "http://localhost:" + applicationPort + "/version", String.class);
        assertThat(apiResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(getManagement("/actuator/prometheus").getBody())
            .contains("http_server_requests_seconds_bucket");
    }

    @Test
    void 애플리케이션_정보를_확인할_수_있다() {
        assertThat(getManagement("/actuator/info").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void 일반_API_포트에서는_Actuator에_접근할_수_없다() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + applicationPort + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> getManagement(String path) {
        return restTemplate.getForEntity("http://localhost:" + managementPort + path, String.class);
    }
}
