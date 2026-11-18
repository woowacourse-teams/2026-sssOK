import { useState } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";

import { useGalleryDragSelection } from "./useGalleryDragSelection";

const open = jest.fn();
const rendered = jest.fn();
const Harness = ({ initialIds = [3] }: { initialIds?: number[] }) => {
  rendered();
  const [selectedIds, onSelect] = useState(initialIds);
  const { containerRef, isSelecting, rectangle } = useGalleryDragSelection({
    ids: [1, 2, 3],
    selectedIds,
    onSelect,
  });
  return (
    <>
      <section ref={containerRef} data-testid="gallery">
        {[1, 2, 3].map((id) => (
          <div key={id} data-media-id={id} data-testid={`card-${id}`}>
            <button onClick={open}>사진 {id}</button>
            <button onClick={open}>체크 {id}</button>
            <button data-drag-selection-ignore onClick={open}>
              좋아요 {id}
            </button>
          </div>
        ))}
      </section>
      <output data-testid="selected">{selectedIds.join(",")}</output>
      <output data-testid="active">{String(isSelecting)}</output>
      <output data-testid="rectangle">{rectangle ? "visible" : "hidden"}</output>
    </>
  );
};

const bounds = (x: number, y: number, width: number, height: number): DOMRect => ({
  x,
  y,
  left: x,
  top: y,
  right: x + width,
  bottom: y + height,
  width,
  height,
  toJSON: () => ({}),
});

const move = (x: number, y: number): void => {
  fireEvent.mouseMove(window, { clientX: x, clientY: y, buttons: 1 });
  act(() => {
    jest.advanceTimersByTime(17);
  });
};

beforeEach(() => {
  jest.useFakeTimers();
  open.mockClear();
  render(<Harness />);
  jest
    .spyOn(screen.getByTestId("gallery"), "getBoundingClientRect")
    .mockReturnValue(bounds(0, 0, 400, 500));
  const gallery = screen.getByTestId("gallery");
  gallery.style.overflowY = "auto";
  Object.defineProperties(gallery, {
    clientHeight: { configurable: true, value: 500 },
    scrollHeight: { configurable: true, value: 2000 },
  });
  [1, 2, 3].forEach((id) => {
    jest
      .spyOn(screen.getByTestId(`card-${id}`), "getBoundingClientRect")
      .mockReturnValue(bounds(20 + (id - 1) * 120, 80, 100, 100));
  });
});
afterEach(() => {
  jest.useRealTimers();
});

it("영역을 늘리고 줄여도 기존 선택은 보존하며 종료 뒤 상세 클릭을 막는다", () => {
  const first = screen.getByRole("button", { name: "사진 1" });
  fireEvent.mouseDown(first, { clientX: 30, clientY: 90, button: 0 });
  move(250, 190);
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1,2");
  move(100, 160);
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1");
  fireEvent.mouseUp(window);
  fireEvent.click(first, { detail: 1 });
  expect(open).not.toHaveBeenCalled();
  expect(screen.getByTestId("rectangle")).toHaveTextContent("hidden");
});

it("작은 움직임은 기존 클릭으로 처리하고 좋아요 버튼에서는 드래그하지 않는다", () => {
  const first = screen.getByRole("button", { name: "사진 1" });
  fireEvent.mouseDown(first, { clientX: 30, clientY: 90, button: 0 });
  move(32, 92);
  fireEvent.mouseUp(window);
  fireEvent.click(first);
  expect(open).toHaveBeenCalledTimes(1);
  fireEvent.mouseDown(screen.getByRole("button", { name: "좋아요 1" }), { button: 0 });
  move(250, 190);
  expect(screen.getByTestId("active")).toHaveTextContent("false");
});

it("Escape로 취소하면 시작 전 선택을 복원한다", () => {
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(250, 190);
  fireEvent.keyDown(window, { key: "Escape" });
  expect(screen.getByTestId("selected")).toHaveTextContent(/^3$/);
  expect(screen.getByTestId("active")).toHaveTextContent("false");
});

it("모바일은 길게 누른 뒤 연속 선택하고 touchcancel 시 복원한다", () => {
  const first = screen.getByTestId("card-1");
  Object.defineProperty(document, "elementFromPoint", {
    configurable: true,
    value: jest.fn(() => first),
  });
  fireEvent.touchStart(first, { touches: [{ clientX: 30, clientY: 90 }] });
  act(() => {
    jest.advanceTimersByTime(400);
  });
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1");
  jest.mocked(document.elementFromPoint).mockReturnValue(screen.getByTestId("card-2"));
  fireEvent.touchMove(first, { touches: [{ clientX: 160, clientY: 100 }] });
  act(() => {
    jest.advanceTimersByTime(17);
  });
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1,2");
  fireEvent.touchCancel(window);
  expect(screen.getByTestId("selected")).toHaveTextContent(/^3$/);
});

it("길게 누르기 전 움직이면 선택하지 않고 스크롤을 우선한다", () => {
  const first = screen.getByTestId("card-1");
  fireEvent.touchStart(first, { touches: [{ clientX: 30, clientY: 90 }] });
  const allowed = fireEvent.touchMove(first, { touches: [{ clientX: 30, clientY: 130 }] });
  act(() => {
    jest.advanceTimersByTime(500);
  });
  expect(allowed).toBe(true);
  expect(screen.getByTestId("active")).toHaveTextContent("false");
  expect(screen.getByTestId("selected")).toHaveTextContent(/^3$/);
});

it("목록 하단 가장자리에서 자동 스크롤하고 종료 후 멈춘다", () => {
  const gallery = screen.getByTestId("gallery");
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(100, 490);
  expect(gallery.scrollTop).toBeGreaterThan(0);
  fireEvent.mouseUp(window);
  const stoppedAt = gallery.scrollTop;
  act(() => {
    jest.advanceTimersByTime(100);
  });
  expect(gallery.scrollTop).toBe(stoppedAt);
});

it("체크 버튼도 길게 누르면 연속 선택을 시작하고 종료 후 중복 클릭을 막는다", () => {
  const check = screen.getByRole("button", { name: "체크 1" });
  Object.defineProperty(document, "elementFromPoint", {
    configurable: true,
    value: jest.fn(() => check),
  });
  fireEvent.touchStart(check, { touches: [{ clientX: 30, clientY: 90 }] });
  act(() => {
    jest.advanceTimersByTime(400);
  });
  expect(screen.getByTestId("active")).toHaveTextContent("true");
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1");
  expect(fireEvent.contextMenu(check)).toBe(false);
  jest.mocked(document.elementFromPoint).mockReturnValue(screen.getByTestId("card-2"));
  fireEvent.touchMove(check, { touches: [{ clientX: 160, clientY: 100 }] });
  act(() => {
    jest.advanceTimersByTime(17);
  });
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1,2");
  fireEvent.touchEnd(window);
  fireEvent.click(check, { detail: 1 });
  expect(open).not.toHaveBeenCalled();
});

it("체크 버튼을 짧게 누르면 기존 클릭을 유지한다", () => {
  const check = screen.getByRole("button", { name: "체크 1" });
  fireEvent.touchStart(check, { touches: [{ clientX: 30, clientY: 90 }] });
  act(() => {
    jest.advanceTimersByTime(100);
  });
  fireEvent.touchEnd(window);
  fireEvent.click(check, { detail: 1 });
  expect(open).toHaveBeenCalledTimes(1);
  expect(screen.getByTestId("active")).toHaveTextContent("false");
});

it("갤러리 대신 문서가 스크롤되는 화면에서도 아래 가장자리 드래그가 작동한다", () => {
  const gallery = screen.getByTestId("gallery");
  Object.defineProperty(gallery, "scrollHeight", { value: 500 });
  jest.mocked(gallery.getBoundingClientRect).mockReturnValue(bounds(0, 0, 400, 2000));
  document.documentElement.scrollTop = 0;
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(100, window.innerHeight - 10);
  expect(document.documentElement.scrollTop).toBeGreaterThan(0);
  expect(gallery.scrollTop).toBe(0);
  fireEvent.mouseUp(window);
  document.documentElement.scrollTop = 0;
});

it("모바일은 손가락을 가장자리에 유지하면 계속 스크롤하고 위쪽으로도 이동한다", () => {
  const gallery = screen.getByTestId("gallery");
  const first = screen.getByTestId("card-1");
  Object.defineProperty(document, "elementFromPoint", {
    configurable: true,
    value: jest.fn(() => (gallery.scrollTop > 40 ? screen.getByTestId("card-2") : first)),
  });
  fireEvent.touchStart(first, { touches: [{ clientX: 30, clientY: 90 }] });
  act(() => {
    jest.advanceTimersByTime(400);
  });
  fireEvent.touchMove(first, { touches: [{ clientX: 100, clientY: 490 }] });
  act(() => {
    jest.advanceTimersByTime(160);
  });
  expect(gallery.scrollTop).toBeGreaterThan(40);
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1,2");
  const lowerPosition = gallery.scrollTop;
  fireEvent.touchMove(first, { touches: [{ clientX: 100, clientY: 5 }] });
  act(() => {
    jest.advanceTimersByTime(32);
  });
  expect(gallery.scrollTop).toBeLessThan(lowerPosition);
  fireEvent.touchEnd(window);
  const stoppedAt = gallery.scrollTop;
  act(() => {
    jest.advanceTimersByTime(100);
  });
  expect(gallery.scrollTop).toBe(stoppedAt);
});

it("PC에서 누르기만 하면 선택 루프나 반복 렌더링을 시작하지 않는다", () => {
  const first = screen.getByTestId("card-1");
  const before = rendered.mock.calls.length;
  fireEvent.mouseDown(first, { clientX: 30, clientY: 90 });
  act(() => {
    jest.advanceTimersByTime(1000);
  });
  expect(rendered).toHaveBeenCalledTimes(before);
  expect(screen.getByTestId("active")).toHaveTextContent("false");
  fireEvent.mouseUp(window);
});

it("PC 드래그 중 포인터와 스크롤이 멈춰 있으면 반복 렌더링하지 않는다", () => {
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(250, 190);
  const afterMove = rendered.mock.calls.length;
  act(() => {
    jest.advanceTimersByTime(1000);
  });
  expect(rendered).toHaveBeenCalledTimes(afterMove);
  move(100, 160);
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1");
  fireEvent.mouseUp(window);
});

it("선택된 카드에서 시작하면 범위를 해제하고 범위를 줄이면 원래 선택을 복원한다", () => {
  // 3번은 처음부터 선택되어 있다. 먼저 1번도 선택한다.
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(100, 160);
  fireEvent.mouseUp(window);
  act(() => {
    jest.advanceTimersByTime(500);
  });
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1");

  fireEvent.mouseDown(screen.getByTestId("card-3"), { clientX: 280, clientY: 90 });
  move(30, 160);
  expect(screen.getByTestId("selected").textContent).toBe("");
  move(300, 160);
  expect(screen.getByTestId("selected")).toHaveTextContent(/^1$/);
  fireEvent.mouseUp(window);
  expect(screen.getByTestId("selected")).toHaveTextContent(/^1$/);
});

it("모바일에서도 선택된 체크 버튼을 길게 누르면 해제하며 취소 시 복원한다", () => {
  const check = screen.getByRole("button", { name: "체크 3" });
  Object.defineProperty(document, "elementFromPoint", {
    configurable: true,
    value: jest.fn(() => check),
  });
  fireEvent.touchStart(check, { touches: [{ clientX: 280, clientY: 90 }] });
  act(() => {
    jest.advanceTimersByTime(400);
  });
  expect(screen.getByTestId("selected").textContent).toBe("");
  fireEvent.touchCancel(window);
  expect(screen.getByTestId("selected")).toHaveTextContent(/^3$/);
});

it("마우스 드래그 직후 기다리지 않고 다음 드래그를 시작할 수 있다", () => {
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(100, 160);
  fireEvent.mouseUp(window);
  fireEvent.mouseDown(screen.getByTestId("card-2"), { clientX: 150, clientY: 90 });
  move(230, 160);
  expect(screen.getByTestId("active")).toHaveTextContent("true");
  expect(screen.getByTestId("selected")).toHaveTextContent("3,1,2");
  fireEvent.mouseUp(window);
});

it("포인터를 멈춘 채 휠로 스크롤해도 선택 영역을 다시 계산한다", () => {
  const gallery = screen.getByTestId("gallery");
  fireEvent.mouseDown(screen.getByTestId("card-1"), { clientX: 30, clientY: 90 });
  move(100, 160);
  const before = rendered.mock.calls.length;
  gallery.scrollTop = 50;
  act(() => {
    jest.advanceTimersByTime(17);
  });
  expect(rendered.mock.calls.length).toBeGreaterThan(before);
  fireEvent.mouseUp(window);
});
