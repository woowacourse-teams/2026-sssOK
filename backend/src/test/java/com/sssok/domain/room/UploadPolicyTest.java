package com.sssok.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UploadPolicyTest {

    @Test
    void ANYONE은_누구나_업로드할_수_있다() {
        assertThat(UploadPolicy.ANYONE.allows(true)).isTrue();
        assertThat(UploadPolicy.ANYONE.allows(false)).isTrue();
    }

    @Test
    void HOST_ONLY는_방장만_업로드할_수_있다() {
        assertThat(UploadPolicy.HOST_ONLY.allows(true)).isTrue();
        assertThat(UploadPolicy.HOST_ONLY.allows(false)).isFalse();
    }
}
