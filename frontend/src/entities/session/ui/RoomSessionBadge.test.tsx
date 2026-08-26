import { render, screen } from "@testing-library/react";

import { saveRoomSession } from "../lib/roomSessionStorage";
import { RoomSessionBadge } from "./RoomSessionBadge";

describe("RoomSessionBadge", () => {
  beforeEach(() => localStorage.clear());

  it("방 세션에 저장된 닉네임을 보여준다", () => {
    saveRoomSession("7K93QX2S", {
      accessToken: "mock-token",
      userId: 10234,
      nickname: "윤돌",
      expiresAt: "2026-09-17T05:30:00Z",
    });

    render(<RoomSessionBadge roomCode="7K93QX2S" hostId={20000} />);

    expect(screen.getByText("윤돌")).toBeInTheDocument();
    expect(screen.getByLabelText("윤돌")).toBeInTheDocument();
  });

  it("세션 사용자와 방장이 같으면 왕관을 표시한다", () => {
    saveRoomSession("7K93QX2S", {
      accessToken: "mock-token",
      userId: 10234,
      nickname: "윤돌",
      expiresAt: "2026-09-17T05:30:00Z",
    });

    render(<RoomSessionBadge roomCode="7K93QX2S" hostId={10234} />);

    expect(screen.getByLabelText("방장 윤돌")).toBeInTheDocument();
  });

  it("저장된 방 세션이 없으면 배지를 보여주지 않는다", () => {
    const { container } = render(<RoomSessionBadge roomCode="7K93QX2S" hostId={10234} />);

    expect(container).toBeEmptyDOMElement();
  });
});
