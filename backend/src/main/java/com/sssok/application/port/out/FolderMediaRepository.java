package com.sssok.application.port.out;

// 폴더-미디어 소속(folder_media) 영속화 출력. 순수 조인 관계라 도메인 객체 없이 ID로만 다룬다.
public interface FolderMediaRepository {

    // 이 폴더가 담고 있던 관계를 모두 끊는다. 미디어 자체는 지우지 않는다. 끊긴 개수를 반환한다.
    long detachAllFromFolder(Long folderId);
}
