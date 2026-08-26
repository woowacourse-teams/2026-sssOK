package com.sssok.infrastructure.persistence.folder;

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

    public FolderMediaJpaEntity(Long id, Long folderId, Long mediaId) {
        this.id = id;
        this.folderId = folderId;
        this.mediaId = mediaId;
    }
}
