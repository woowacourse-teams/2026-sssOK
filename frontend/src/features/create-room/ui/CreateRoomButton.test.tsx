import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { CreateRoomButton } from "./CreateRoomButton";

describe("CreateRoomButton", () => {
  it("클릭하면 방 생성 화면으로 이동한다", async () => {
    const user = userEvent.setup();
    const router = createMemoryRouter(
      [
        { path: "/", element: <CreateRoomButton /> },
        { path: "/rooms/create", element: null },
      ],
      { initialEntries: ["/"] },
    );

    render(<RouterProvider router={router} />);

    await user.click(screen.getByRole("button", { name: "방 만들기" }));

    expect(router.state.location.pathname).toBe("/rooms/create");
  });
});
