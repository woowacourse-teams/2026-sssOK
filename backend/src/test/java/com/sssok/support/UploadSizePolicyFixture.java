package com.sssok.support;

import com.sssok.domain.file.UploadSizePolicy;

// 크기 제한이 관심사가 아닌 테스트가 쓰는 기본 상한. 운영 설정(application.yml)과 같은 값이다.
public final class UploadSizePolicyFixture {

    public static final UploadSizePolicy SIZE_POLICY =
        new UploadSizePolicy(20L * 1024 * 1024, 1024L * 1024 * 1024);

    private UploadSizePolicyFixture() {
    }
}
