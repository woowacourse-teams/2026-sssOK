import { render, screen } from "@testing-library/react";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { MOCK_ROOM_CODES } from "@/mocks/handlers/room";
import { ROUTES } from "@/shared/config";
import { routes } from "./routes";

const renderAt = (path: string) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] });

  render(<RouterProvider router={router} />);

  return router;
};

describe("라우트", () => {
  it("/ 는 홈 화면을 보여준다", () => {
    renderAt(ROUTES.home);

    expect(screen.getByText(/홈 화면/)).toBeInTheDocument();
  });

  it("/rooms/:code 는 방 입장 화면을 보여주고 코드를 읽는다", () => {
    renderAt(ROUTES.roomEntry(MOCK_ROOM_CODES.active));

    expect(screen.getByText(new RegExp(MOCK_ROOM_CODES.active))).toBeInTheDocument();
  });

  it("/rooms/:code/gallery 는 갤러리 화면을 보여준다", () => {
    renderAt(ROUTES.gallery(MOCK_ROOM_CODES.active));

    expect(screen.getByText(/갤러리 화면/)).toBeInTheDocument();
  });

  it("알 수 없는 주소는 홈으로 보낸다", () => {
    const router = renderAt("/이런-주소는-없다");

    expect(router.state.location.pathname).toBe(ROUTES.home);
    expect(screen.getByText(/홈 화면/)).toBeInTheDocument();
  });
});
