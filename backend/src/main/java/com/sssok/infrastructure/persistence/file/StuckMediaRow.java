package com.sssok.infrastructure.persistence.file;

// 썸네일 회수 배치가 집어 가는 행. 엔티티 컬럼 그대로라 mediaType 은 아직 문자열이다.
// 도메인 타입으로 바꾸는 일은 FileRepositoryAdapter 가 맡는다 — 다른 조회와 같은 자리다.
public record StuckMediaRow(Long id, String mediaType) {
}
