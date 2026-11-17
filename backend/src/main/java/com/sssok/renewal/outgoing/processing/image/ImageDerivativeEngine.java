package com.sssok.renewal.outgoing.processing.image;

import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImages;

/** 선택한 엔진이 지원하지 않는 입력은 기존 Java 처리 경로를 사용한다. */
public interface ImageDerivativeEngine {
    boolean supports(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview);
    DerivedImages derive(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview);
}
