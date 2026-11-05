package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository.IdempotencyRecord;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizeIdempotencyTest {
    private final OrganizeJobRepository repository = mock(OrganizeJobRepository.class);
    private final OrganizeIdempotency idempotency = new OrganizeIdempotency(repository);

    @ParameterizedTest
    @ValueSource(strings = {"0123456789abcdef", "a-b_c-d_e-f_g-h_", "ABCDEFGHIJKLMNOP", "7b44258a-81c7-4e75-9a85-16e5df65ad61"})
    void 영숫자_하이픈_밑줄_16자에서_64자_키를_받는다(String key) {
        assertThat(idempotency.requireKey(key)).isEqualTo(key);
        assertThat(idempotency.requireKey("a".repeat(64))).hasSize(64);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"짧은키", "0123456789abcde", "has space in the key!", "한글키한글키한글키한글키한글키"})
    void 없거나_짧거나_형식이_틀린_키는_400이다(String key) {
        assertThatThrownBy(() -> idempotency.requireKey(key)).isInstanceOfSatisfying(SssOkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
    }

    @Test
    void 키가_65자를_넘으면_거절한다() {
        assertThatThrownBy(() -> idempotency.requireKey("a".repeat(65))).isInstanceOf(SssOkException.class);
    }

    @Test
    void 해시는_같은_입력에_같고_다른_입력에_다르다() {
        assertThat(OrganizeIdempotency.hash("BUTTON:PERSON")).isEqualTo(OrganizeIdempotency.hash("BUTTON:PERSON"));
        assertThat(OrganizeIdempotency.hash("BUTTON:PERSON")).isNotEqualTo(OrganizeIdempotency.hash("BUTTON:SIMILAR"));
        assertThat(OrganizeIdempotency.hash("BUTTON:PERSON")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void 기록이_없으면_빈_값이다() {
        given(repository.findIdempotency(1L, 2L, "START", "k")).willReturn(Optional.empty());

        assertThat(idempotency.find(1L, 2L, "START", "k", "h")).isEmpty();
    }

    @Test
    void 같은_해시의_기록은_작업과_응답을_돌려준다() {
        given(repository.findIdempotency(1L, 2L, "APPLY", "k"))
            .willReturn(Optional.of(new IdempotencyRecord("h", 9L, "[]")));

        assertThat(idempotency.find(1L, 2L, "APPLY", "k", "h")).get()
            .satisfies(replay -> {
                assertThat(replay.jobId()).isEqualTo(9L);
                assertThat(replay.response()).isEqualTo("[]");
            });
    }

    @Test
    void 다른_해시의_기록은_키_충돌이다() {
        given(repository.findIdempotency(1L, 2L, "START", "k"))
            .willReturn(Optional.of(new IdempotencyRecord("other", 9L, null)));

        assertThatThrownBy(() -> idempotency.find(1L, 2L, "START", "k", "h")).isInstanceOfSatisfying(
            SssOkException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }
}
