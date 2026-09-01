import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider, createMemoryRouter } from "react-router-dom";
import { http, HttpResponse } from "msw";

import { getRoomSession, saveRoomSession } from "@/entities/session";
import { markMediaDeleted } from "@/mocks/db";
import { mediaOfRoom, MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { routes } from "./routes";

const ROOM_CODE = "7K93QX2S";

const renderAt = (path: string) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
};

describe("라우트", () => {
  beforeEach(() => localStorage.clear());

  it("/ 는 홈 화면을 보여준다", () => {
    renderAt(ROUTES.home);

    expect(screen.getByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ })).toBeInTheDocument();
  });

  // 방 조회 결과에 따른 화면 분기는 RoomEntryPage.test.tsx 가 검증한다.
  // 여기서는 경로가 그 화면으로 연결되는지만 본다.
  it("/rooms/:code 는 방 입장 화면을 보여준다", () => {
    renderAt(ROUTES.roomEntry(ROOM_CODE));

    expect(screen.getByText(/불러오는 중/)).toBeInTheDocument();
  });

  it("/rooms/:code/gallery 는 갤러리 화면을 보여준다", async () => {
    const user = userEvent.setup();
    saveRoomSession(ROOM_CODE, {
      accessToken: "mock-token-10234",
      userId: 10234,
      nickname: "민수",
      expiresAt: "2099-01-01T00:00:00Z",
    });

    renderAt(ROUTES.gallery(ROOM_CODE));

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();
    expect(await screen.findByAltText("IMG_0421.jpg")).toBeInTheDocument();
    expect(screen.getByAltText("VID_0032.mp4")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "내 사진" }));

    expect(screen.getByAltText("IMG_0421.jpg")).toBeInTheDocument();
    expect(screen.queryByAltText("VID_0032.mp4")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다른 사람 사진" }));

    expect(screen.queryByAltText("IMG_0421.jpg")).not.toBeInTheDocument();
    expect(screen.getByAltText("VID_0032.mp4")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "첫째 날 12" }));

    expect(screen.getByAltText("IMG_0421.jpg")).toBeInTheDocument();
    expect(screen.queryByAltText("VID_0032.mp4")).not.toBeInTheDocument();
  });

  it("갤러리 메뉴에서 방 설정 화면으로 이동한다", async () => {
    const user = userEvent.setup();
    const router = renderAtGallery();

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));
    await user.click(screen.getByRole("button", { name: "방 설정" }));

    expect(router.state.location.pathname).toBe(ROUTES.roomSettings(ROOM_CODE));
    expect(screen.getByRole("heading", { name: "방 설정" })).toBeInTheDocument();
  });

  it("방 설정을 변경하면 갤러리에 변경된 정보를 보여준다", async () => {
    const user = userEvent.setup();
    const router = renderAtGallery();

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));
    await user.click(screen.getByRole("button", { name: "방 설정" }));

    const nameInput = await screen.findByRole("textbox", { name: "방 이름" });
    await user.clear(nameInput);
    await user.type(nameInput, "제주 3박 4일");
    await user.click(screen.getByRole("radio", { name: "방장만" }));
    await user.click(screen.getByRole("button", { name: "변경 사항 저장" }));

    expect(await screen.findByRole("heading", { name: "제주 3박 4일" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(ROUTES.gallery(ROOM_CODE));
  });

  it("만료 시간만 변경한 뒤 설정 화면에 다시 진입할 수 있다", async () => {
    const user = userEvent.setup();
    renderAtGallery();

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));
    await user.click(screen.getByRole("button", { name: "방 설정" }));
    await user.click(await screen.findByRole("radio", { name: "3일" }));
    await user.click(screen.getByRole("button", { name: "변경 사항 저장" }));

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));
    await user.click(screen.getByRole("button", { name: "방 설정" }));

    expect(await screen.findByRole("textbox", { name: "방 이름" })).toHaveValue("제주 여행");
  });

  it("갤러리 메뉴에서 방을 삭제하면 세션을 지우고 홈으로 이동한다", async () => {
    const user = userEvent.setup();
    const router = renderAtGallery();

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "방 메뉴 열기" }));
    await user.click(screen.getByRole("button", { name: "방 삭제" }));

    expect(screen.getByRole("heading", { name: "방을 삭제할까요?" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "방 삭제 경고" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "삭제하기" }));

    expect(
      await screen.findByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(ROUTES.home);
    expect(getRoomSession(ROOM_CODE)).toBeNull();
  });

  it("사진 선택 중에는 올리기 버튼을 숨기고 선택 해제 후 다시 표시한다", async () => {
    const user = userEvent.setup();
    saveRoomSession(ROOM_CODE, {
      accessToken: "mock-token-10234",
      userId: 10234,
      nickname: "민수",
      expiresAt: "2099-01-01T00:00:00Z",
    });
    renderAt(ROUTES.gallery(ROOM_CODE));

    const photo = await screen.findByRole("button", { name: "IMG_0421.jpg 선택" });
    expect(screen.getByRole("button", { name: "사진 올리기" })).toBeInTheDocument();

    await user.click(photo);
    expect(screen.queryByRole("button", { name: "사진 올리기" })).not.toBeInTheDocument();

    await user.click(photo);
    expect(screen.getByRole("button", { name: "사진 올리기" })).toBeInTheDocument();
  });

  it("갤러리와 상세 화면의 체크 상태가 왕복 이동 후에도 연동된다", async () => {
    const user = userEvent.setup();
    renderAtGallery();
    await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
      method: "POST",
      headers: { Authorization: "Bearer mock-token-10234" },
    });
    await user.click(await screen.findByRole("button", { name: "IMG_0421.jpg 선택" }));
    await user.click(screen.getByRole("button", { name: "IMG_0421.jpg 크게 보기" }));
    const selection = await screen.findByRole("button", { name: "사진 선택" });
    expect(selection).toHaveAttribute("aria-pressed", "true");
    await user.click(selection);
    await user.click(screen.getByRole("button", { name: "갤러리로 돌아가기" }));
    expect(await screen.findByRole("button", { name: "IMG_0421.jpg 선택" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("삭제 성공에 JSON 본문이 있어도 갤러리로 돌아가 목록을 다시 조회한다", async () => {
    const user = userEvent.setup();
    const listRequests = jest.fn();
    server.use(
      http.get(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media`, () => {
        listRequests();
        return HttpResponse.json({ data: { items: mediaOfRoom(MOCK_ROOM_ID) } });
      }),
      http.delete(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media/5012`, () => {
        markMediaDeleted(MOCK_ROOM_ID, 5012);
        return HttpResponse.json({ data: null });
      }),
    );
    const router = renderAtGallery();
    await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
      method: "POST",
      headers: { Authorization: "Bearer mock-token-10234" },
    });
    await user.click(await screen.findByRole("button", { name: "IMG_0421.jpg 선택" }));
    await user.click(screen.getByRole("button", { name: "IMG_0421.jpg 크게 보기" }));
    const requestsBeforeDelete = listRequests.mock.calls.length;
    await user.click(await screen.findByRole("button", { name: "사진 삭제" }));
    expect(screen.queryByRole("heading", { name: "사진을 삭제할까요?" })).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "IMG_0419.jpg 선택" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(ROUTES.gallery(ROOM_CODE));
    expect(screen.queryByAltText("IMG_0421.jpg")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "사진 올리기" })).toBeInTheDocument();
    await waitFor(() =>
      expect(listRequests.mock.calls.length).toBeGreaterThan(requestsBeforeDelete),
    );
  });

  it("방 생성에 성공하면 생성된 방의 갤러리로 이동한다", async () => {
    const user = userEvent.setup();
    const router = renderAt(ROUTES.createRoom);

    await user.type(screen.getByRole("textbox", { name: "내 이름" }), "민수");
    await user.type(screen.getByRole("textbox", { name: "방 이름" }), "제주 여행");
    await user.click(screen.getByRole("button", { name: "방 만들기" }));

    expect(await screen.findByRole("heading", { name: "제주 여행" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(ROUTES.gallery("7K93QX2S"));
  });

  it("알 수 없는 주소는 홈으로 보낸다", () => {
    const router = renderAt("/이런-주소는-없다");

    expect(router.state.location.pathname).toBe(ROUTES.home);
    expect(screen.getByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ })).toBeInTheDocument();
  });
});

const renderAtGallery = () => {
  saveRoomSession(ROOM_CODE, {
    accessToken: "mock-token-10234",
    userId: 10234,
    nickname: "민수",
    expiresAt: "2099-01-01T00:00:00Z",
  });

  return renderAt(ROUTES.gallery(ROOM_CODE));
};
