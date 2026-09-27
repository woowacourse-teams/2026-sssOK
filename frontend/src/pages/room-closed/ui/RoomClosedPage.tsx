import closedRoomImage from "@/shared/assets/mascot-close.png";
import { ROUTES } from "@/shared/config";
import {
  ActionLink,
  Actions,
  Content,
  Description,
  Image,
  Page,
  PrimaryActionLink,
  TextGroup,
  Title,
} from "./RoomClosedPage.styles";

export const RoomClosedPage = () => {
  return (
    <Page>
      <Content>
        <Image src={closedRoomImage} alt="닫힌 방을 안내하는 캐릭터" />
        <TextGroup>
          <Title>이 방은 사라졌어요</Title>
          <Description>{"이 방의 사진과 영상은\n더 이상 확인할 수 없어요"}</Description>
        </TextGroup>
      </Content>

      <Actions>
        <PrimaryActionLink to={ROUTES.createRoom}>새 방 만들기</PrimaryActionLink>
        <ActionLink to={ROUTES.home}>홈으로</ActionLink>
      </Actions>
    </Page>
  );
};
