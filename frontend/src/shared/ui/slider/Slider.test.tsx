import { fireEvent, render, screen } from "@testing-library/react";

import { Slider } from "./Slider";

describe("Slider", () => {
  it("라벨과 표기된 값을 보여준다", () => {
    render(
      <Slider
        label="방 만료 기간"
        name="expiryDays"
        value={3}
        min={1}
        max={14}
        formatValue={(value) => `${value}일`}
        onValueChange={() => undefined}
      />,
    );

    const slider = screen.getByRole("slider", { name: "방 만료 기간" });
    expect(slider).toHaveValue("3");
    expect(slider).toHaveAttribute("aria-valuetext", "3일");
    expect(screen.getByText("3일")).toBeInTheDocument();
  });

  it("값을 바꾸면 숫자로 전달한다", () => {
    const handleValueChange = jest.fn();
    render(
      <Slider
        label="방 만료 기간"
        name="expiryDays"
        value={1}
        min={1}
        max={14}
        onValueChange={handleValueChange}
      />,
    );

    fireEvent.change(screen.getByRole("slider"), { target: { value: "7" } });

    expect(handleValueChange).toHaveBeenCalledWith(7);
  });

  it("눈금을 누르면 그 값으로 옮겨 간다", () => {
    const handleValueChange = jest.fn();
    render(
      <Slider
        label="방 만료 기간"
        name="expiryDays"
        value={1}
        min={1}
        max={14}
        marks={[
          { value: 1, label: "1일" },
          { value: 7, label: "1주" },
          { value: 14, label: "2주" },
        ]}
        onValueChange={handleValueChange}
      />,
    );

    fireEvent.click(screen.getByText("2주"));

    expect(handleValueChange).toHaveBeenCalledWith(14);
  });
});
