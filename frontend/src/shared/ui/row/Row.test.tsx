import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Row } from "./Row";

describe("Row", () => {
  it("children을 렌더링한다", () => {
    render(
      <Row>
        <span>Item 1</span>
        <span>Item 2</span>
      </Row>,
    );

    expect(screen.getByText("Item 1")).toBeInTheDocument();
    expect(screen.getByText("Item 2")).toBeInTheDocument();
  });

  it("레이아웃 스타일을 적용한다", () => {
    render(
      <Row gap={8} align="center" justify="space-between" data-testid="row">
        <span>Item</span>
      </Row>,
    );

    expect(screen.getByTestId("row")).toHaveStyle({
      display: "flex",
      flexDirection: "row",
      gap: "8px",
      alignItems: "center",
      justifyContent: "space-between",
    });
  });

  it("div의 기본 props를 전달한다", async () => {
    const user = userEvent.setup();
    const handleClick = jest.fn();

    render(
      <Row data-testid="row" onClick={handleClick}>
        Item
      </Row>,
    );

    await user.click(screen.getByTestId("row"));

    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
