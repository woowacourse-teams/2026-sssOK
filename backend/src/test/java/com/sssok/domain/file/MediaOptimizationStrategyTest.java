package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.OptimizationPlan.OptimizationStep;
import org.junit.jupiter.api.Test;

class MediaOptimizationStrategyTest {

    @Test
    void 일반_이미지는_1600px_0_8_로_줄인_뒤_필요하면_1200px_0_7_로_재압축한다() {
        OptimizationPlan plan =
            MediaOptimizationStrategy.decide(MediaType.JPEG, FileSize.ofMegabytes(5));

        assertThat(plan.skipped()).isFalse();
        assertThat(plan.steps()).containsExactly(
            new OptimizationStep(1600, 0.8),
            new OptimizationStep(1200, 0.7)
        );
    }

    @Test
    void GIF_는_애니메이션_보존을_위해_압축하지_않는다() {
        OptimizationPlan plan =
            MediaOptimizationStrategy.decide(MediaType.GIF, FileSize.ofMegabytes(5));

        assertThat(plan.skipped()).isTrue();
        assertThat(plan.steps()).isEmpty();
    }

    @Test
    void 크기가_500KB_미만인_원본은_압축하지_않는다() {
        OptimizationPlan plan =
            MediaOptimizationStrategy.decide(MediaType.JPEG, FileSize.ofKilobytes(499));

        assertThat(plan.skipped()).isTrue();
    }

    @Test
    void 크기가_정확히_500KB_이면_압축_대상이다() {
        OptimizationPlan plan =
            MediaOptimizationStrategy.decide(MediaType.JPEG, FileSize.ofKilobytes(500));

        assertThat(plan.skipped()).isFalse();
    }

    @Test
    void 영상은_리사이즈_대상이_아니다() {
        OptimizationPlan plan =
            MediaOptimizationStrategy.decide(MediaType.MP4, FileSize.ofMegabytes(100));

        assertThat(plan.skipped()).isTrue();
    }

    @Test
    void 계획의_단계_목록은_바꿀_수_없다() {
        OptimizationPlan plan =
            MediaOptimizationStrategy.decide(MediaType.PNG, FileSize.ofMegabytes(2));

        assertThat(plan.steps()).isUnmodifiable();
    }
}