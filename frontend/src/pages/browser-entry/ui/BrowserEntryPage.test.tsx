import type { RenderResult } from "@testing-library/react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { openInviteApp, isMobileBrowser } from "../lib/browserEntry";
import { BrowserEntryPage } from "./BrowserEntryPage";

jest.mock("../lib/browserEntry", () => ({
  ...jest.requireActual("../lib/browserEntry"),
  openInviteApp: jest.fn(),
  isMobileBrowser: jest.fn(),
}));

const openInviteAppMock = openInviteApp as jest.MockedFunction<typeof openInviteApp>;
const isMobileBrowserMock = isMobileBrowser as jest.MockedFunction<typeof isMobileBrowser>;

const renderAtInvite = (): RenderResult<
  typeof import("@testing-library/dom/types/queries"),
  HTMLElement,
  HTMLElement
> =>
  render(
    <MemoryRouter initialEntries={["/join/ABCDEF23?linkCode=483920"]}>
      <Routes>
        <Route path="/join/:code" element={<BrowserEntryPage />} />
        <Route path="/rooms/:code" element={<p>기존 방 입장</p>} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  sessionStorage.clear();
  jest.clearAllMocks();
});

describe("BrowserEntryPage", () => {
  it("데스크톱 브라우저는 쿼리를 보존해 기존 방 입장으로 이동한다", () => {
    isMobileBrowserMock.mockReturnValue(false);

    renderAtInvite();

    expect(screen.getByText("기존 방 입장")).toBeInTheDocument();
    expect(openInviteAppMock).not.toHaveBeenCalled();
  });

  it("모바일에서는 앱 열기을 한 번 자동 시도한다", async () => {
    isMobileBrowserMock.mockReturnValue(true);

    const { unmount } = renderAtInvite();

    await waitFor(() => expect(openInviteAppMock).toHaveBeenCalledTimes(1));
    expect(openInviteAppMock.mock.calls[0][0]).toContain(
      "sssok://localhost/join/ABCDEF23?linkCode=483920",
    );

    unmount();
    renderAtInvite();
    await waitFor(() => expect(openInviteAppMock).toHaveBeenCalledTimes(1));
  });

  it("자동 이동 후에도 사용자가 다시 앱을 열 수 있다", async () => {
    isMobileBrowserMock.mockReturnValue(true);
    const user = userEvent.setup();
    renderAtInvite();

    await waitFor(() => expect(openInviteAppMock).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole("button", { name: "쏙 앱에서 열기" }));

    expect(openInviteAppMock).toHaveBeenCalledTimes(2);
    await user.click(screen.getByRole("button", { name: "웹에서 참여하기" }));
    expect(screen.getByText("기존 방 입장")).toBeInTheDocument();
  });
});
