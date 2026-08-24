import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Stack } from "./Stack";

describe("Stack", () => {
  it("children을 렌더링한다", () => {
    render(
      <Stack>
        <span>Item 1</span>
        <span>Item 2</span>
      </Stack>,
    );

    expect(screen.getByText("Item 1")).toBeInTheDocument();
    expect(screen.getByText("Item 2")).toBeInTheDocument();
  });

  it("레이아웃 스타일을 적용한다", () => {
    render(
      <Stack gap={8} align="center" justify="space-between" data-testid="stack">
        <span>Item</span>
      </Stack>,
    );

    expect(screen.getByTestId("stack")).toHaveStyle({
      display: "flex",
      flexDirection: "column",
      gap: "8px",
      alignItems: "center",
      justifyContent: "space-between",
    });
  });

  it("div의 기본 props를 전달한다", async () => {
    const user = userEvent.setup();
    const handleClick = jest.fn();

    render(
      <Stack data-testid="stack" onClick={handleClick}>
        Item
      </Stack>,
    );

    await user.click(screen.getByTestId("stack"));

    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
