import { act, render, screen } from "@testing-library/react";

import { Spinner, SPINNER_DELAY_MS } from "./Spinner";

describe("Spinner", () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it("안내 문구는 처음부터 status 로 알린다", () => {
    render(<Spinner label="방 정보를 불러오는 중이에요." />);

    expect(screen.getByRole("status")).toHaveTextContent("방 정보를 불러오는 중이에요.");
  });

  it("지연 시간이 지나기 전에는 스피너를 그리지 않는다", () => {
    const { container } = render(<Spinner label="불러오는 중" />);

    expect(container.querySelector("[aria-hidden]")).toBeNull();

    act(() => jest.advanceTimersByTime(SPINNER_DELAY_MS));

    expect(container.querySelector("[aria-hidden]")).toBeInTheDocument();
  });

  it("delayMs 가 0 이면 바로 그린다", () => {
    const { container } = render(<Spinner label="불러오는 중" delayMs={0} />);

    expect(container.querySelector("[aria-hidden]")).toBeInTheDocument();
  });
});
