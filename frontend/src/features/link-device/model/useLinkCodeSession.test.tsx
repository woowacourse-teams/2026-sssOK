import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";

import { getRoomSession } from "@/entities/session";
import { track } from "@/shared/lib/analytics";
import { issueLinkCode } from "@/features/share-room/api/issueLinkCode";
import { useLinkCodeSession } from "./useLinkCodeSession";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));

const ROOM_CODE = "7K93QX2S";

const renderAt = (linkCode: string) => {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[`/rooms/${ROOM_CODE}?linkCode=${linkCode}`]}>
      {children}
    </MemoryRouter>
  );

  return renderHook(() => useLinkCodeSession(ROOM_CODE), { wrapper: Wrapper });
};

beforeEach(() => localStorage.clear());

describe("useLinkCodeSession 기기 연결 이벤트", () => {
  it("유효한 연결 코드로 들어오면 세션을 이어받고 연결 성공을 남긴다", async () => {
    // 목이 이 코드를 유효한 것으로 기억하게 먼저 발급받는다
    const { linkCode } = await issueLinkCode("mock-token-10234");

    renderAt(linkCode);

    await waitFor(() =>
      expect(track).toHaveBeenCalledWith("Device Linked", { room_code: ROOM_CODE }),
    );
    expect(getRoomSession(ROOM_CODE)).not.toBeNull();
  });

  it("만료됐거나 없는 코드면 연결 실패를 남긴다", async () => {
    renderAt("000000");

    await waitFor(() =>
      expect(track).toHaveBeenCalledWith("Device Link Failed", { room_code: ROOM_CODE }),
    );
    expect(track).not.toHaveBeenCalledWith("Device Linked", expect.anything());
  });
});
