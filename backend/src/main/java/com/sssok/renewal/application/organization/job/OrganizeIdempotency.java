package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 같은 요청의 재전송을 가려낸다. 키는 (회원, 방, 연산, 키)로 구분하고 입력 해시가 다르면 거절한다.
// 성공한 요청만 기록하므로 검증 실패나 429·503 뒤에는 같은 키로 다시 보낼 수 있다.
@Component
@RequiredArgsConstructor
public class OrganizeIdempotency {
    public static final String START = "START";
    public static final String RETRY = "RETRY";
    public static final String APPLY = "APPLY";

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private final OrganizeJobRepository repository;

    public record Replay(Long jobId, String response) {
    }

    public String requireKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new OrganizeJobException(ErrorCode.INVALID_PARAM, "Idempotency-Key 는 영숫자, -, _ 로 16~64자여야 합니다");
        }
        return key;
    }

    // 이미 처리한 요청이면 기록을 돌려주고, 같은 키에 다른 입력이면 409 로 거절한다.
    public Optional<Replay> find(Long memberId, Long roomId, String operation, String key, String requestHash) {
        return repository.findIdempotency(memberId, roomId, operation, key).map(record -> {
            if (!record.requestHash().equals(requestHash)) {
                throw new OrganizeJobException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return new Replay(record.jobId(), record.response());
        });
    }

    // 작업을 바꾸는 트랜잭션 안에서 부른다. 같은 키가 동시에 오면 유니크 제약이 한쪽을 거절한다.
    public void record(Long memberId, Long roomId, String operation, String key, String requestHash, Long jobId,
                       String response, Instant now) {
        repository.saveIdempotency(memberId, roomId, operation, key, requestHash, jobId, response, now);
    }

    public static String hash(String canonicalInput) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", impossible);
        }
    }
}
