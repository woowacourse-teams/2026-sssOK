package com.sssok.renewal.outgoing.database.member;

import com.sssok.infrastructure.persistence.download.DownloadJobJpaRepository;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaRepository;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import com.sssok.infrastructure.persistence.medialike.MediaLikeJpaRepository;
import com.sssok.infrastructure.persistence.room.RoomJpaRepository;
import com.sssok.infrastructure.persistence.room.RoomMemberJpaRepository;
import com.sssok.renewal.outgoing.database.DeviceTokenJpaRepository;
import com.sssok.renewal.outgoing.database.PushSubscriptionJpaRepository;
import com.sssok.renewal.outgoing.database.RoomNotificationSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberActivityRepository {

    private final RoomMemberJpaRepository roomMemberJpaRepository;
    private final MediaLikeJpaRepository mediaLikeJpaRepository;
    private final RoomJpaRepository roomJpaRepository;
    private final StoredFileJpaRepository storedFileJpaRepository;
    private final DownloadJobJpaRepository downloadJobJpaRepository;
    private final FeedbackJpaRepository feedbackJpaRepository;
    private final PushSubscriptionJpaRepository pushSubscriptionJpaRepository;
    private final DeviceTokenJpaRepository deviceTokenJpaRepository;
    private final RoomNotificationSettingJpaRepository roomNotificationSettingJpaRepository;

    public void mergeRoomMemberships(Long sourceMemberId, Long targetMemberId) {
        roomMemberJpaRepository.mergeMemberships(sourceMemberId, targetMemberId);
    }

    public void deleteRoomMemberships(Long memberId) {
        roomMemberJpaRepository.deleteByMemberId(memberId);
    }

    public void mergeLikes(Long sourceMemberId, Long targetMemberId) {
        mediaLikeJpaRepository.mergeLikes(sourceMemberId, targetMemberId);
    }

    public void deleteLikes(Long memberId) {
        mediaLikeJpaRepository.deleteByMemberId(memberId);
    }

    public void transferHostedRooms(Long sourceMemberId, Long targetMemberId) {
        roomJpaRepository.transferHost(sourceMemberId, targetMemberId);
    }

    public void transferUploads(Long sourceMemberId, Long targetMemberId) {
        storedFileJpaRepository.transferUploader(sourceMemberId, targetMemberId);
    }

    public void transferDownloadJobs(Long sourceMemberId, Long targetMemberId) {
        downloadJobJpaRepository.transferRequester(sourceMemberId, targetMemberId);
    }

    public void transferFeedbackAuthor(Long sourceMemberId, Long targetMemberId) {
        feedbackJpaRepository.transferMember(sourceMemberId, targetMemberId);
    }

    public void transferPushSubscriptions(Long sourceMemberId, Long targetMemberId) {
        pushSubscriptionJpaRepository.transferMember(sourceMemberId, targetMemberId);
    }

    public void transferDeviceTokens(Long sourceMemberId, Long targetMemberId) {
        deviceTokenJpaRepository.transferMember(sourceMemberId, targetMemberId);
    }

    public void mergeNotificationSettings(Long sourceMemberId, Long targetMemberId) {
        roomNotificationSettingJpaRepository.mergeSettings(sourceMemberId, targetMemberId);
    }

    public void deleteNotificationSettings(Long memberId) {
        roomNotificationSettingJpaRepository.deleteByMemberId(memberId);
    }
}
