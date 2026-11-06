import { act, fireEvent, render, screen } from "@testing-library/react";

import { ToastProvider, useToast } from "./ToastProvider";

const Trigger = () => {
  const { showToast } = useToast();
  return (
    <>
      <button onClick={() => showToast({ message: "완료", showClose: true })}>성공</button>
      <button onClick={() => showToast({ message: "실패", tone: "error", duration: 2000 })}>
        실패
      </button>
    </>
  );
};

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

it("같은 알림을 다시 호출하면 기존 타이머를 취소하고 표시 시간을 갱신한다", () => {
  render(
    <ToastProvider>
      <Trigger />
    </ToastProvider>,
  );
  fireEvent.click(screen.getByText("성공"));
  act(() => jest.advanceTimersByTime(3000));
  fireEvent.click(screen.getByText("성공"));
  act(() => jest.advanceTimersByTime(1000));
  expect(screen.getByRole("status")).toHaveTextContent("완료");
  expect(screen.getAllByRole("status")).toHaveLength(1);
  act(() => jest.advanceTimersByTime(3000));
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
});

it("새 알림으로 기존 알림을 교체하고 새 유지 시간을 적용한다", () => {
  render(
    <ToastProvider>
      <Trigger />
    </ToastProvider>,
  );
  fireEvent.click(screen.getByText("성공"));
  fireEvent.click(screen.getByText("실패"));
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(screen.getByRole("alert")).toHaveTextContent("실패");
  act(() => jest.advanceTimersByTime(2000));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it("호출한 화면이 사라져도 알림을 유지하고 닫기 버튼으로 제거한다", () => {
  const view = render(
    <ToastProvider>
      <Trigger />
    </ToastProvider>,
  );
  fireEvent.click(screen.getByText("성공"));
  view.rerender(
    <ToastProvider>
      <p>다음 화면</p>
    </ToastProvider>,
  );
  expect(screen.getByRole("status")).toHaveTextContent("완료");
  fireEvent.click(screen.getByRole("button", { name: "알림 닫기" }));
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
});

it("열린 dialog 안에 표시하고 dialog가 사라지면 본문으로 옮긴다", async () => {
  const dialog = document.createElement("dialog");
  dialog.setAttribute("open", "");
  document.body.append(dialog);
  render(
    <ToastProvider>
      <Trigger />
    </ToastProvider>,
  );
  fireEvent.click(screen.getByText("성공"));
  expect(dialog).toContainElement(screen.getByRole("status"));
  await act(async () => dialog.remove());
  expect(document.body).toContainElement(screen.getByRole("status"));
});
