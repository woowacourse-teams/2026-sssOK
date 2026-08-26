package com.sssok.application.port.out;

import java.util.List;

// 폴더-미디어 소속(folder_media) 영속화 출력. 순수 조인 관계라 도메인 객체 없이 ID로만 다룬다.
public interface FolderMediaRepository {

    // folderIds × mediaIds 모든 조합을 연결한다(카테시안 곱). 이미 있는 조합은 건너뛰고,
    // 새로 연결된 조합 수만 반환한다 — 나머지(전체 조합 수 - 이 값)가 alreadyInCount다.
    int attachAll(List<Long> folderIds, List<Long> mediaIds);

    // 이 폴더가 담고 있던 관계를 모두 끊는다. 미디어 자체는 지우지 않는다. 끊긴 개수를 반환한다.
    long detachAllFromFolder(Long folderId);

    // folderIds × mediaIds 조합 중 실제로 있던 것만 끊는다(폴더를 직접 지정한 꺼내기).
    // 실제로 끊긴 조합 수를 반환한다.
    int detachFromFolders(List<Long> folderIds, List<Long> mediaIds);

    // 폴더 지정 없이, 이 미디어들이 속한 모든 폴더 관계를 끊는다(전부 루트로). 끊긴 조합 수를 반환한다.
    long detachFromAllFolders(List<Long> mediaIds);

    long countByFolderId(Long folderId);

    // 주어진 미디어 중, 지금 어떤 폴더에든 하나라도 속해 있는 것만 골라 반환한다.
    // 꺼내기 전후로 두 번 호출해 "이번 요청으로 폴더 소속이 0개가 된 미디어"(movedToRoot)를 가려낸다.
    List<Long> findMediaIdsBelongingToAnyFolder(List<Long> mediaIds);

    // 주어진 미디어들이 (호출 시점 기준) 속해 있는 폴더 id를 중복 없이 반환한다.
    // 폴더 미지정 꺼내기에서 "영향받은 폴더" 목록을 만드는 용도.
    List<Long> findFolderIdsContainingMedia(List<Long> mediaIds);
}
