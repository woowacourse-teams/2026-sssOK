package com.sssok.application.port.out;

import java.util.List;
import java.util.Map;

// 폴더-미디어 소속(folder_media) 영속화 출력. 순수 조인 관계라 도메인 객체 없이 ID로만 다룬다.
public interface FolderMediaRepository {

    // 이 미디어들을 전부 한 폴더에 연결한다. 이미 있는 조합은 건너뛰고,
    // 새로 연결된 개수만 반환한다 — 나머지(mediaIds 수 - 이 값)가 alreadyInCount다.
    int attachToFolder(Long folderId, List<Long> mediaIds);

    // 이 폴더가 담고 있던 관계를 모두 끊는다. 미디어 자체는 지우지 않는다. 끊긴 개수를 반환한다.
    long detachAllFromFolder(Long folderId);

    // folderIds × mediaIds 조합 중 실제로 있던 것만 끊는다(폴더를 직접 지정한 꺼내기).
    // 실제로 끊긴 조합 수를 반환한다.
    int detachFromFolders(List<Long> folderIds, List<Long> mediaIds);

    // 폴더 지정 없이, 이 미디어들이 속한 모든 폴더 관계를 끊는다(전부 루트로). 끊긴 조합 수를 반환한다.
    long detachFromAllFolders(List<Long> mediaIds);

    // 방을 통째로 정리(purge)할 때, 그 방에 속한 폴더들이 맺고 있던 관계를 한번에 끊는다.
    // 폴더 자체를 지우기 전에 먼저 불러야 고아 행이 남지 않는다.
    long detachAllByRoomId(Long roomId);

    long countByFolderId(Long folderId);

    // 위와 같지만 여러 폴더를 한 번에 센다(꺼내기에서 대상 폴더가 여러 개일 때 N+1을 피하는 용도).
    // 결과에 없는 폴더 id는 0개로 보면 된다.
    Map<Long, Long> countByFolderIds(List<Long> folderIds);

    // 주어진 미디어 중, 지금 어떤 폴더에든 하나라도 속해 있는 것만 골라 반환한다.
    // 꺼내기 전후로 두 번 호출해 "이번 요청으로 폴더 소속이 0개가 된 미디어"(movedToRoot)를 가려낸다.
    List<Long> findMediaIdsBelongingToAnyFolder(List<Long> mediaIds);

    // 주어진 미디어들이 (호출 시점 기준) 속해 있는 폴더 id를 중복 없이 반환한다.
    // 폴더 미지정 꺼내기에서 "영향받은 폴더" 목록을 만드는 용도.
    List<Long> findFolderIdsContainingMedia(List<Long> mediaIds);

    // 이 폴더에 담긴 미디어 id 전체를 반환한다. 폴더 단위 zip 다운로드의 대상 산정에 쓴다.
    List<Long> findMediaIdsByFolderId(Long folderId);

    // 미디어별로 담긴 폴더 목록. 어느 폴더에도 없으면 결과에 없다.
    Map<Long, List<Long>> findFolderIdsByMedia(List<Long> mediaIds);
}
