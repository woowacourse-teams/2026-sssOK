package com.sssok.infrastructure.persistence.folder;

import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.sssok.infrastructure.persistence.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "folder_media",
    uniqueConstraints = @UniqueConstraint(name = "uk_folder_media", columnNames = {"folder_id", "media_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FolderMediaJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    // 쓰기는 기존 ID 필드가 담당한다. 조회용 단방향 관계에 cascade는 사용하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_folder_media_folder"))
    private FolderJpaEntity folder;

    // 쓰기는 기존 ID 필드가 담당한다. 조회용 단방향 관계에 cascade는 사용하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_folder_media_media"))
    private StoredFileJpaEntity media;

    public FolderMediaJpaEntity(Long id, Long folderId, Long mediaId) {
        this.id = id;
        this.folderId = folderId;
        this.mediaId = mediaId;
    }
}
