package com.sssok.renewal.application.report;

import com.sssok.application.room.RoomNicknameReader;
import com.sssok.renewal.application.report.exception.MediaAlreadyReportedException;
import com.sssok.renewal.application.report.exception.ReportMediaNotFoundException;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.domain.report.MediaReportReason;
import com.sssok.renewal.outgoing.database.MediaReportRepository;
import com.sssok.renewal.outgoing.database.ReportFileRepository;
import com.sssok.renewal.outgoing.database.ReportRoomRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
// 신고 알림이 커밋 뒤에만 나가도록 트랜잭션으로 묶는다.
@Service
@RequiredArgsConstructor
public class ReportMediaService {
    private final ReportFileRepository reportFileRepository;
    private final ReportRoomRepository reportRoomRepository;
    private final RoomNicknameReader roomNicknameReader;
    private final MediaReportRepository mediaReportRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MediaReport report(ReportMediaCommand command) {
        MediaReportReason reason = MediaReportReason.from(command.reason());
        reportFileRepository.findById(command.mediaId())
            .filter(file -> file.getRoomId().equals(command.roomId()))
            // RESERVED·FAILED 는 갤러리에 보이지 않아 없는 미디어와 같게 본다.
            .filter(file -> file.getStatus().isVisible())
            .orElseThrow(ReportMediaNotFoundException::new);

        String roomName = reportRoomRepository.findNameById(command.roomId()).orElse(null);
        String nickname = roomNicknameReader.read(command.roomId(), command.memberId());
        MediaReport saved = mediaReportRepository.saveIfAbsent(MediaReport.create(command.roomId(), roomName,
                command.mediaId(), command.memberId(), nickname, reason, Instant.now()))
            .orElseThrow(MediaAlreadyReportedException::new);
        eventPublisher.publishEvent(new MediaReportedEvent(saved));
        return saved;
    }
}
