import { render, screen, type RenderResult } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";

import { openInviteApp, isMobileBrowser } from "../lib/browserEntry";
import { BrowserEntryPage } from "./BrowserEntryPage";

jest.mock("../lib/browserEntry", () => ({
  ...jest.requireActual("../lib/browserEntry"),
  openInviteApp: jest.fn(),
  isMobileBrowser: jest.fn(),
}));

const openInviteAppMock = openInviteApp as jest.MockedFunction<typeof openInviteApp>;

const isMobileBrowserMock = isMobileBrowser as jest.MockedFunction<typeof isMobileBrowser>;

const Entry = () => {
  const { pathname, search } = useLocation();
  return <p>{pathname + search}</p>;
};

const renderAtInvite = (): RenderResult =>
  render(
    <MemoryRouter initialEntries={["/join/ABCDEF23?linkCode=483920"]}>
      <Routes>
        <Route path="/join/:code" element={<BrowserEntryPage />} />
        <Route path="/rooms/:code" element={<Entry />} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  sessionStorage.clear();
  jest.clearAllMocks();
  isMobileBrowserMock.mockReturnValue(false);
});

describe("BrowserEntryPage", () => {
  it("모바일은 초대받은 방으로 자동 실행을 한 번 시도하고 버튼으로 재시도한다", async () => {
    isMobileBrowserMock.mockReturnValue(true);
    const user = userEvent.setup();
    const { unmount } = renderAtInvite();
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
    expect(openInviteAppMock).toHaveBeenCalledWith(
      "sssok://localhost/join/ABCDEF23?linkCode=483920",
    );
    unmount();
    renderAtInvite();
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "쏙 앱에서 열기" }));
    expect(openInviteAppMock).toHaveBeenCalledTimes(2);
    expect(openInviteAppMock).toHaveBeenLastCalledWith(
      "sssok://localhost/join/ABCDEF23?linkCode=483920",
    );
  });

  it("PC 미리보기는 두 버튼을 표시하고 앱을 자동 실행하지 않는다", () => {
    const { unmount } = renderAtInvite();
    expect(screen.getAllByRole("button")).toHaveLength(2);
    expect(openInviteAppMock).not.toHaveBeenCalled();
    unmount();
    renderAtInvite();
    expect(openInviteAppMock).not.toHaveBeenCalled();
  });

  it("앱 버튼은 초대 방 정보를 전달하고 웹 버튼은 같은 방으로 이동한다", async () => {
    const user = userEvent.setup();
    renderAtInvite();
    await user.click(screen.getByRole("button", { name: "쏙 앱에서 열기" }));
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
    expect(openInviteAppMock).toHaveBeenCalledWith(
      "sssok://localhost/join/ABCDEF23?linkCode=483920",
    );
    await user.click(screen.getByRole("button", { name: "웹에서 참여하기" }));
    expect(screen.getByText("/rooms/ABCDEF23?linkCode=483920")).toBeInTheDocument();
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
  });
});
