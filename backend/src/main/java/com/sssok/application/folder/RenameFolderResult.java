package com.sssok.application.folder;

import com.sssok.domain.folder.Folder;

// 이름을 바꾼 폴더와, 그 폴더가 지금 담고 있는(목록에 보이는) 사진 수.
// 응답의 photoCount 를 0으로 고정해 내보내면 클라이언트가 그 값으로 배지를 갱신했을 때
// 사진이 든 폴더가 0장으로 보인다.
public record RenameFolderResult(Folder folder, int photoCount) {
}
