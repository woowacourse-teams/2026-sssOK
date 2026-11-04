import mascotCrying from "@/shared/assets/mascot-crying.png";
import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { ORGANIZE_PHOTO_LIMIT } from "../config";
import type { OrganizeLimit } from "../model/analyzablePhotos";
import { Content, Description, Mascot, Title } from "./OrganizeLimitModal.styles";

interface OrganizeLimitModalProps {
  limit: OrganizeLimit;
  onClose: () => void;
}

/**
 * '자동으로 정리하기' 를 눌렀는데 장수 때문에 정리할 수 없을 때 뜬다.
 *
 * 정리 방식 시트까지 다 고른 뒤에 막히면 헛걸음이라, 시트를 열기 전에 먼저 알린다.
 * 서버가 같은 이유(422)로 거절했을 때도 이 모달을 쓴다.
 */
export const OrganizeLimitModal = ({ limit, onClose }: OrganizeLimitModalProps) => (
  <Modal onClose={onClose} showClose={false}>
    <Content>
      <Mascot src={mascotCrying} alt="" />
      {limit.reason === "tooMany" ? (
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
      <Button variant="primary" onClick={onClose}>
        확인
      </Button>
    </Content>
  </Modal>
);
