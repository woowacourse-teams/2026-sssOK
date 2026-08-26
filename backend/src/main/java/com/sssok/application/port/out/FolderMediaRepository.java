package com.sssok.application.port.out;

import java.util.List;

// 폴더-미디어 소속(folder_media) 영속화 출력. 순수 조인 관계라 도메인 객체 없이 ID로만 다룬다.
public interface FolderMediaRepository {

    // folderIds × mediaIds 모든 조합을 연결한다(카테시안 곱). 이미 있는 조합은 건너뛰고,
    // 새로 연결된 조합 수만 반환한다 — 나머지(전체 조합 수 - 이 값)가 alreadyInCount다.
    int attachAll(List<Long> folderIds, List<Long> mediaIds);

    // 이 폴더가 담고 있던 관계를 모두 끊는다. 미디어 자체는 지우지 않는다. 끊긴 개수를 반환한다.
    long detachAllFromFolder(Long folderId);

    long countByFolderId(Long folderId);
}
