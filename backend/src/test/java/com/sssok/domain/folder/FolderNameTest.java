package com.sssok.domain.folder;

import com.sssok.domain.folder.exception.InvalidFolderNameException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FolderNameTest {

    @Test
    void 정상적인_이름은_생성된다() {
        FolderName name = new FolderName("맛집");

        assertThat(name.value()).isEqualTo("맛집");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void 빈_문자열이거나_공백뿐이면_예외(String invalidValue) {
        assertThatThrownBy(() -> new FolderName(invalidValue))
            .isInstanceOf(InvalidFolderNameException.class);
    }

    @Test
    void 최대_길이를_넘으면_예외() {
        String tooLong = "가".repeat(21);

        assertThatThrownBy(() -> new FolderName(tooLong))
            .isInstanceOf(InvalidFolderNameException.class);
    }
}
