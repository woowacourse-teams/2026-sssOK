package com.sssok.renewal.outgoing.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.domain.notification.DevicePlatform;
import com.sssok.renewal.domain.notification.DeviceToken;
import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.outgoing.config.FcmProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// FCM 과 Google OAuth 토큰 엔드포인트를 로컬 HTTP 서버로 대신해 실제로 오가는 요청·응답을 확인한다.
class FcmClientTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final DeviceToken DEVICE = DeviceToken.reconstruct(1L, "device-token-1", DevicePlatform.IOS);
    private static final RoomNotification NOTIFICATION =
        RoomNotification.uploadRequested(7L, "ABC123", "민수");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Request> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<Request> sendRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger issuedTokens = new AtomicInteger();
    private KeyPair keyPair;
    private HttpServer server;
    private volatile int sendStatus;
    private volatile String sendResponse;
    private volatile int tokenStatus;

    private record Request(String authorization, String contentLength, String body) {
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        sendStatus = 200;
        tokenStatus = 200;
        sendResponse = "{\"name\":\"projects/sssok-test/messages/1\"}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenRequests.add(read(exchange));
            if (tokenStatus != 200) {
                respond(exchange, tokenStatus, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid JWT\"}");
                return;
            }
            respond(exchange, 200, "{\"access_token\":\"access-" + issuedTokens.incrementAndGet()
                + "\",\"expires_in\":3599,\"token_type\":\"Bearer\"}");
        });
        server.createContext("/v1/projects/sssok-test/messages:send", exchange -> {
            sendRequests.add(read(exchange));
            respond(exchange, sendStatus, sendResponse);
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private FcmClient client(Clock clock) {
        String account = """
            {"type":"service_account","project_id":"sssok-test",
             "client_email":"fcm@sssok-test.iam.gserviceaccount.com",
             "private_key":"%s","token_uri":"%s/token"}
            """.formatted(pem().replace("\n", "\\n"), baseUrl());
        String encoded = Base64.getEncoder().encodeToString(account.getBytes(StandardCharsets.UTF_8));
        return new FcmClient(properties(encoded), objectMapper, clock);
    }

    private FcmProperties properties(String serviceAccount) {
        return new FcmProperties(serviceAccount, baseUrl(), Duration.ofHours(1),
            Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private String pem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    private static Request read(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new Request(exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("Content-Length"), body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void 알림_문구와_방_이동_정보를_담아_보낸다() throws Exception {
        PushSendResult result = client(fixed(NOW)).send(DEVICE, NOTIFICATION);

        assertThat(result).isEqualTo(PushSendResult.SENT);
        Request request = sendRequests.getFirst();
        assertThat(request.authorization()).isEqualTo("Bearer access-1");
        assertThat(request.contentLength())
            .isEqualTo(String.valueOf(request.body().getBytes(StandardCharsets.UTF_8).length));
        JsonNode message = objectMapper.readTree(request.body()).get("message");
        assertThat(message.get("token").asText()).isEqualTo("device-token-1");
        assertThat(message.at("/notification/title").asText()).isEqualTo("민수님이 알림을 보냈습니다");
        assertThat(message.at("/notification/body").asText()).isEqualTo("민수님의 사진을 올려주세요!");
        assertThat(message.at("/data/type").asText()).isEqualTo("UPLOAD_REQUESTED");
        assertThat(message.at("/data/roomId").asText()).isEqualTo("7");
        assertThat(message.at("/data/roomCode").asText()).isEqualTo("ABC123");
        assertThat(message.at("/data/url").asText()).isEqualTo("/rooms/ABC123/gallery");
        assertThat(message.at("/android/ttl").asText()).isEqualTo("3600s");
        assertThat(message.at("/apns/headers/apns-expiration").asText())
            .isEqualTo(String.valueOf(NOW.plusSeconds(3600).getEpochSecond()));
    }

    @Test
    void 서비스_계정_키로_서명한_JWT로_액세스_토큰을_받는다() throws Exception {
        client(fixed(NOW)).send(DEVICE, NOTIFICATION);

        Map<String, String> form = form(tokenRequests.getFirst().body());
        assertThat(form.get("grant_type")).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
        String[] jwt = form.get("assertion").split("\\.");
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(jwt[1]));
        assertThat(claims.get("iss").asText()).isEqualTo("fcm@sssok-test.iam.gserviceaccount.com");
        assertThat(claims.get("scope").asText()).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
        assertThat(claims.get("aud").asText()).isEqualTo(baseUrl() + "/token");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(3600);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
    }

    @Test
    void 액세스_토큰은_만료_전까지_재사용한다() {
        FcmClient client = client(fixed(NOW));

        client.send(DEVICE, NOTIFICATION);
        client.send(DEVICE, NOTIFICATION);

        assertThat(tokenRequests).hasSize(1);
        assertThat(sendRequests).extracting(Request::authorization).containsOnly("Bearer access-1");
    }

    @Test
    void 액세스_토큰이_만료될_즈음이면_새로_받는다() {
        MutableClock clock = new MutableClock(NOW);
        FcmClient client = client(clock);
        client.send(DEVICE, NOTIFICATION);

        clock.now = NOW.plus(Duration.ofMinutes(56));
        client.send(DEVICE, NOTIFICATION);

        assertThat(tokenRequests).hasSize(2);
        assertThat(sendRequests.getLast().authorization()).isEqualTo("Bearer access-2");
    }

    @Test
    void UNREGISTERED_응답이면_만료로_돌려준다() {
        sendStatus = 404;
        sendResponse = """
            {"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
             "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}
            """;

        assertThat(client(fixed(NOW)).send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.EXPIRED);
    }

    @Test
    void 다른_Firebase_프로젝트의_토큰이면_만료로_돌려준다() {
        sendStatus = 403;
        sendResponse = """
            {"error":{"code":403,"status":"PERMISSION_DENIED",
             "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"SENDER_ID_MISMATCH"}]}}
            """;

        assertThat(client(fixed(NOW)).send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.EXPIRED);
    }

    @Test
    void 액세스_토큰을_받지_못하면_기기_토큰을_만료로_보지_않고_잠시_재발급을_쉰다() {
        MutableClock clock = new MutableClock(NOW);
        FcmClient client = client(clock);
        tokenStatus = 400;

        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.FAILED);
        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.FAILED);
        assertThat(tokenRequests).hasSize(1);
        assertThat(sendRequests).isEmpty();

        tokenStatus = 200;
        clock.now = NOW.plusSeconds(31);
        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.SENT);
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void 토큰과_상관없는_실패는_만료로_보지_않는다() {
        sendStatus = 503;
        sendResponse = """
            {"error":{"code":503,"status":"UNAVAILABLE",
             "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNAVAILABLE"}]}}
            """;

        assertThat(client(fixed(NOW)).send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.FAILED);
    }

    @Test
    void 인증이_거절되면_다음_발송_때_액세스_토큰을_새로_받는다() {
        FcmClient client = client(fixed(NOW));
        sendStatus = 401;
        sendResponse = "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}";

        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.FAILED);
        sendStatus = 200;
        sendResponse = "{}";
        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.SENT);

        assertThat(tokenRequests).hasSize(2);
        assertThat(sendRequests.getLast().authorization()).isEqualTo("Bearer access-2");
    }

    @Test
    void APNs_인증이_거절되면_만료로_보지_않고_액세스_토큰도_유지한다() {
        FcmClient client = client(fixed(NOW));
        sendStatus = 401;
        sendResponse = """
            {"error":{"code":401,"status":"UNAUTHENTICATED",
             "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"THIRD_PARTY_AUTH_ERROR"}]}}
            """;

        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.FAILED);
        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.FAILED);

        assertThat(tokenRequests).hasSize(1);
        assertThat(sendRequests).extracting(Request::authorization).containsOnly("Bearer access-1");
    }

    @Test
    void 서비스_계정_키가_없으면_보내지_않는다() {
        FcmClient client = new FcmClient(properties(""), objectMapper, fixed(NOW));

        assertThat(client.enabled()).isFalse();
        assertThat(client.send(DEVICE, NOTIFICATION)).isEqualTo(PushSendResult.SKIPPED);
        assertThat(sendRequests).isEmpty();
    }

    @Test
    void 서비스_계정_키_형식이_틀리면_기동을_막는다() {
        assertThatThrownBy(() -> new FcmClient(properties("not-a-key"), objectMapper, fixed(NOW)))
            .isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, String> form(String body) {
        return Arrays.stream(body.split("&"))
            .map(pair -> pair.split("=", 2))
            .collect(Collectors.toMap(
                pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
