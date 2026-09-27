package com.sssok.infrastructure.config;

import com.sssok.domain.file.DerivativeFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 사진에서 만드는 파생본 두 종류(목록 타일용 thumbnail, 상세 모달용 preview)의 포맷·해상도·품질.
//
// 이 값들은 감으로 고른 게 아니라 사진 24장을 JPEG / WebP / AVIF 로 각각 인코딩해 변환 시간 ·
// 파일 크기 · SSIM 을 재고 정했다. 측정표와 AVIF 를 고르지 않은 이유까지
// docs/backend/IMAGE_DERIVATIVE_FORMAT.md 에 있다. 바꾸기 전에 그 문서를 먼저 본다.
//
// 영상 썸네일은 ffmpeg 경로라 여기가 아니라 media.video 가 갖는다.
//
// 코드에는 기본값을 두지 않는다. 값의 단일 출처는 application.yml 이고, 여기는 잘못된 값을
// 거르기만 한다. 코드에도 기본값을 두면 yml 에서 한 줄이 빠졌을 때 다른 값으로 조용히 돌기
// 때문이다 — 특히 preview.quality 만 빠졌을 때 thumbnail 기준 기본값으로 떨어지는 식으로.
@ConfigurationProperties(prefix = "media.image")
public record DerivativeImageProperties(
    // 두 파생본이 공유하는 출력 포맷. 원본이 JPEG 이든 PNG 이든 이 포맷으로 통일한다.
    DerivativeFormat format,
    Variant thumbnail,
    Variant preview
) {

    public DerivativeImageProperties {
        require(format != null, "media.image.format 은 필수입니다.");
        require(thumbnail != null, "media.image.thumbnail 설정이 없습니다.");
        require(preview != null, "media.image.preview 설정이 없습니다.");
    }

    public record Variant(
        // 긴 변이 아니라 너비 기준이다. 원본이 이보다 작으면 늘리지 않는다.
        Integer maxWidth,
        // 0.0 ~ 1.0. 포맷마다 의미하는 바가 달라 포맷을 바꾸면 같이 다시 재야 한다.
        Float quality
    ) {

        public Variant {
            require(maxWidth != null, "파생본 너비(max-width)가 없습니다.");
            require(quality != null, "파생본 품질(quality)이 없습니다.");
            require(maxWidth > 0, "파생본 너비는 1px 이상이어야 합니다: " + maxWidth);
            require(quality > 0f && quality <= 1f,
                "파생본 품질은 0 초과 1 이하여야 합니다: " + quality);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
