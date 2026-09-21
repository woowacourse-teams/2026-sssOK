import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { saveRoomSession } from "@/entities/session";
import { track } from "@/shared/lib/analytics";
import { RoomShareButton } from "./RoomShareButton";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));

const ROOM_CODE = "7K93QX2S";

const openMenu = async (user: ReturnType<typeof userEvent.setup>) => {
  render(<RoomShareButton roomCode={ROOM_CODE} />);
  await user.click(screen.getByRole("button", { name: "방 공유 메뉴 열기" }));
};

beforeEach(() => localStorage.clear());

describe("RoomShareButton 공유 이벤트", () => {
  it("링크 공유를 복사하면 초대 링크 복사를 남긴다", async () => {
    const user = userEvent.setup();
    await openMenu(user);

    await user.click(screen.getByRole("menuitem", { name: /링크 공유/ }));

    expect(await screen.findByText("공유 링크를 복사했어요.")).toBeInTheDocument();
    expect(track).toHaveBeenCalledWith("Invite Link Copied", { is_success: true });
  });

  it("복사에 실패하면 실패로 남긴다", async () => {
    const user = userEvent.setup();
    jest.spyOn(navigator.clipboard, "writeText").mockRejectedValueOnce(new Error("denied"));
    await openMenu(user);

    await user.click(screen.getByRole("menuitem", { name: /링크 공유/ }));

    expect(await screen.findByText(/링크를 복사하지 못했어요/)).toBeInTheDocument();
    expect(track).toHaveBeenCalledWith("Invite Link Copied", { is_success: false });
  });

  it("다른 기기에서 이어하기 링크를 복사하면 기기 연결 링크 복사를 남긴다", async () => {
    saveRoomSession(ROOM_CODE, {
      accessToken: "mock-token-10234",
      userId: 10234,
      nickname: "민수",
      expiresAt: "2099-01-01T00:00:00Z",
    });
    const user = userEvent.setup();
    await openMenu(user);

    await user.click(screen.getByRole("menuitem", { name: /다른 기기에서 이어하기/ }));

    expect(await screen.findByText("다른 기기에서 이어할 링크를 복사했어요.")).toBeInTheDocument();
    expect(track).toHaveBeenCalledWith("Device Link Copied", { is_success: true });
  });
});
