package com.sssok.infrastructure.persistence.file;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 존재 확인(FileRepository.findExistingIds)에만 쓰는 최소 매핑. 실제 업로드 파이프라인(#16)이
// 들어올 때 stored_file의 나머지 컬럼(StoredFile 도메인 필드)까지 채워 넣는다.
@Entity
@Table(name = "stored_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFileJpaEntity {

    @Id
    private Long id;

    public StoredFileJpaEntity(Long id) {
        this.id = id;
    }
}
