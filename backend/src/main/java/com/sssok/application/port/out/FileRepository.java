package com.sssok.application.port.out;

import com.sssok.domain.file.StoredFile;
import java.util.List;

// 파일 영속화 출력
public interface FileRepository {

    List<StoredFile> findAllByRoomId(Long roomId);

    void deleteAllByRoomId(Long roomId);
}
