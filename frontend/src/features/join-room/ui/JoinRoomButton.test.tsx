import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { JoinRoomButton } from "./JoinRoomButton";

describe("JoinRoomButton", () => {
  it("클릭하면 코드 입장 화면으로 이동한다", async () => {
    const user = userEvent.setup();
    const router = createMemoryRouter(
      [
        { path: "/", element: <JoinRoomButton /> },
        { path: "/rooms/join", element: null },
      ],
      { initialEntries: ["/"] },
    );

    render(<RouterProvider router={router} />);

    await user.click(screen.getByRole("button", { name: "코드로 입장" }));

    expect(router.state.location.pathname).toBe("/rooms/join");
  });
});
