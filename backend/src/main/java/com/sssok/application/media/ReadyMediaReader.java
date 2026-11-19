package com.sssok.application.media;

import com.sssok.renewal.outgoing.database.UploadFileRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 커밋된 파일을 다시 읽어 완료 이벤트 본문을 만든다. 삭제된 파일이면 발행하지 않는다. */
@Component
@RequiredArgsConstructor
public class ReadyMediaReader {
    private final UploadFileRepository files;
    private final MediaDetailAssembler assembler;

    public Optional<MediaDetail> read(Long mediaId) {
        return files.findById(mediaId)
            .map(file -> assembler.assembleForList(List.of(file), null).get(0));
    }
}
