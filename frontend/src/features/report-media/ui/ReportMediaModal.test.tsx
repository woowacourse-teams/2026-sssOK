import type { UserEvent } from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRoomSession } from "@/entities/member-session";
import { ApiError } from "@/shared/api";
import { reportMedia } from "../api/reportMedia";
import { ReportMediaModal } from "./ReportMediaModal";

jest.mock("react-router-dom", () => ({ useParams: () => ({ code: "ROOMCODE" }) }));
jest.mock("@/entities/member-session", () => ({ useRoomSession: jest.fn() }));
jest.mock("../api/reportMedia", () => ({ reportMedia: jest.fn() }));
const reportMock = jest.mocked(reportMedia);
beforeEach(() => {
  jest.resetAllMocks();
  jest.mocked(useRoomSession).mockReturnValue({
    session: { accessToken: "session-token" },
    isPending: false,
    isError: false,
  } as ReturnType<typeof useRoomSession>);
});
const setup = (): {
  onClose: ReturnType<typeof jest.fn>;
  onSubmit: ReturnType<typeof jest.fn>;
  user: UserEvent;
} => {
  const onClose = jest.fn();
  const onSubmit = jest.fn();
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}
    >
      <ReportMediaModal roomId={12} mediaId={34} onClose={onClose} onSubmit={onSubmit} />
    </QueryClientProvider>,
  );
  return { onClose, onSubmit, user: userEvent.setup() };
};

it("선택한 사유와 사진 정보로 접수하고 성공 후 안내한다", async () => {
  reportMock.mockResolvedValue({
    mediaId: 34,
    reason: "PRIVACY_VIOLATION",
    reportedAt: "2026-11-03T00:00:00Z",
  });
  const { user, onSubmit } = setup();
  expect(screen.getAllByRole("radio")).toHaveLength(4);
  expect(screen.getByRole("button", { name: "신고" })).toBeDisabled();
  await user.click(screen.getByRole("radio", { name: "저작권 침해" }));
  await user.click(screen.getByRole("radio", { name: "개인정보·초상권 침해" }));
  expect(screen.getByRole("radio", { name: "저작권 침해" })).not.toBeChecked();
  await user.click(screen.getByRole("button", { name: "신고" }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  expect(reportMock).toHaveBeenCalledWith({
    roomId: 12,
    mediaId: 34,
    token: "session-token",
    reason: "PRIVACY_VIOLATION",
  });
});

it("접수 중에는 중복 제출과 닫기를 막는다", async () => {
  let resolve!: (value: Awaited<ReturnType<typeof reportMedia>>) => void;
  reportMock.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const { user, onClose, onSubmit } = setup();
  await user.click(screen.getByRole("radio", { name: "저작권 침해" }));
  await user.click(screen.getByRole("button", { name: "신고" }));
  expect(screen.getByRole("button", { name: "신고 중…" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  await user.keyboard("{Escape}");
  expect(onClose).not.toHaveBeenCalled();
  resolve({ mediaId: 34, reason: "COPYRIGHT_VIOLATION", reportedAt: "2026-11-03T00:00:00Z" });
  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  expect(reportMock).toHaveBeenCalledTimes(1);
});

it("중복 신고 오류를 안내하고 성공 처리를 하지 않는다", async () => {
  reportMock.mockRejectedValue(new ApiError(409, "MEDIA_ALREADY_REPORTED", "duplicate"));
  const { user, onSubmit } = setup();
  await user.click(screen.getByRole("radio", { name: "괴롭힘·혐오 표현" }));
  await user.click(screen.getByRole("button", { name: "신고" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("이미 신고한 사진이에요.");
  expect(onSubmit).not.toHaveBeenCalled();
  expect(screen.getByRole("radio", { name: "괴롭힘·혐오 표현" })).toBeChecked();
});

it("취소하면 신고 요청 없이 닫는다", async () => {
  const { user, onClose } = setup();
  await user.click(screen.getByRole("button", { name: "취소" }));
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(reportMock).not.toHaveBeenCalled();
});

it("세션이 없으면 신고 요청을 보내지 않는다", async () => {
  jest
    .mocked(useRoomSession)
    .mockReturnValue({ session: null, isPending: false, isError: false } as ReturnType<
      typeof useRoomSession
    >);
  const { user } = setup();
  await user.click(screen.getByRole("radio", { name: "저작권 침해" }));
  expect(screen.getByRole("button", { name: "신고" })).toBeDisabled();
  expect(screen.getByRole("alert")).toHaveTextContent("로그인 정보를 확인하지 못했어요");
  expect(useRoomSession).toHaveBeenCalledWith("ROOMCODE");
  expect(reportMock).not.toHaveBeenCalled();
});
