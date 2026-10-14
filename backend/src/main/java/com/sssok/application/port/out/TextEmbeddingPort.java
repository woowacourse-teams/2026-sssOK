package com.sssok.application.port.out;

import java.time.Duration;
import java.util.List;

// 구현체 내부의 자동 재시도는 금지한다. 작업 재시도는 DB 워커에서만 수행한다.
// 호출 실패는 AiCallException으로 미처리 확정/영구 실패/결과 불명을 구분한다.
public interface TextEmbeddingPort {
    // 이미지 설명과 검색어에 동일한 구현체·모델을 사용한다.
    Embedding embed(String text, Duration timeout);

    record Embedding(List<Double> values, String model) {
    }
}
