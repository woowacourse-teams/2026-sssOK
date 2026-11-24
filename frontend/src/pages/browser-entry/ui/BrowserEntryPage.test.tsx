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
  isMobileBrowserMock.mockReturnValue(true);
});

describe("BrowserEntryPage", () => {
  it("모바일은 같은 초대의 앱 자동 실행을 세션에서 한 번만 시도한다", () => {
    const { unmount } = renderAtInvite();
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
    expect(openInviteAppMock).toHaveBeenCalledWith(
      "sssok://localhost/join/ABCDEF23?linkCode=483920",
    );
    unmount();
    renderAtInvite();
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button", { name: "쏙 앱에서 열기" })).not.toBeInTheDocument();
  });

  it("PC는 앱 실행 없이 초대 정보를 유지해 웹 입장 화면으로 이동한다", () => {
    isMobileBrowserMock.mockReturnValue(false);
    renderAtInvite();
    expect(screen.getByText("/rooms/ABCDEF23?linkCode=483920")).toBeInTheDocument();
    expect(openInviteAppMock).not.toHaveBeenCalled();
  });

  it("설치 버튼을 표시하고 웹 참여는 같은 방으로 이동한다", async () => {
    const user = userEvent.setup();
    renderAtInvite();
    expect(screen.getByRole("link", { name: "Google Play" })).toHaveAttribute(
      "href",
      "https://play.google.com/store/apps/details?id=com.ssssok.app&pcampaignid=web_share",
    );
    expect(screen.getByRole("link", { name: "App Store" })).toHaveAttribute(
      "href",
      "https://apps.apple.com/kr/app/%EC%8F%99-%ED%95%A8%EA%BB%98-%EB%AA%A8%EC%9C%BC%EB%8A%94-%EC%82%AC%EC%A7%84/id6828711923",
    );
    await user.click(screen.getByRole("button", { name: "웹으로 참여하기" }));
    expect(screen.getByText("/rooms/ABCDEF23?linkCode=483920")).toBeInTheDocument();
    expect(openInviteAppMock).toHaveBeenCalledTimes(1);
  });
});
