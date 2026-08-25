import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { API_PREFIX } from "@/mocks/config";
import { MOCK_ROOM_CODES } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { tokenStorage } from "@/shared/api";
import { ROUTE_PATTERNS } from "@/shared/config";
import { RoomEntryPage } from "./RoomEntryPage";

const FUTURE = "2099-01-01T00:00:00Z";
const PAST = "2020-01-01T00:00:00Z";

const storedToken = (expiresAt: string) => ({
  accessToken: "abc",
  userId: 10234,
  nickname: "민수",
  expiresAt,
});

const GALLERY_TEXT = "갤러리 도착";

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

      expect(await screen.findByRole("heading", { name: "표시할 이름을 입력해주세요" })).toBeInTheDocument();
      expect(getSubmitButton()).toBeDisabled();
    });

    it("이름을 넣고 입장하면 토큰을 저장하고 갤러리로 이동한다", async () => {
      const user = userEvent.setup();
      renderAt(MOCK_ROOM_CODES.active);

      await user.type(await screen.findByRole("textbox"), "해니");
      await user.click(getSubmitButton());

      expect(await screen.findByText(GALLERY_TEXT)).toBeInTheDocument();
      expect(tokenStorage.current()?.accessToken).toBe("mock-access-token");
    });

    it("인증에 실패하면 에러 화면을 보여준다", async () => {
      server.use(
        http.post(`${API_PREFIX}/auth/anonymous`, () =>
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
    it("유효한 토큰이면 이름 모달 없이 갤러리로 이동한다", async () => {
      tokenStorage.save(MOCK_ROOM_CODES.active, storedToken(FUTURE));

      renderAt(MOCK_ROOM_CODES.active);

      expect(await screen.findByText(GALLERY_TEXT)).toBeInTheDocument();
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    });

    it("만료된 토큰이면 지우고 이름 모달을 띄운다", async () => {
      tokenStorage.save(MOCK_ROOM_CODES.active, storedToken(PAST));

      renderAt(MOCK_ROOM_CODES.active);

      expect(await screen.findByRole("heading", { name: "표시할 이름을 입력해주세요" })).toBeInTheDocument();
      expect(tokenStorage.current()).toBeNull();
    });
  });

  describe("들어갈 수 없는 방", () => {
    it("만료된 방은 안내와 함께 그 방 기록만 지운다", async () => {
      tokenStorage.save(MOCK_ROOM_CODES.active, storedToken(FUTURE));
      tokenStorage.save(MOCK_ROOM_CODES.expired, storedToken(FUTURE));

      renderAt(MOCK_ROOM_CODES.expired);

      expect(await screen.findByText(/만료된 방이에요/)).toBeInTheDocument();
      expect(tokenStorage.hasVisited(MOCK_ROOM_CODES.expired)).toBe(false);
      // 토큰 자체는 계정 것이라 다른 방에서 계속 쓴다
      expect(tokenStorage.current()).not.toBeNull();
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
