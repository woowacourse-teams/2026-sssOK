import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { openChrome, shouldOpenInChrome } from "../lib/browserEntry";
import { BrowserEntryPage } from "./BrowserEntryPage";

jest.mock("../lib/browserEntry", () => ({
  ...jest.requireActual("../lib/browserEntry"),
  openChrome: jest.fn(),
  shouldOpenInChrome: jest.fn(),
}));

const openChromeMock = openChrome as jest.MockedFunction<typeof openChrome>;
const shouldOpenInChromeMock = shouldOpenInChrome as jest.MockedFunction<typeof shouldOpenInChrome>;

const renderAtInvite = () =>
  render(
    <MemoryRouter initialEntries={["/join/ABCD1234?linkCode=483920"]}>
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
  it("Chrome 이동이 필요 없는 브라우저는 쿼리를 보존해 기존 방 입장으로 이동한다", () => {
    shouldOpenInChromeMock.mockReturnValue(false);

    renderAtInvite();

    expect(screen.getByText("기존 방 입장")).toBeInTheDocument();
    expect(openChromeMock).not.toHaveBeenCalled();
  });

  it("Android 비-Chrome에서는 Chrome 이동을 한 번 자동 시도한다", async () => {
    shouldOpenInChromeMock.mockReturnValue(true);

    const { unmount } = renderAtInvite();

    await waitFor(() => expect(openChromeMock).toHaveBeenCalledTimes(1));
    expect(openChromeMock.mock.calls[0][0]).toContain(
      "intent://localhost/rooms/ABCD1234?linkCode=483920#Intent;scheme=http;",
    );

    unmount();
    renderAtInvite();
    await waitFor(() => expect(openChromeMock).toHaveBeenCalledTimes(1));
  });

  it("자동 이동 후에도 사용자가 다시 Chrome을 열 수 있다", async () => {
    shouldOpenInChromeMock.mockReturnValue(true);
    const user = userEvent.setup();
    renderAtInvite();

    await waitFor(() => expect(openChromeMock).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole("button", { name: "Chrome에서 계속하기" }));

    expect(openChromeMock).toHaveBeenCalledTimes(2);
  });
});
