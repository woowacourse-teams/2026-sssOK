package com.sssok.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.room.exception.InvalidEntryPasswordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EntryPasswordTest {

    private static final RoomCode CODE = new RoomCode("A3F9K2M7");
    private static final RoomCode OTHER_CODE = new RoomCode("B4G8L3N6");
    private static final String RAW = "sssok2026";

    @Test
    void 평문을_그대로_저장하지_않는다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);

        assertThat(password.hash()).isNotEqualTo(RAW);
        assertThat(password.hash()).doesNotContain(RAW);
    }

    @Test
    void 해시는_SHA_256_결과인_hex_64자다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);

        assertThat(password.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    void 같은_평문과_같은_방_코드는_같은_해시를_만든다() {
        assertThat(EntryPassword.of(RAW, CODE).hash())
            .isEqualTo(EntryPassword.of(RAW, CODE).hash());
    }

    @Test
    void 방_코드를_salt로_쓰므로_같은_평문이라도_방이_다르면_해시가_다르다() {
        assertThat(EntryPassword.of(RAW, CODE).hash())
            .isNotEqualTo(EntryPassword.of(RAW, OTHER_CODE).hash());
    }

    @Test
    void 같은_평문이면_검증에_성공한다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);

        assertThat(password.matches(RAW, CODE)).isTrue();
    }

    @Test
    void 다른_평문이면_검증에_실패한다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);

        assertThat(password.matches("wrong-one", CODE)).isFalse();
    }

    @Test
    void 평문이_맞아도_방_코드가_다르면_검증에_실패한다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);

        assertThat(password.matches(RAW, OTHER_CODE)).isFalse();
    }

    @Test
    void 검증할_평문이_null이면_예외가_아니라_실패로_처리한다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);

        assertThat(password.matches(null, CODE)).isFalse();
    }

    @Test
    void 길이_제한을_벗어난_평문으로_검증하면_예외가_아니라_실패로_처리한다() {
        EntryPassword password = EntryPassword.of(RAW, CODE);
        assertThat(password.matches("ab", CODE)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "abc",
    })
    void 생성할_평문이_너무_짧으면_예외(String rawPassword) {
        assertThatThrownBy(() -> EntryPassword.of(rawPassword, CODE))
            .isInstanceOf(InvalidEntryPasswordException.class);
    }

    @Test
    void 생성할_평문이_너무_길면_예외() {
        String tooLong = "a".repeat(21);

        assertThatThrownBy(() -> EntryPassword.of(tooLong, CODE))
            .isInstanceOf(InvalidEntryPasswordException.class);
    }

    @Test
    void 생성할_평문이_null이면_예외() {
        assertThatThrownBy(() -> EntryPassword.of(null, CODE))
            .isInstanceOf(InvalidEntryPasswordException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-hash",
        "ABCDEF",
        "0123456789abcdef",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdefa",
    })
    void 해시_형식이_아닌_값으로_복원하면_예외(String hash) {
        assertThatThrownBy(() -> new EntryPassword(hash))
            .isInstanceOf(InvalidEntryPasswordException.class);
    }

    @Test
    void 앞뒤_공백은_무시하고_같은_암호로_본다() {
        EntryPassword password = EntryPassword.of("  " + RAW + "  ", CODE);

        assertThat(password.matches(RAW, CODE)).isTrue();
        assertThat(password.matches("  " + RAW + "  ", CODE)).isTrue();
    }

    @Test
    void 공백을_뺀_길이가_기준에_못_미치면_예외() {
        assertThatThrownBy(() -> EntryPassword.of("  ab  ", CODE))
            .isInstanceOf(InvalidEntryPasswordException.class);
    }

    @Test
    void 저장된_해시로_복원하면_같은_평문을_검증할_수_있다() {
        String storedHash = EntryPassword.of(RAW, CODE).hash();

        EntryPassword restored = new EntryPassword(storedHash);

        assertThat(restored.matches(RAW, CODE)).isTrue();
    }
}
