package com.sssok.application.mediafolder;

import java.util.List;

// 요청받은 mediaId를 실제로 존재하는 것과 존재하지 않는 것으로 나눈 결과.
public record MediaExistence(List<Long> existingIds, List<Long> notFoundIds) {
}
