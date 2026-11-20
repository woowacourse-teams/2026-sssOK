import { render, screen } from "@testing-library/react";

import { EMPTY_ROOM_TITLE, EmptyRoomGuide } from "./EmptyRoomGuide";

describe("EmptyRoomGuide", () => {
  it("방장에게는 공유 메뉴로 링크나 QR을 보내라고 안내한다", () => {
    render(<EmptyRoomGuide isHost canUpload />);

    expect(screen.getByText(EMPTY_ROOM_TITLE)).toBeInTheDocument();
    expect(screen.getByText(/오른쪽 위 링크 버튼으로 링크나 QR을 보내면/)).toBeInTheDocument();
  });

  it("올릴 수 있는 게스트에게는 아래 올리기 버튼을 안내한다", () => {
    render(<EmptyRoomGuide isHost={false} canUpload />);

    expect(screen.getByText(/아래 올리기 버튼으로 내 사진을 올려 주세요/)).toBeInTheDocument();
  });

  it("방장만 올리는 방의 게스트에게는 방장이 올리면 보인다고 안내한다", () => {
    render(<EmptyRoomGuide isHost={false} canUpload={false} />);

    expect(screen.getByText("방장이 사진을 올리면 여기에 보여요")).toBeInTheDocument();
  });

  it("같은 일을 하는 버튼을 따로 두지 않는다", () => {
    render(<EmptyRoomGuide isHost canUpload />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
