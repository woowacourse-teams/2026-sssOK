package com.sssok.application.media;

import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.member.Member;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 파일 행만으로는 응답을 만들 수 없다. 폴더 소속과 업로더 이름이 다른 테이블에 있어서다.
// 미디어마다 찾아오면 30장짜리 목록에 쿼리가 60번 나가므로, 종류별로 한 번씩만 모아서 채운다.
@Component
@RequiredArgsConstructor
public class MediaDetailAssembler {

    private final FolderMediaRepository folderMediaRepository;
    private final MemberRepository memberRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public List<MediaDetail> assemble(List<StoredFile> files) {
        if (files.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> folderIds = folderMediaRepository.findFolderIdsByMedia(
            files.stream().map(StoredFile::getId).toList());
        Map<Long, String> uploaderNames = uploaderNames(files);

        return files.stream()
            .map(file -> MediaDetail.of(
                file,
                uploaderNames.get(file.getUploaderId()),
                folderIds.getOrDefault(file.getId(), List.of()),
                mediaUrlResolver.resolve(file)))
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
