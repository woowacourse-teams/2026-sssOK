package com.sssok.renewal.domain.organization;

// 요청 시점에 확정한 분석 대상 사진. 이후 업로드는 포함하지 않는다.
public record OrganizeTargetPhoto(long mediaId, String extension, OrganizePhotoSource source) {
}
