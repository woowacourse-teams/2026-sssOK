package com.sssok.application.port.out;

import java.time.Duration;
import java.util.List;

public interface TextEmbeddingPort {
    // 이미지 설명과 검색어에 동일한 구현체·모델을 사용한다.
    Embedding embed(String text, Duration timeout);

    record Embedding(List<Double> values, String model) {}
}
