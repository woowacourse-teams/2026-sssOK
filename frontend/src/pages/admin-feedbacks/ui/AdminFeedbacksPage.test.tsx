import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { routes } from "@/app/routes";
import { getAdminSession, saveAdminSession } from "@/entities/admin-session";
import { triggerIntersection } from "@/mocks/intersectionObserver";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";

const ADMIN_SESSION = {
  accessToken: "mock-admin-token",
  adminId: 4,
  loginId: "haeni",
  name: "해니",
  role: "ADMIN",
  expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
};

const renderPage = () => {
  const queryClient = new QueryClient({
    // 실패를 그대로 화면까지 올려야 한다 — 재시도하면 에러 상태를 볼 수 없다.
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [ROUTES.adminFeedbacks] });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
};

const respondWith = (status: number, body: Record<string, unknown>) => {
  server.use(
    http.get(`${API_BASE_URL}/admin/feedbacks`, () => HttpResponse.json(body, { status })),
  );
};

/** 첫 페이지가 다 그려질 때까지 기다린다. */
const waitForFirstPage = async () => {
  await screen.findByText(
    "사진 30장 올리는데 5분 넘게 걸렸어요. 중간에 멈춘 줄 알고 몇 번이나 껐다 켰습니다.",
  );
};

const chips = () => screen.getAllByRole("button", { name: /^fe-v/ });

const scrollToEnd = async () => {
  await act(async () => {
    triggerIntersection();
  });
};

describe("AdminFeedbacksPage", () => {
  beforeEach(() => {
    localStorage.clear();
    saveAdminSession(ADMIN_SESSION);
  });

  afterEach(() => server.events.removeAllListeners());

  it("의견을 최신순으로 나열한다", async () => {
    renderPage();
    await waitForFirstPage();

    const items = screen.getAllByRole("listitem");

    expect(items).toHaveLength(20);
    expect(items[0]).toHaveTextContent("사진 30장 올리는데 5분 넘게 걸렸어요");
    expect(items[1]).toHaveTextContent("다운로드 눌렀는데 zip이 안 열려요");
  });

  it("방·멤버·기기·브라우저·작성 시각을 한 줄로 보여준다", async () => {
    renderPage();
    await waitForFirstPage();

    expect(
      screen.getByText("방 128 · 멤버 871 · iPhone · Safari 17.4 · 09-16 08:41"),
    ).toBeInTheDocument();
  });

  it("UA 가 없으면 기기·브라우저 자리를 비우고 나머지 메타는 그대로 보여준다", async () => {
    renderPage();
    await waitForFirstPage();

    expect(screen.getByText("방 300 · 멤버 1601 · 09-14 23:12")).toBeInTheDocument();
  });

  it("해석되지 않는 UA 여도 항목이 빠지지 않는다", async () => {
    renderPage();
    await waitForFirstPage();

    expect(screen.getByText("API로 직접 올려봤는데 응답이 느려요.")).toBeInTheDocument();
    expect(screen.getByText("방 400 · 멤버 1700 · 09-14 11:20")).toBeInTheDocument();
  });

  it("버전이 없는 의견에는 버전 배지를 붙이지 않는다", async () => {
    renderPage();
    await waitForFirstPage();

    const withoutVersion = screen.getByText("의견 남기는 곳을 찾기가 어려웠습니다.").closest("li");

    expect(within(withoutVersion as HTMLElement).queryByText(/^fe-v/)).not.toBeInTheDocument();
  });

  it("불러온 의견에 들어 있는 버전만 최신순으로 칩에 그린다", async () => {
    renderPage();
    await waitForFirstPage();

    expect(chips().map((chip) => chip.textContent)).toEqual(["fe-v0.1.0", "fe-v0.0.9"]);
  });

  it("칩을 누르면 그 버전의 의견만 남는다", async () => {
    const user = userEvent.setup();
    renderPage();
    await waitForFirstPage();

    await user.click(screen.getByRole("button", { name: "fe-v0.0.9" }));

    expect(screen.getByRole("button", { name: "fe-v0.0.9" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      screen.queryByText(
        "사진 30장 올리는데 5분 넘게 걸렸어요. 중간에 멈춘 줄 알고 몇 번이나 껐다 켰습니다.",
      ),
    ).not.toBeInTheDocument();
    expect(screen.getByText("닉네임을 잘못 적었는데 바꿀 방법을 못 찾겠어요.")).toBeInTheDocument();
  });

  it("선택한 칩을 다시 누르면 전체로 돌아온다", async () => {
    const user = userEvent.setup();
    renderPage();
    await waitForFirstPage();

    const chip = screen.getByRole("button", { name: "fe-v0.0.9" });

    await user.click(chip);
    await user.click(chip);

    expect(chip).toHaveAttribute("aria-pressed", "false");
    expect(screen.getAllByRole("listitem")).toHaveLength(20);
  });

  it("목록 끝이 보이면 다음 페이지를 이어 붙이고, 새로 온 버전만큼 칩이 늘어난다", async () => {
    renderPage();
    await waitForFirstPage();

    expect(chips()).toHaveLength(2);

    await scrollToEnd();

    await waitFor(() => expect(screen.getAllByRole("listitem")).toHaveLength(26));
    expect(chips().map((chip) => chip.textContent)).toEqual([
      "fe-v0.1.0",
      "fe-v0.0.9",
      "fe-v0.0.8",
    ]);
  });

  it("마지막 페이지까지 불러온 뒤에는 더 요청하지 않는다", async () => {
    const requested: string[] = [];

    server.events.on("request:start", ({ request }) => {
      if (request.url.includes("/admin/feedbacks")) requested.push(request.url);
    });

    renderPage();
    await waitForFirstPage();
    await scrollToEnd();
    await waitFor(() => expect(screen.getAllByRole("listitem")).toHaveLength(26));

    await scrollToEnd();

    expect(requested).toHaveLength(2);
  });

  it("의견이 한 건도 없으면 빈 상태를 보여준다", async () => {
    respondWith(200, { data: { feedbacks: [], nextCursor: null, hasNext: false } });
    renderPage();

    expect(await screen.findByText("아직 들어온 의견이 없어요.")).toBeInTheDocument();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
  });

  it("401 이면 세션을 버리고 로그인 화면으로 되돌린다", async () => {
    respondWith(401, { code: "UNAUTHORIZED", message: "인증이 필요합니다" });
    const router = renderPage();

    await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.adminLogin));
    expect(getAdminSession()).toBeNull();
  });

  it("403 이면 권한 안내만 보여주고 재시도를 권하지 않는다", async () => {
    respondWith(403, { code: "ADMIN_FORBIDDEN", message: "권한이 없습니다" });
    renderPage();

    expect(await screen.findByText("이 계정으로는 의견을 볼 수 없어요.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
  });

  it("불러오지 못하면 다시 시도할 수 있다", async () => {
    respondWith(500, { code: "INTERNAL_SERVER_ERROR", message: "서버 오류" });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("의견을 불러오지 못했어요.")).toBeInTheDocument();

    server.resetHandlers();
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitForFirstPage();
  });

  describe("의견 상세", () => {
    const LONG_CONTENT_START = "업로드 중에 화면을 끄면 다시 처음부터 올라가요.";

    const respondDetailWith = (status: number, body: Record<string, unknown>) => {
      server.use(
        http.get(`${API_BASE_URL}/admin/feedbacks/:feedbackId`, () =>
          HttpResponse.json(body, { status }),
        ),
      );
    };

    const openFeedback = async (content: RegExp | string) => {
      const user = userEvent.setup();

      renderPage();
      await waitForFirstPage();
      await user.click(screen.getByRole("button", { name: new RegExp(content) }));

      return { user, dialog: await screen.findByRole("dialog") };
    };

    it("의견을 누르면 목록에서 잘린 본문을 모달에서 끝까지 보여준다", async () => {
      renderPage();
      await waitForFirstPage();

      const listItem = screen.getByText(new RegExp(LONG_CONTENT_START));

      expect(listItem.textContent).toMatch(/\.\.\.$/);

      await userEvent
        .setup()
        .click(screen.getByRole("button", { name: new RegExp(LONG_CONTENT_START) }));
      const dialog = await screen.findByRole("dialog");

      expect(within(dialog).getByRole("heading", { name: "의견 #18" })).toBeInTheDocument();
      expect(
        await within(dialog).findByText(/적어도 화면을 켜 두라는 안내라도 있었으면 좋겠습니다\.$/),
      ).toBeInTheDocument();
    });

    it("방·작성자·기기·버전·작성 시각을 보여준다", async () => {
      const { dialog } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      expect(await within(dialog).findByText("128 · 제주도 여행")).toBeInTheDocument();
      expect(within(dialog).getByText("871 · 민수")).toBeInTheDocument();
      expect(within(dialog).getByText("iPhone · Safari 17.4")).toBeInTheDocument();
      expect(within(dialog).getByText(/^Mozilla\/5\.0 \(iPhone;/)).toBeInTheDocument();
      expect(within(dialog).getByText("fe-v0.1.0")).toBeInTheDocument();
      expect(within(dialog).getByText("2026-09-16 08:41")).toBeInTheDocument();
    });

    it("방으로 이동하는 링크를 두지 않는다", async () => {
      const { dialog } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      await within(dialog).findByText("128 · 제주도 여행");

      expect(within(dialog).queryByRole("link")).not.toBeInTheDocument();
    });

    it("닉네임·UA·버전이 없어도 모달이 열리고 빈 값은 알 수 없음으로 적는다", async () => {
      const { dialog } = await openFeedback("의견 남기는 곳을 찾기가 어려웠습니다");

      expect(await within(dialog).findByText("1601")).toBeInTheDocument();
      expect(within(dialog).getAllByText("알 수 없음")).toHaveLength(2);
    });

    it("해석되지 않는 UA 는 원본만 보여준다", async () => {
      const { dialog } = await openFeedback("API로 직접 올려봤는데 응답이 느려요");

      expect(await within(dialog).findByText("curl/8.4.0")).toBeInTheDocument();
    });

    it("닫기 버튼으로 닫는다", async () => {
      const { user, dialog } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      await user.click(within(dialog).getByRole("button", { name: "닫기" }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("ESC 로 닫는다", async () => {
      const { user } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      await user.keyboard("{Escape}");

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("바깥을 누르면 닫는다", async () => {
      const { user } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      await user.click(screen.getByTestId("modal-overlay"));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("401 이면 세션을 버리고 로그인 화면으로 되돌린다", async () => {
      respondDetailWith(401, { code: "UNAUTHORIZED", message: "인증이 필요합니다" });
      const user = userEvent.setup();
      const router = renderPage();
      await waitForFirstPage();

      await user.click(screen.getByRole("button", { name: /사진 30장 올리는데/ }));

      await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.adminLogin));
      expect(getAdminSession()).toBeNull();
    });

    it("403 이면 권한 안내만 보여주고 재시도를 권하지 않는다", async () => {
      respondDetailWith(403, { code: "ADMIN_FORBIDDEN", message: "권한이 없습니다" });
      const { dialog } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      expect(
        await within(dialog).findByText("이 계정으로는 의견을 볼 수 없어요."),
      ).toBeInTheDocument();
      expect(within(dialog).queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
    });

    it("404 이면 없어진 의견이라고 알리고 재시도를 권하지 않는다", async () => {
      respondDetailWith(404, { code: "FEEDBACK_NOT_FOUND", message: "존재하지 않는 의견입니다" });
      const { dialog } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      expect(await within(dialog).findByText("없어진 의견이에요.")).toBeInTheDocument();
      expect(within(dialog).queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
    });

    it("불러오지 못하면 모달 안에서 다시 시도할 수 있다", async () => {
      respondDetailWith(500, { code: "INTERNAL_SERVER_ERROR", message: "서버 오류" });
      const { user, dialog } = await openFeedback("사진 30장 올리는데 5분 넘게 걸렸어요");

      // 서버 실패는 훅이 한 번 더 불러 본 뒤(기본 1초 뒤)에야 화면에 올라온다.
      expect(
        await within(dialog).findByText("의견을 불러오지 못했어요.", {}, { timeout: 3000 }),
      ).toBeInTheDocument();

      server.resetHandlers();
      await user.click(within(dialog).getByRole("button", { name: "다시 시도" }));

      expect(await within(dialog).findByText("128 · 제주도 여행")).toBeInTheDocument();
    });
  });
});
