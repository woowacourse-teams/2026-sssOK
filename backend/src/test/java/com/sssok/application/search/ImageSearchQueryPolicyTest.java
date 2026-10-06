package com.sssok.application.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.application.search.exception.ImageSearchUnavailableException;
import com.sssok.infrastructure.config.ImageSearchQueryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ImageSearchQueryPolicyTest {
    @Test
    void 임계값을_정하지_않으면_외부_호출_없이_503_예외를_반환한다() {
        var provider = mock(TextEmbeddingPort.class);
        var factory = new StaticListableBeanFactory();
        factory.addBean("embedding", provider);
        var assembler = mock(ImageSearchResultAssembler.class);
        var service = new SearchImagesService(factory.getBeanProvider(TextEmbeddingPort.class), assembler,
            new ImageSearchQueryProperties(null, null), true);
        assertThatThrownBy(() -> service.search(1L, "사진"))
            .isInstanceOf(ImageSearchUnavailableException.class);
        verifyNoInteractions(provider, assembler);
    }

    @Test
    void 유사도_범위를_벗어나거나_비정상인_임계값을_거절한다() {
        for (double threshold : new double[] { -1.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThatThrownBy(() -> new ImageSearchQueryProperties(threshold, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
