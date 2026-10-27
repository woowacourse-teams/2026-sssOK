package com.sssok.application.media;

import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.application.room.RoomNicknameReader;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 파일 행만으로는 응답을 만들 수 없다. 폴더 소속과 업로더 이름이 다른 테이블에 있어서다.
// 미디어마다 찾아오면 30장짜리 목록에 쿼리가 60번 나가므로, 종류별로 한 번씩만 모아서 채운다.
@Component
@RequiredArgsConstructor
public class MediaDetailAssembler {

    private final FolderMediaRepository folderMediaRepository;
    private final RoomNicknameReader roomNicknameReader;
    private final MediaLikeRepository mediaLikeRepository;
    private final MediaUrlResolver mediaUrlResolver;

    // 모든 조회 응답은 같은 URL 계약을 쓴다. 화면은 thumbnailUrl과 displayUrl만 알면 된다.
    public List<MediaDetail> assembleForList(List<StoredFile> files, Long viewerId) {
        return assemble(files, viewerId, mediaUrlResolver::resolve);
    }

    // 상세용. 사진이면 프리뷰를, 영상이면 재생할 원본을 싣는다.
    public List<MediaDetail> assembleForDetail(List<StoredFile> files, Long viewerId) {
        return assemble(files, viewerId, mediaUrlResolver::resolve);
    }

    // DB 조회와 URL 서명을 나눠 부를 수 있게 한다. 사진이 많은 목록은 서명이 CPU 를 오래 쓰므로,
    // 호출하는 쪽이 load 만 트랜잭션 안에서 하고 toDetails 는 커넥션을 돌려준 뒤 부른다.
    public LoadedMedia load(List<StoredFile> files, Long viewerId) {
        if (files.isEmpty()) {
            return LoadedMedia.EMPTY;
        }
        List<Long> mediaIds = files.stream().map(StoredFile::getId).toList();
        return new LoadedMedia(
            files,
            folderMediaRepository.findFolderIdsByMedia(mediaIds),
            roomNicknameReader.readUploaderNames(files),
            mediaLikeRepository.countByMediaIds(mediaIds),
            viewerId == null ? Set.of() : mediaLikeRepository.findLikedMediaIds(viewerId, mediaIds));
    }

    public List<MediaDetail> toDetails(LoadedMedia loaded) {
        return toDetails(loaded, mediaUrlResolver::resolve);
    }

    private List<MediaDetail> assemble(List<StoredFile> files, Long viewerId,
                                       Function<StoredFile, MediaUrls> urls) {
        return toDetails(load(files, viewerId), urls);
    }

    private List<MediaDetail> toDetails(LoadedMedia loaded, Function<StoredFile, MediaUrls> urls) {
        return loaded.files().stream()
            .map(file -> MediaDetail.of(
                file,
                loaded.uploaderNames().get(file.getId()),
                loaded.folderIds().getOrDefault(file.getId(), List.of()),
                urls.apply(file),
                loaded.likeCounts().getOrDefault(file.getId(), 0L),
                loaded.likedByViewer().contains(file.getId())))
            .toList();
    }

    // 응답을 만드는 데 필요한 DB 값을 모아 둔다. 파일 행은 지연 로딩 연관이 없어 트랜잭션 밖에서 읽어도 된다.
    public record LoadedMedia(
        List<StoredFile> files,
        Map<Long, List<Long>> folderIds,
        Map<Long, String> uploaderNames,
        Map<Long, Long> likeCounts,
        Set<Long> likedByViewer
    ) {
        private static final LoadedMedia EMPTY = new LoadedMedia(List.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }

}
