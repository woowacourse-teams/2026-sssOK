import mascotHello from "@/shared/assets/mascot-hello.svg";
import { Empty, EmptyBody } from "./RoomListPage.styles";

export const RoomListEmpty = () => (
  <Empty>
    <EmptyBody>
      <img src={mascotHello} alt="" />
      <h2>아직 들어간 방이 없어요</h2>
      <p>방을 만들거나, 받은 초대 링크로 들어가 보세요.</p>
    </EmptyBody>
  </Empty>
);
