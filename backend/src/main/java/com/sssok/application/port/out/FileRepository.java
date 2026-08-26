package com.sssok.application.port.out;

// 파일 영속화 출력
public interface FileRepository {

    // 담기/꺼내기에서 mediaId가 실제로 존재하는 미디어인지 확인하는 용도.
    boolean existsById(Long id);
}
