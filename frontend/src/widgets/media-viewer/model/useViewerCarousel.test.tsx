import { act, createEvent, fireEvent, render, screen } from "@testing-library/react";
import { useViewerCarousel } from "./useViewerCarousel";

const setup = (
  activeMediaId = 2,
): { strip: HTMLElement; onChange: jest.Mock; unmount: () => void } => {
  const onChange = jest.fn();
  const Carousel = () => {
    const { ref, events } = useViewerCarousel([1, 2, 3], activeMediaId, onChange);
    return (
      <div ref={ref} data-testid="strip" {...events}>
        <button onClick={() => onChange(3)}>tap</button>
      </div>
    );
  };
  const view = render(<Carousel />);
  const strip = screen.getByTestId("strip");
  Object.defineProperty(strip, "clientWidth", { value: 360 });
  return { strip, onChange, unmount: view.unmount };
};

const drag = (strip: HTMLElement, dx: number): void => {
  fireEvent.touchStart(strip, { touches: [{ clientX: 180, clientY: 200 }] });
  fireEvent.touchMove(strip, { touches: [{ clientX: 180 + dx, clientY: 200 }] });
  fireEvent.touchEnd(strip, { changedTouches: [{ clientX: 180 + dx, clientY: 200 }] });
};

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

it.each([
  [-100, 3],
  [100, 1],
])("드래그 %i의 전환 완료 후 %i번으로 한 번만 이동한다", (dx, mediaId) => {
  const { strip, onChange } = setup();
  drag(strip, dx);
  fireEvent.click(screen.getByText("tap"));
  expect(onChange).not.toHaveBeenCalled();

  const end = createEvent.transitionEnd(strip);
  Object.defineProperty(end, "propertyName", { value: "transform" });
  fireEvent(strip, end);
  act(() => jest.advanceTimersByTime(270));
  expect(onChange).toHaveBeenCalledTimes(1);
  expect(onChange).toHaveBeenCalledWith(mediaId);
});

it.each([3, 16, 45])("%ipx의 짧은 드래그는 현재 사진의 정확한 위치로 복귀한다", (dx) => {
  const { strip, onChange } = setup();
  drag(strip, dx);
  act(() => jest.advanceTimersByTime(270));
  expect(strip.style.transform).toBe("translate3d(-100%, 0, 0)");
  expect(strip.style.transition).toBe("none");
  expect(onChange).not.toHaveBeenCalled();
});

it.each([
  [1, 120, 0],
  [3, -120, -200],
])("목록 경계 %i에서 바깥으로 드래그하면 복귀한다", (activeId, dx, position) => {
  const { strip, onChange } = setup(activeId);
  drag(strip, dx);
  act(() => jest.advanceTimersByTime(270));
  expect(strip.style.transform).toBe(`translate3d(${position}%, 0, 0)`);
  expect(onChange).not.toHaveBeenCalled();
});

it("멀티터치로 취소하면 원위치로 돌아오고 전환 중 추가 터치는 무시한다", () => {
  const { strip, onChange } = setup();
  fireEvent.touchStart(strip, { touches: [{ clientX: 180, clientY: 200 }] });
  fireEvent.touchMove(strip, { touches: [{ clientX: 80, clientY: 200 }] });
  fireEvent.touchMove(strip, {
    touches: [
      { clientX: 80, clientY: 200 },
      { clientX: 100, clientY: 200 },
    ],
  });
  drag(strip, -120);
  act(() => jest.advanceTimersByTime(270));
  expect(strip.style.transform).toBe("translate3d(-100%, 0, 0)");
  expect(onChange).not.toHaveBeenCalled();
});

it("닫히면 예약된 전환을 취소한다", () => {
  const { strip, onChange, unmount } = setup();
  drag(strip, -120);
  unmount();
  act(() => jest.advanceTimersByTime(270));
  expect(onChange).not.toHaveBeenCalled();
});
