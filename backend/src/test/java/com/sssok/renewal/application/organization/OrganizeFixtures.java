package com.sssok.renewal.application.organization;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.organization.job.OrganizeStartService;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

// 사진 정리 테스트가 쓰는 회원·방·사진·작업 준비. H2 서비스 테스트와 PostgreSQL 서비스 테스트가 함께 쓴다.
// 테스트마다 새 행을 만들어 서로 격리되므로 정리가 필요 없다.
@TestComponent
@RequiredArgsConstructor
public class OrganizeFixtures {
    private final JdbcTemplate jdbc;
    private final AnonymousAuthService anonymousAuthService;
    private final CreateRoomService createRoomService;
    private final FileRepository fileRepository;
    private final OrganizeStartService startService;
    private final ObjectMapper objectMapper;

    public Long anonymousMember() {
        return anonymousAuthService.authenticate("익명").userId();
    }

    public Long registeredMember() {
        Long id = anonymousMember();
        jdbc.update("UPDATE member SET status = 'REGISTERED' WHERE id = ?", id);
        return id;
    }

    public Long room(Long hostId) {
        return createRoomService.create(hostId, "정리 방", null).room().getId();
    }

    public Long photo(Long roomId, Long uploaderId, String fileName, String mimeType, boolean ready) {
        StoredFile file = StoredFile.reserve(roomId, uploaderId, fileName, mimeType, new FileSize(1024),
            Instant.now(), SIZE_POLICY);
        file.startProcessing();
        if (ready) {
            file.markReady();
        }
        return fileRepository.save(file).getId();
    }

    public List<Long> jpegs(Long roomId, Long uploaderId, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(photo(roomId, uploaderId, "p" + i + ".jpg", "image/jpeg", true));
        }
        return ids;
    }

    public String key() {
        return UUID.randomUUID().toString();
    }

    public OrganizeJob startPerson(Long roomId, Long memberId) {
        return startService.startByKind(roomId, memberId, OrganizeKind.PERSON, key());
    }

    public JsonNode personResult(List<Long> grouped, List<Long> unassigned) {
        return objectMapper.valueToTree(Map.of(
            "groups", List.of(Map.of("groupId", "person_0001", "mediaIds", grouped,
                "faceMembershipCount", grouped.size(), "bodyAdditionCount", 0)),
            "unassignedMediaIds", unassigned, "errors", List.of(), "counts", Map.of(), "pipelineVersion", "v1"));
    }
}
