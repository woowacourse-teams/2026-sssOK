import { fireEvent, render, screen } from "@testing-library/react";
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

const saveSession = (accessToken = "mock-token-10234") =>
  saveRoomSession(ROOM_CODE, {
    accessToken,
    userId: 10234,
    nickname: "민수",
    expiresAt: "2099-01-01T00:00:00Z",
  });

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

  it("현재 방 주소를 담은 QR 코드 모달을 연다", async () => {
    const user = userEvent.setup();
    await openMenu(user);

    await user.click(screen.getByRole("menuitem", { name: /QR 코드 공유/ }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByTitle("현재 방 참여 QR 코드")).toBeInTheDocument();
  });

  it("다른 기기에서 이어하기 링크를 복사하면 기기 연결 링크 복사를 남긴다", async () => {
    saveSession();
    const user = userEvent.setup();
    await openMenu(user);

    await user.click(screen.getByRole("menuitem", { name: /다른 기기에서 이어하기/ }));

    expect(await screen.findByText("다른 기기에서 이어할 링크를 복사했어요.")).toBeInTheDocument();
    expect(track).toHaveBeenCalledWith("Device Link Copied", { is_success: true });
  });

  it("탭한 버튼이 포커스를 받지 않아도 메뉴가 먼저 닫히지 않고 이어하기 링크를 복사한다", async () => {
    saveSession();
    const user = userEvent.setup();
    await openMenu(user);

    fireEvent.blur(screen.getByRole("menuitem", { name: /링크 공유/ }), { relatedTarget: null });
    await user.click(screen.getByRole("menuitem", { name: /다른 기기에서 이어하기/ }));

    expect(await screen.findByText("다른 기기에서 이어할 링크를 복사했어요.")).toBeInTheDocument();
  });

  it("ClipboardItem을 지원하면 링크를 받아오기 전에 복사를 시작한다", async () => {
    saveSession();
    const user = userEvent.setup();
    class MockClipboardItem {
      items: Record<string, Promise<Blob>>;
      constructor(items: Record<string, Promise<Blob>>) {
        this.items = items;
      }
    }
    Object.defineProperty(globalThis, "ClipboardItem", {
      value: MockClipboardItem,
      configurable: true,
    });
    const write = jest.spyOn(navigator.clipboard, "write").mockResolvedValue();
    const writeText = jest.spyOn(navigator.clipboard, "writeText");

    try {
      await openMenu(user);
      await user.click(screen.getByRole("menuitem", { name: /다른 기기에서 이어하기/ }));

      expect(
        await screen.findByText("다른 기기에서 이어할 링크를 복사했어요."),
      ).toBeInTheDocument();
      expect(writeText).not.toHaveBeenCalled();
      const item = write.mock.calls[0][0][0] as unknown as MockClipboardItem;
      const copied = await (await item.items["text/plain"]).text();
      expect(copied).toBe(`${window.location.origin}/rooms/${ROOM_CODE}?linkCode=483920`);
    } finally {
      Reflect.deleteProperty(globalThis, "ClipboardItem");
    }
  });

  it("이어하기 링크를 받아오지 못하면 실패 토스트를 띄운다", async () => {
    saveSession("invalid-token");
    const user = userEvent.setup();
    await openMenu(user);

    await user.click(screen.getByRole("menuitem", { name: /다른 기기에서 이어하기/ }));

    expect(await screen.findByText(/이어하기 링크를 만들지 못했어요/)).toBeInTheDocument();
    expect(track).toHaveBeenCalledWith("Device Link Copied", { is_success: false });
  });
});
