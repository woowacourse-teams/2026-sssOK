import { render, screen } from "@testing-library/react";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { lazyPage } from "./lazyPage";
import { RouteErrorPage } from "./RouteErrorPage";

const LoadedPage = () => <h1>불러온 화면</h1>;

const renderLazy = (load: () => Promise<{ LoadedPage: typeof LoadedPage }>) => {
  const router = createMemoryRouter(
    [
      {
        path: "/",
        element: lazyPage(load, (module) => module.LoadedPage),
        errorElement: <RouteErrorPage />,
      },
    ],
    { initialEntries: ["/"] },
  );
  render(<RouterProvider router={router} />);
};

describe("lazyPage", () => {
  // 오류 경계가 잡은 렌더링 오류를 React 가 콘솔에 남긴다. 테스트 출력만 조용히 한다.
  beforeEach(() => jest.spyOn(console, "error").mockImplementation(() => {}));
  afterEach(() => jest.restoreAllMocks());

  it("화면 코드를 받는 동안 빈 화면 대신 로딩 상태를 보여준다", () => {
    renderLazy(() => new Promise(() => {}));

    expect(screen.getByRole("status")).toHaveTextContent("화면을 불러오는 중이에요.");
  });

  it("화면 코드를 받으면 그 화면을 보여준다", async () => {
    renderLazy(() => Promise.resolve({ LoadedPage }));

    expect(await screen.findByRole("heading", { name: "불러온 화면" })).toBeInTheDocument();
  });

  it("배포로 이전 화면 코드를 받지 못하면 새로고침을 안내한다", async () => {
    const chunkError = Object.assign(new Error("Loading chunk 907 failed."), {
      name: "ChunkLoadError",
    });
    renderLazy(() => Promise.reject(chunkError));

    expect(
      await screen.findByRole("heading", { name: "화면을 불러오지 못했어요" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "홈으로 가기" })).toBeInTheDocument();
  });

  it("그 밖의 오류는 일반 오류 안내를 보여준다", async () => {
    renderLazy(() => Promise.reject(new Error("알 수 없는 오류")));

    expect(await screen.findByRole("heading", { name: "문제가 생겼어요" })).toBeInTheDocument();
  });
});
