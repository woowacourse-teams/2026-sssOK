import { render, screen } from "@testing-library/react";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { routes } from "./routes";

const ROOM_CODE = "7K93QX2S";

const renderAt = (path: string) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] });

  render(<RouterProvider router={router} />);

  return router;
};

describe("라우트", () => {
  it("/ 는 홈 화면을 보여준다", () => {
    renderAt(ROUTES.home);

    expect(screen.getByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ })).toBeInTheDocument();
  });

  it("/rooms/:code 는 방 입장 화면을 보여주고 코드를 읽는다", () => {
    renderAt(ROUTES.roomEntry(ROOM_CODE));

    expect(screen.getByText(new RegExp(ROOM_CODE))).toBeInTheDocument();
  });

  it("/rooms/:code/gallery 는 갤러리 화면을 보여준다", () => {
    renderAt(ROUTES.gallery(ROOM_CODE));

    expect(screen.getByText(/갤러리 화면/)).toBeInTheDocument();
  });

  it("알 수 없는 주소는 홈으로 보낸다", () => {
    const router = renderAt("/이런-주소는-없다");

    expect(router.state.location.pathname).toBe(ROUTES.home);
    expect(screen.getByRole("heading", { name: /사진 모으고 바로 쏙 나누기/ })).toBeInTheDocument();
  });
});
