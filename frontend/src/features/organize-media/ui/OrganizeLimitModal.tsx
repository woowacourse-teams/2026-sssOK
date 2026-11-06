import mascotCrying from "@/shared/assets/mascot-crying.png";
import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { ORGANIZE_PHOTO_LIMIT } from "../config";
import type { OrganizeLimit } from "../model/analyzablePhotos";
import { Actions, Content, Description, Mascot, Title } from "./OrganizeLimitModal.styles";

interface OrganizeLimitModalProps {
  limit: OrganizeLimit;
  onClose: () => void;
  /** 체험을 다 썼을 때 로그인 화면으로 보낸다. 없으면 로그인 버튼을 그리지 않는다. */
  onLogin?: () => void;
}

/**
 * '자동으로 정리하기' 를 눌렀는데 장수 때문에 정리할 수 없을 때 뜬다.
 *
 * 정리 방식 시트까지 다 고른 뒤에 막히면 헛걸음이라, 시트를 열기 전에 먼저 알린다.
 * 서버가 같은 이유(422)로 거절했을 때도 이 모달을 쓴다.
 * 익명 체험(방마다 1회)을 다 써서 거절당했을 때는 로그인을 권한다.
 */
export const OrganizeLimitModal = ({ limit, onClose, onLogin }: OrganizeLimitModalProps) => (
  <Modal onClose={onClose} showClose={false}>
    <Content>
      {limit.reason !== "trialExhausted" && <Mascot src={mascotCrying} alt="" />}
      {limit.reason === "trialExhausted" ? (
        <>
          <Title>AI 정리를 다 썼어요</Title>
          <Description>로그인하면 횟수 제한 없이 정리할 수 있어요.</Description>
        </>
      ) : limit.reason === "tooMany" ? (
        <>
          <Title>사진이 너무 많아요</Title>
          <Description>
            AI 정리는 사진 {ORGANIZE_PHOTO_LIMIT}장까지 할 수 있어요.
            {limit.count !== null && (
              <>
                <br />
                지금 이 방에는 사진이 {limit.count}장 있어요.
              </>
            )}
          </Description>
        </>
      ) : (
        <>
          <Title>나눌 사진이 없어요</Title>
          <Description>
            사진을 올린 뒤에 다시 시도해 주세요.
            <br />
            동영상은 나누지 않아요.
          </Description>
        </>
      )}
      {limit.reason === "trialExhausted" && onLogin ? (
        <Actions>
          <Button variant="primary" onClick={onLogin}>
            로그인하기
          </Button>
          <Button variant="default" onClick={onClose}>
            다음에 할게요
          </Button>
        </Actions>
      ) : (
        <Button variant="primary" onClick={onClose}>
          확인
        </Button>
      )}
    </Content>
  </Modal>
);
