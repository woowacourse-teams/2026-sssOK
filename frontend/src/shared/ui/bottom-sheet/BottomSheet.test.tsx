import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BottomSheet } from "./BottomSheet";

test("title과 children을 렌더링한다", () => {
  render(
    <BottomSheet title="새 폴더 만들기">
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  expect(screen.getByText("새 폴더 만들기")).toBeInTheDocument();
  expect(screen.getByText("폴더 이름을 입력해 주세요.")).toBeInTheDocument();
});

test("배경(오버레이)을 클릭하면 onClose가 호출된다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <BottomSheet title="새 폴더 만들기" onClose={handleClose}>
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  await user.click(screen.getByTestId("bottom-sheet-overlay"));

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("시트 내부를 클릭해도 onClose가 호출되지 않는다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <BottomSheet title="새 폴더 만들기" onClose={handleClose}>
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  await user.click(screen.getByText("새 폴더 만들기"));

  expect(handleClose).not.toHaveBeenCalled();
});

test("onClose 없이도 렌더링되고 배경 클릭 시 에러가 발생하지 않는다", async () => {
  const user = userEvent.setup();
  render(
    <BottomSheet title="새 폴더 만들기">
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  await expect(user.click(screen.getByTestId("bottom-sheet-overlay"))).resolves.not.toThrow();
});

test("onClose를 전달하면 닫기 핸들을 렌더링한다", () => {
  render(
    <BottomSheet title="새 폴더 만들기" onClose={jest.fn()}>
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  expect(screen.getByTestId("bottom-sheet-handle")).toBeInTheDocument();
});

test("onClose를 전달하지 않으면 닫기 핸들을 렌더링하지 않는다", () => {
  render(
    <BottomSheet title="새 폴더 만들기">
      <p>폴더 이름을 입력해 주세요.</p>
    </BottomSheet>,
  );

  expect(screen.queryByTestId("bottom-sheet-handle")).not.toBeInTheDocument();
});
