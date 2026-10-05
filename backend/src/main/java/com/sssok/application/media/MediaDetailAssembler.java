package com.sssok.application.media;

import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.member.Member;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 파일 행만으로는 응답을 만들 수 없다. 폴더 소속·업로더 이름·좋아요가 다른 테이블에 있어서다.
// 미디어마다 찾아오면 30장짜리 목록에 쿼리가 수십 번 나가므로, 종류별로 한 번씩만 모아서 채운다.
//
// viewerId 는 likedByMe 를 판단할 사람이다. 방 전체에 뿌리는 SSE payload 처럼 보는 사람이 정해지지 않은
// 곳에서는 null 을 넘기고, 그때 likedByMe 는 모두 false 다.
@Component
@RequiredArgsConstructor
public class MediaDetailAssembler {

    private final FolderMediaRepository folderMediaRepository;
    private final MemberRepository memberRepository;
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

    private List<MediaDetail> assemble(List<StoredFile> files, Long viewerId,
                                       Function<StoredFile, MediaUrls> urls) {
        if (files.isEmpty()) {
            return List.of();
        }
        List<Long> mediaIds = files.stream().map(StoredFile::getId).toList();
        Map<Long, List<Long>> folderIds = folderMediaRepository.findFolderIdsByMedia(mediaIds);
        Map<Long, String> uploaderNames = uploaderNames(files);
        Map<Long, Long> likeCounts = mediaLikeRepository.countByMediaIds(mediaIds);
        Set<Long> likedByViewer = viewerId == null
            ? Set.of()
            : mediaLikeRepository.findLikedMediaIds(viewerId, mediaIds);

        return files.stream()
            .map(file -> MediaDetail.of(
                file,
                uploaderNames.get(file.getUploaderId()),
                folderIds.getOrDefault(file.getId(), List.of()),
                urls.apply(file),
                likeCounts.getOrDefault(file.getId(), 0L),
                likedByViewer.contains(file.getId())))
            .toList();
    }

    // 방을 나간 사람이 올린 사진은 회원 행이 없을 수 있어, 이름이 빠진 채로 내려간다.
    // 사진 자체는 방에 남으므로 목록에서 빼지는 않는다.
    private Map<Long, String> uploaderNames(List<StoredFile> files) {
        Set<Long> uploaderIds = files.stream()
            .map(StoredFile::getUploaderId)
            .collect(Collectors.toSet());

        return memberRepository.findAllByIdIn(uploaderIds).stream()
            .collect(Collectors.toMap(Member::getId,
                member -> member.getDisplayName().value(), (first, second) -> first));
    }
}
