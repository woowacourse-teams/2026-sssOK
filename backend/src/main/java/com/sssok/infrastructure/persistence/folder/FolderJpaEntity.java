package com.sssok.infrastructure.persistence.folder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "folder",
    uniqueConstraints = @UniqueConstraint(name = "uk_folder_room_id_name", columnNames = {"room_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FolderJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 12)
    private String name;

    public FolderJpaEntity(Long id, Long roomId, String name, Instant createdAt) {
        super(createdAt);
        this.id = id;
        this.roomId = roomId;
        this.name = name;
    }
}
