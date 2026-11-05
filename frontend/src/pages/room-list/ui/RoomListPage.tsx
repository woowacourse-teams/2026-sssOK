import { RoomListHeader } from "./RoomListHeader";
import { Body, Page } from "./RoomListPage.styles";
import { RoomListSection } from "./RoomListSection";

export const RoomListPage = () => (
  <Page>
    <RoomListHeader />
    <Body>
      <RoomListSection />
    </Body>
  </Page>
);
