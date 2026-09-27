import closedRoomImage from "@/shared/assets/mascot-close.png";
import { ROUTES } from "@/shared/config";
import { Stack } from "@/shared/ui/stack";
import {
  ActionLink,
  Actions,
  Content,
  Description,
  Image,
  Page,
  PrimaryActionLink,
  Title,
} from "./RoomClosedPage.styles";

export const RoomClosedPage = () => {
  return (
    <Page>
      <Content>
        <Image src={closedRoomImage} alt="닫힌 방을 안내하는 캐릭터" />

        <Stack gap={8} align="center">
          <Title>이 방은 사라졌어요</Title>
          <Description>{"삭제되었거나 보관 기간이 지나\n더 이상 이용할 수 없어요"}</Description>
        </Stack>
      </Content>

      <Actions>
        <PrimaryActionLink to={ROUTES.createRoom}>새 방 만들기</PrimaryActionLink>
        <ActionLink to={ROUTES.home}>홈으로 가기</ActionLink>
      </Actions>
    </Page>
  );
};
