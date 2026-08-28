package com.sssok.application.media;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageProcessorPort;
import com.sssok.application.port.out.ImageProcessorPort.CaptureInfo;
import com.sssok.application.port.out.ImageProcessorPort.ProcessedImage;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 등록된 미디어를 PROCESSING 에서 READY 로 넘기는 워커.
//
// 트랜잭션을 열지 않는다. 원본을 내려받고 줄여서 다시 올리는 동안 DB 커넥션을 쥐고 있으면
// 사진 몇 장만 처리해도 풀이 마른다. 실제 쓰기는 MediaFinisher 가 짧게 연다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateThumbnailService {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final ImageProcessorPort imageProcessor;
    private final MediaFinisher mediaFinisher;
    private final ThumbnailProperties properties;

    public void generate(Long mediaId) {
        StoredFile file = fileRepository.findById(mediaId).orElse(null);
        if (file == null || file.getStatus() != UploadStatus.PROCESSING) {
            return;
        }
        try {
            process(file);
        } catch (RuntimeException e) {
            // 스토리지나 네트워크 오류는 대개 일시적이다. 여기서 FAILED 로 내리면 멀쩡히 올라간
            // 사진이 목록에서 사라진다. PROCESSING 으로 두면 회수 배치가 다시 태운다.
            log.warn("썸네일 생성에 실패했습니다. 회수 배치가 다시 시도합니다. mediaId={}", mediaId, e);
        }
    }

    private void process(StoredFile file) {
        // 영상은 프레임을 뽑으려면 별도 도구가 필요하다. 썸네일 없이 완료로 넘긴다 —
        // PROCESSING 에 두면 다운로드가 영영 409 로 막힌다.
        //
        // 원본을 내려받지도 않는다. 영상은 최대 1GB 라, 길이 하나 읽자고 통째로 메모리에 올리면
        // 서버가 죽는다. duration 은 스트리밍으로 읽는 도구가 붙은 뒤에 채운다.
        if (!file.canGenerateThumbnail()) {
            mediaFinisher.finish(file, ProcessedMedia.none());
            return;
        }

        // 원본이 없으면 여기서 예외가 난다. 등록 때 실물을 확인했으므로 원래는 있어야 하고,
        // 지금 안 보이는 것은 일시적일 수 있어 바깥에서 PROCESSING 으로 남긴다.
        byte[] original = readOriginal(file.getStorageKey());

        // 원본과 같은 형식으로 줄인다. PNG 를 JPEG 로 바꾸면 투명한 부분이 검게 나온다.
        Optional<ProcessedImage> shrunk = imageProcessor.shrink(
            original, properties.maxWidth(), file.getMediaType().extension());
        if (shrunk.isEmpty()) {
            // 파일이 깨졌거나 확장자와 실제 내용이 다르다. 다시 시도해도 결과가 같으므로
            // 여기서만 FAILED 로 확정한다 — 되풀이해도 소용없는 유일한 경우다.
            log.warn("이미지를 읽을 수 없습니다. mediaId={}", file.getId());
            mediaFinisher.markFailed(file);
            return;
        }

        ProcessedImage image = shrunk.get();
        StorageKey thumbnailKey = file.getStorageKey().thumbnail();
        upload(thumbnailKey, image.content(), file.getMediaType().contentType());

        // 썸네일을 만들면서 EXIF 도 같이 읽는다. 원본이 이미 메모리에 있어 추가 왕복이 없다.
        CaptureInfo capture = imageProcessor.readCaptureInfo(original);

        // 크기는 썸네일이 아니라 원본의 것을 저장한다. 클라이언트가 자리를 미리 잡는 데 쓴다.
        mediaFinisher.finish(file, new ProcessedMedia(thumbnailKey,
            image.sourceWidth(), image.sourceHeight(), capture.takenAt(), capture.location()));
    }

    // 이미지는 최대 10MB 라 통째로 읽어도 괜찮다. 축소하려면 어차피 전체가 필요하다.
    // 영상은 여기까지 오지 않는다 — 1GB 를 힙에 올리면 서버가 죽는다.
    private byte[] readOriginal(StorageKey storageKey) {
        try (InputStream in = fileStoragePort.openDownloadStream(storageKey)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 실패하면 abort 로 정리한다. 그냥 두면 완료되지 않은 멀티파트 조각이 스토리지에 남아 요금을 먹는다.
    private void upload(StorageKey storageKey, byte[] content, String contentType) {
        AbortableOutputStream out = fileStoragePort.openUploadStream(storageKey, contentType);
        try {
            out.write(content);
            out.close();
        } catch (IOException e) {
            out.abort();
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            out.abort();
            throw e;
        }
    }
}
