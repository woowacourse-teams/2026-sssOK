package com.sssok.renewal.application.auth;

import com.sssok.application.media.MediaDetailAssembler;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.DeleteRoomService;
import com.sssok.application.room.RoomNicknameReader;
import com.sssok.renewal.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.SocialAccount;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@PostgresIntegrationTest
class WithdrawAccountServiceTest extends PostgresContainerSupport {

    @Autowired WithdrawAccountService withdrawAccountService;
    @MockitoSpyBean WithdrawMemberService withdrawMemberService;
    @Autowired SocialMemberLoginService socialMemberLoginService;
    @Autowired AnonymousAuthService anonymousAuthService;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @Autowired IssueLinkCodeService issueLinkCodeService;
    @Autowired LinkLoginService linkLoginService;
    @Autowired MemberRepository memberRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired CreateRoomService createRoomService;
    @Autowired DeleteRoomService deleteRoomService;
    @Autowired RoomRepository roomRepository;
    @Autowired FileRepository fileRepository;
    @Autowired MediaDetailAssembler mediaDetailAssembler;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean KakaoAuthClient kakaoAuthClient;
    @MockitoBean AppleAuthClient appleAuthClient;

    @BeforeEach
    void setUp() {
        when(kakaoAuthClient.provider()).thenReturn(SocialProvider.KAKAO);
        when(appleAuthClient.provider()).thenReturn(SocialProvider.APPLE);
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void 탈퇴_커밋후_외부연결을_한번_해제하고_새회원으로_재가입한다(SocialProvider provider) {
        SocialIdentity identity = identity(provider);
        AuthResult member = register(identity);
        String otherSession = sessionTokenProvider.issue(member.userId(), Instant.now()).value();
        String linkCode = issueLinkCodeService.issue(member.userId()).linkCode();
        SocialAuthClient client = client(provider);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertWithdrawn(member);
            assertThat(socialAccountRepository.findByIdentity(identity)).isPresent();
            return null;
        }).when(client).unlink(any());

        withdrawAccountService.withdraw(member.userId());

        verify(client, times(1)).unlink(any());
        assertThat(socialAccountRepository.findByIdentity(identity)).isEmpty();
        assertThat(socialAccountRepository.findTokens(identity)).isEmpty();
        assertThatThrownBy(() -> sessionTokenProvider.parse(otherSession)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> linkLoginService.login(linkCode)).isInstanceOf(LinkCodeNotFoundException.class);
        AuthResult rejoined = register(identity);
        assertThat(rejoined.userId()).isNotEqualTo(member.userId());
        assertThat(memberRepository.findById(member.userId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    void 본인_방만_삭제하고_다른방의_파일과_탈퇴작성자_표시를_유지한다() {
        AuthResult member = register(identity(SocialProvider.KAKAO));
        Long ownRoom = createRoomService.create(member.userId(), "내방", null).room().getId();
        Long deletedRoom = createRoomService.create(member.userId(), "삭제된방", null).room().getId();
        deleteRoomService.delete(deletedRoom, member.userId());
        Instant deletedAt = roomRepository.findById(deletedRoom).orElseThrow().getDeletedAt();
        AuthResult other = anonymousAuthService.authenticate("다른사람");
        Long otherRoom = createRoomService.create(other.userId(), "다른방", null).room().getId();
        Long fileId = jdbcTemplate.queryForObject("""
            INSERT INTO stored_file (room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 'a.jpg', 'JPEG', 1024, ?, 'RESERVED', now(), now(), now(), 0) RETURNING id
            """, Long.class, otherRoom, member.userId(), UUID.randomUUID().toString());

        withdrawAccountService.withdraw(member.userId());

        assertThat(roomRepository.findById(ownRoom).orElseThrow().isDeleted()).isTrue();
        assertThat(roomRepository.findById(deletedRoom).orElseThrow().getDeletedAt()).isEqualTo(deletedAt);
        assertThat(roomRepository.findById(otherRoom).orElseThrow().isDeleted()).isFalse();
        assertThat(fileRepository.findById(fileId)).isPresent();
        assertThat(mediaDetailAssembler.load(List.of(fileRepository.findById(fileId).orElseThrow()), other.userId())
            .uploaderNames().get(new RoomNicknameReader.Participant(otherRoom, member.userId()))).isEqualTo("탈퇴한 사용자");
        assertThat(sessionTokenProvider.parse(other.accessToken())).isEqualTo(other.userId());
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void 외부실패에도_탈퇴완료하고_암호문을_보존하며_재가입과_반복탈퇴를_차단한다(SocialProvider provider) {
        SocialIdentity identity = identity(provider);
        AuthResult member = register(identity);
        EncryptedSocialTokens stored = socialAccountRepository.findTokens(identity).orElseThrow();
        doThrow(SocialAuthException.unavailable()).when(client(provider)).unlink(any());

        withdrawAccountService.withdraw(member.userId());

        assertWithdrawn(member);
        assertThat(socialAccountRepository.findTokens(identity)).contains(stored);
        assertThatThrownBy(() -> register(identity)).isInstanceOf(SocialAuthException.class);
        AuthResult anonymous = anonymousAuthService.authenticate("익명");
        assertThatThrownBy(() -> socialMemberLoginService.login(authentication(identity), List.of(anonymous.userId()), RefreshTokenDelivery.COOKIE))
            .isInstanceOf(SocialAuthException.class);
        assertThat(sessionTokenProvider.parse(anonymous.accessToken())).isEqualTo(anonymous.userId());
        assertThatThrownBy(() -> withdrawAccountService.withdraw(member.userId())).isInstanceOf(SocialAuthException.class);
        verify(client(provider), times(1)).unlink(any());
    }

    @Test
    void 외부성공후_DB정리실패도_탈퇴완료와_대기기록을_유지한다() {
        SocialIdentity identity = identity(SocialProvider.KAKAO);
        AuthResult member = register(identity);
        doThrow(new IllegalStateException("DB 정리 실패")).when(withdrawMemberService).completeUnlink(any());

        withdrawAccountService.withdraw(member.userId());

        assertWithdrawn(member);
        assertThat(socialAccountRepository.findTokens(identity)).isPresent();
        verify(kakaoAuthClient, times(1)).unlink(any());
    }

    @Test
    void 탈퇴트랜잭션이_실패하면_회원_방_인증을_함께_복원한다() {
        SocialIdentity identity = identity(SocialProvider.KAKAO);
        AuthResult member = register(identity);
        Long roomId = createRoomService.create(member.userId(), "내방", null).room().getId();
        String code = issueLinkCodeService.issue(member.userId()).linkCode();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            withdrawMemberService.withdraw(member.userId());
            throw new IllegalStateException("롤백");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(memberRepository.findById(member.userId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.REGISTERED);
        assertThat(roomRepository.findById(roomId).orElseThrow().isDeleted()).isFalse();
        assertThat(sessionTokenProvider.parse(member.accessToken())).isEqualTo(member.userId());
        assertThat(linkLoginService.login(code).userId()).isEqualTo(member.userId());
        assertThat(socialAccountRepository.findByIdentity(identity)).isPresent();
        verify(kakaoAuthClient, never()).unlink(any());
    }

    @Test
    void 커밋직후_중단되어도_탈퇴회원의_소셜계정이_대기기록으로_남는다() {
        SocialIdentity identity = identity(SocialProvider.KAKAO);
        AuthResult member = register(identity);
        List<SocialAccount> pending = withdrawMemberService.withdraw(member.userId());
        assertWithdrawn(member);
        assertThat(socialAccountRepository.findByMemberId(member.userId())).isEqualTo(pending);
        assertThat(socialAccountRepository.findTokens(identity)).isPresent();
        verify(kakaoAuthClient, never()).unlink(any());
    }

    @Test
    void 활성회원의_연결은_정리할수없고_익명회원은_탈퇴할수없다() {
        SocialIdentity identity = identity(SocialProvider.KAKAO);
        AuthResult member = register(identity);
        assertThatThrownBy(() -> withdrawMemberService.completeUnlink(socialAccountRepository.findByIdentity(identity).orElseThrow()))
            .isInstanceOf(SocialAuthException.class);
        AuthResult anonymous = anonymousAuthService.authenticate("익명");
        assertThatThrownBy(() -> withdrawAccountService.withdraw(anonymous.userId())).isInstanceOf(SocialAuthException.class);
        assertThat(sessionTokenProvider.parse(member.accessToken())).isEqualTo(member.userId());
        assertThat(sessionTokenProvider.parse(anonymous.accessToken())).isEqualTo(anonymous.userId());
        verify(kakaoAuthClient, never()).unlink(any());
    }

    private void assertWithdrawn(AuthResult member) {
        assertThat(memberRepository.findById(member.userId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThatThrownBy(() -> sessionTokenProvider.parse(member.accessToken())).isInstanceOf(UnauthorizedException.class);
    }

    private AuthResult register(SocialIdentity identity) {
        return socialMemberLoginService.login(authentication(identity), List.of(), RefreshTokenDelivery.COOKIE);
    }

    private SocialAuthentication authentication(SocialIdentity identity) {
        return new SocialAuthentication(identity, "access", "refresh");
    }

    private SocialIdentity identity(SocialProvider provider) {
        return new SocialIdentity(provider, UUID.randomUUID().toString());
    }

    private SocialAuthClient client(SocialProvider provider) {
        return provider == SocialProvider.KAKAO ? kakaoAuthClient : appleAuthClient;
    }
}
