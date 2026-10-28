package com.sssok.renewal.outgoing.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

// Firebase 콘솔에서 받은 서비스 계정 키(JSON). 환경변수에 줄바꿈 없이 넣을 수 있게 base64 로 감싼 값도 받는다.
// 잘못된 값이면 기동을 막는다. 비워 둔 것과 달리 설정 실수라 조용히 꺼지면 알아채기 어렵다.
public record FcmServiceAccount(String projectId, String clientEmail, RSAPrivateKey privateKey, String tokenUri) {

    public static FcmServiceAccount parse(String value, ObjectMapper objectMapper) {
        try {
            String json = value.trim().startsWith("{")
                ? value
                : new String(Base64.getMimeDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(json);
            return new FcmServiceAccount(
                required(node, "project_id"),
                required(node, "client_email"),
                privateKey(required(node, "private_key")),
                required(node, "token_uri"));
        } catch (IOException | IllegalArgumentException | GeneralSecurityException e) {
            // 메시지에 키 내용이 섞일 수 있어 원인 예외를 붙이지 않는다.
            throw new IllegalStateException("FCM 서비스 계정 키 형식이 올바르지 않습니다: " + e.getClass().getSimpleName());
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " 가 없습니다");
        }
        return value.asText();
    }

    private static RSAPrivateKey privateKey(String pem) throws GeneralSecurityException {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
