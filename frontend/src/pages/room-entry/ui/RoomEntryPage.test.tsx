import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { getRoomSession, saveRoomSession } from "@/entities/session";
import { MOCK_ROOM_CODES } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTE_PATTERNS } from "@/shared/config";
import { RoomEntryPage } from "./RoomEntryPage";

const FUTURE = "2099-01-01T00:00:00Z";
const PAST = "2020-01-01T00:00:00Z";

const session = (expiresAt: string) => ({
  accessToken: "mock-token-99999",
  userId: 10234,
  nickname: "민수",
  expiresAt,
});

const GALLERY_TEXT = "갤러리 도착";

/** MSW 가 넘겨주는 request.url 은 언제나 절대 URL 이다. 베이스가 상대경로여도 맞춰 볼 수 있게 푼다. */
const absolute = (path: string) => new URL(path, location.href).href;

const renderAt = (code: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/rooms/${code}`]}>
        <Routes>
          <Route path={ROUTE_PATTERNS.roomEntry} element={<RoomEntryPage />} />
          <Route path={ROUTE_PATTERNS.gallery} element={<div>{GALLERY_TEXT}</div>} />
          <Route path={ROUTE_PATTERNS.home} element={<div>홈 도착</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const getSubmitButton = () => screen.getByRole("button", { name: "입장하기" });

beforeEach(() => localStorage.clear());

describe("RoomEntryPage", () => {
  it("조회하는 동안 로딩 문구를 보여준다", () => {
    renderAt(MOCK_ROOM_CODES.active);

    expect(screen.getByText(/불러오는 중/)).toBeInTheDocument();
  });

  describe("토큰이 없을 때", () => {
    it("이름 입력 모달을 띄우고, 이름이 비어 있으면 입장하기가 비활성화된다", async () => {
      renderAt(MOCK_ROOM_CODES.active);

      expect(
        await screen.findByRole("heading", { name: "표시할 이름을 입력해주세요" }),
      ).toBeInTheDocument();
      expect(getSubmitButton()).toBeDisabled();
    });

    it("이름을 넣고 입장하면 이 방 토큰으로 저장하고 갤러리로 이동한다", async () => {
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.active);

      await user.type(await screen.findByRole("textbox"), "해니");
      await user.click(getSubmitButton());

      expect(await screen.findByText(GALLERY_TEXT)).toBeInTheDocument();
      const saved = getRoomSession(MOCK_ROOM_CODES.active);
      expect(saved?.accessToken).toBeTruthy();
      expect(saved?.nickname).toBe("해니");
    });

    it("인증 토큰으로 방 입장까지 마친다", async () => {
      let joinRequest: { url: string; authorization: string | null } | null = null;
      server.use(
        http.post(`${API_BASE_URL}/rooms/:roomId/members`, ({ request }) => {
          joinRequest = {
            url: request.url,
            authorization: request.headers.get("Authorization"),
          };
          return HttpResponse.json({ data: { roomId: 5031 } }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.active);

      await user.type(await screen.findByRole("textbox"), "해니");
      await user.click(getSubmitButton());

      expect(await screen.findByText(GALLERY_TEXT)).toBeInTheDocument();
      expect(joinRequest).toEqual({
        // 입장 API 는 방 코드가 아니라 방 조회 응답의 roomId 를 쓴다
        url: absolute(`${API_BASE_URL}/rooms/5031/members`),
        authorization: "Bearer mock-token-10234",
      });
    });

    it("방 입장에 실패하면 저장한 세션을 도로 지우고 에러 화면을 보여준다", async () => {
      server.use(
        http.post(`${API_BASE_URL}/rooms/:roomId/members`, () =>
          HttpResponse.json({ code: "ROOM_JOIN_FAILED", message: "안돼요" }, { status: 500 }),
        ),
      );
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.active);

      await user.type(await screen.findByRole("textbox"), "해니");
      await user.click(getSubmitButton());

      expect(await screen.findByText(/입장하지 못했어요/)).toBeInTheDocument();
      // 세션만 남으면 다음 방문 때 입장하지 않은 채 갤러리로 새어 들어간다
      expect(getRoomSession(MOCK_ROOM_CODES.active)).toBeNull();
    });

    it("인증에 실패하면 에러 화면을 보여준다", async () => {
      server.use(
        http.post(`${API_BASE_URL}/auth/anonymous`, () =>
          HttpResponse.json({ code: "INVALID_NICKNAME", message: "안돼요" }, { status: 400 }),
        ),
      );
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.active);

      await user.type(await screen.findByRole("textbox"), "해니");
      await user.click(getSubmitButton());

      expect(await screen.findByText(/입장하지 못했어요/)).toBeInTheDocument();
    });
  });

  describe("토큰이 있을 때", () => {
    it("다른 방 세션은 그대로 두고 이 방 세션만 새 member 로 덧붙인다", async () => {
      saveRoomSession("ABCD2345", session(FUTURE));
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.active);

      await user.type(await screen.findByRole("textbox"), "해니");
      await user.click(getSubmitButton());

      expect(await screen.findByText(GALLERY_TEXT)).toBeInTheDocument();
      // 방마다 인증을 새로 하므로 방마다 다른 member 가 방 코드별로 나란히 쌓인다
      expect(getRoomSession("ABCD2345")).toMatchObject({
        accessToken: "mock-token-99999",
        nickname: "민수",
      });
      expect(getRoomSession(MOCK_ROOM_CODES.active)).toMatchObject({
        accessToken: "mock-token-10234",
        nickname: "해니",
      });
    });

    it("다른 방 토큰만 있으면 이 방에서는 이름을 다시 묻는다", async () => {
      saveRoomSession("ABCD2345", session(FUTURE));

      renderAt(MOCK_ROOM_CODES.active);

      expect(
        await screen.findByRole("heading", { name: "표시할 이름을 입력해주세요" }),
      ).toBeInTheDocument();
    });

    it("유효한 토큰이면 이름 모달 없이 갤러리로 이동한다", async () => {
      saveRoomSession(MOCK_ROOM_CODES.active, session(FUTURE));

      renderAt(MOCK_ROOM_CODES.active);

      expect(await screen.findByText(GALLERY_TEXT)).toBeInTheDocument();
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    });

    it("만료된 토큰이면 지우고 이름 모달을 띄운다", async () => {
      saveRoomSession(MOCK_ROOM_CODES.active, session(PAST));

      renderAt(MOCK_ROOM_CODES.active);

      expect(
        await screen.findByRole("heading", { name: "표시할 이름을 입력해주세요" }),
      ).toBeInTheDocument();
      expect(getRoomSession(MOCK_ROOM_CODES.active)).toBeNull();
    });
  });

  describe("들어갈 수 없는 방", () => {
    it("만료된 방은 안내와 함께 그 방 토큰만 지운다", async () => {
      saveRoomSession(MOCK_ROOM_CODES.active, session(FUTURE));
      saveRoomSession(MOCK_ROOM_CODES.expired, session(FUTURE));

      renderAt(MOCK_ROOM_CODES.expired);

      expect(await screen.findByText(/만료된 방이에요/)).toBeInTheDocument();
      expect(getRoomSession(MOCK_ROOM_CODES.expired)).toBeNull();
      // 다른 방 토큰은 그 방에서 계속 쓴다
      expect(getRoomSession(MOCK_ROOM_CODES.active)).not.toBeNull();
    });

    it("삭제된 방은 삭제 안내를 보여준다", async () => {
      renderAt(MOCK_ROOM_CODES.deleted);

      expect(await screen.findByText(/삭제된 방이에요/)).toBeInTheDocument();
    });
  });

  describe("방 조회 실패", () => {
    it("존재하지 않는 방은 안내와 홈 링크를 보여준다", async () => {
      renderAt(MOCK_ROOM_CODES.notFound);

      expect(await screen.findByText("존재하지 않는 방이에요.")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "홈으로 돌아가기" })).toBeInTheDocument();
    });

    it("형식이 틀린 코드는 코드 형식 문제라고 안내한다", async () => {
      renderAt(MOCK_ROOM_CODES.invalid);

      expect(await screen.findByText("방 코드 형식이 올바르지 않아요.")).toBeInTheDocument();
    });

    it("홈으로 돌아가기를 누르면 홈으로 이동한다", async () => {
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.notFound);

      await user.click(await screen.findByRole("link", { name: "홈으로 돌아가기" }));

      expect(screen.getByText("홈 도착")).toBeInTheDocument();
    });
  });
});
