package com.sssok.infrastructure.persistence.medialike;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "media_like",
    uniqueConstraints = @UniqueConstraint(name = "uk_media_like", columnNames = {"media_id", "member_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaLikeJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    public MediaLikeJpaEntity(Long id, Long mediaId, Long memberId) {
        this.id = id;
        this.mediaId = mediaId;
        this.memberId = memberId;
    }
}
