package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.download.CreateDownloadJobService;
import com.sssok.application.download.DownloadCompressionWorker;
import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.application.port.out.VideoFrameExtractorPort;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractedFrame;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractionResult;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 영상이 사진과 똑같이 다뤄지는지 네 흐름을 한 자리에서 확인한다.
//
// 썸네일이 붙기 전까지 영상은 목록에서 아예 빠져 있었고(thumbnailUrl 이 null),
// 그래서 다운로드·삭제·폴더 이동이 실제로 되는지 확인된 적이 없다. 경로마다 타입 분기가
// 없다는 것은 읽어서 알 수 있지만, 그 사실이 계속 유지되는지는 테스트만 지켜준다.
@SpringBootTest
@ActiveProfiles("test")
class VideoMediaOperationsTest {

    private static final String PRESIGNED = "https://storage.example.com/signed";
    private static final Instant TAKEN_AT = Instant.parse("2026-09-20T14:30:00Z");
    private static final GeoPoint SEOUL = new GeoPoint(
        new BigDecimal("37.566500"), new BigDecimal("126.978000"));

    @Autowired
    GenerateThumbnailService generateThumbnailService;

    @Autowired
    GetMediaService getMediaService;

    @Autowired
    GetMediaListService getMediaListService;

    @Autowired
    DeleteMediaService deleteMediaService;

    @Autowired
    CreateDownloadJobService createDownloadJobService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    OrphanObjectRepository orphanObjectRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @MockitoBean
    VideoFrameExtractorPort videoFrameExtractor;

    // 다운로드 생성 이벤트는 별도 스레드에서 압축을 시작한다. 이 통합 테스트에서는 대상 선정까지만
    // 검증하므로 워커를 격리해 다음 테스트의 FileStoragePort 스텁과 경합하지 않게 한다.
    @MockitoBean
    DownloadCompressionWorker downloadCompressionWorker;

    private Long uploaderId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        uploaderId = memberRepository.save(
            Member.register(new Nickname("가현"), Instant.now())).getId();
        roomId = createRoomService.create(uploaderId, "우테코 회식", null, null).room().getId();

        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn(PRESIGNED);
        given(fileStoragePort.openUploadStream(any(), anyString()))
            .willReturn(new AbortableOutputStream() {
                @Override
                public void write(int b) {
                }

                @Override
                public void abort() {
                }
            });
        given(videoFrameExtractor.extractFirstFrame(anyString(), anyInt()))
            .willReturn(ExtractionResult.extracted(new ExtractedFrame(
                1920, 1080, 12, TAKEN_AT, SEOUL, jpeg())));
    }

    // 썸네일이 붙어야 프론트가 목록에서 걸러내지 않는다 — 이게 없으면 나머지 셋도 UI 에서 못 한다.
    @Test
    void 영상도_목록에_썸네일과_함께_나온다() {
        StoredFile video = readyVideo();

        MediaDetail found = onlyItemOf(roomId);

        assertThat(found.mediaId()).isEqualTo(video.getId());
        assertThat(found.type()).isEqualTo("VIDEO");
        assertThat(found.thumbnailUrl()).isEqualTo(PRESIGNED);
    }

    @Test
    void 영상_상세에_재생시간과_촬영_정보가_담긴다() {
        StoredFile video = readyVideo();

        MediaFullDetail full = getMediaService.get(roomId, video.getId(), uploaderId);

        assertThat(full.media().type()).isEqualTo("VIDEO");
        assertThat(full.media().duration()).isEqualTo(12);
        assertThat(full.takenAt()).isEqualTo(TAKEN_AT);
        assertThat(full.location()).isEqualTo(SEOUL);
        assertThat(full.canDelete()).isTrue();
    }

    // 상세 응답은 원본을 그대로 재생할 수 있어야 한다. 썸네일만 있고 원본이 없으면 아무것도 못 한다.
    @Test
    void 영상_상세에_원본_주소가_담긴다() {
        StoredFile video = readyVideo();

        MediaFullDetail full = getMediaService.get(roomId, video.getId(), uploaderId);

        assertThat(full.media().originalUrl()).isEqualTo(PRESIGNED);
        assertThat(full.media().originalUrlExpiresAt()).isNotNull();
    }

    @Test
    void 영상도_다운로드_대상이_된다() {
        StoredFile video = readyVideo();

        var job = createDownloadJobService.create(
            roomId, uploaderId, List.of(video.getId()), null,
            null);

        assertThat(job).isNotNull();
    }

    @Test
    void 영상도_삭제된다() {
        StoredFile video = readyVideo();

        deleteMediaService.deleteOne(roomId, video.getId(), uploaderId);

        assertThat(fileRepository.findById(video.getId())).isEmpty();
    }

    // 영상 썸네일은 원본 옆 thumbnails/ 에 있다. 원본만 지우면 스토리지에 고아가 남는다.
    @Test
    void 영상을_지우면_썸네일도_같이_지운다() {
        StoredFile video = readyVideo();
        StorageKey original = video.getStorageKey();

        deleteMediaService.deleteOne(roomId, video.getId(), uploaderId);

        List<StorageKey> pendingKeys = orphanObjectRepository
            .findStale(Instant.now().plusSeconds(60), 1000).stream()
            .map(OrphanObject::storageKey)
            .toList();
        assertThat(pendingKeys).contains(original, original.thumbnail());
    }

    // 워커를 실제로 돌려 READY 까지 보낸다 — 썸네일 키가 붙은 상태가 이 테스트의 출발점이다.
    private StoredFile readyVideo() {
        StoredFile file = StoredFile.reserve(
            roomId, uploaderId, "회식영상.mp4", "video/mp4", new FileSize(2048), Instant.now());
        file.startProcessing();
        StoredFile saved = fileRepository.save(file);

        generateThumbnailService.generate(saved.getId());

        StoredFile after = fileRepository.findById(saved.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(UploadStatus.READY);
        assertThat(after.getThumbnailKey()).isNotNull();
        return after;
    }

    private MediaDetail onlyItemOf(Long targetRoomId) {
        MediaPage page = getMediaListService.page(
            targetRoomId, null, uploaderId, MediaUploaderFilter.ALL, 30, null);
        assertThat(page.items()).hasSize(1);
        return page.items().getFirst();
    }

    private byte[] jpeg() {
        BufferedImage image = new BufferedImage(400, 225, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 400, 225);
        graphics.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
