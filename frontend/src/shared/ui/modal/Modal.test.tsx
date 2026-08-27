import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Modal } from "./Modal";

test("닫기 버튼을 렌더링한다", () => {
  render(
    <Modal onClose={jest.fn()}>
      <p>본문</p>
    </Modal>,
  );

  expect(screen.getByRole("button", { name: "닫기" })).toBeInTheDocument();
});

test("showClose 가 false 면 닫기 버튼을 그리지 않는다", () => {
  render(
    <Modal onClose={jest.fn()} showClose={false}>
      <p>본문</p>
    </Modal>,
  );

  expect(screen.queryByRole("button", { name: "닫기" })).not.toBeInTheDocument();
});

// X 를 끄더라도 바깥을 눌러 나가는 길은 막지 않는다. 나갈 길이 아예 없는 모달이 되면 안 된다.
test("showClose 가 false 여도 배경 클릭은 그대로 닫는다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <Modal onClose={handleClose} showClose={false}>
      <p>본문</p>
    </Modal>,
  );

  await user.click(screen.getByTestId("modal-overlay"));

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("children을 렌더링한다", () => {
  render(
    <Modal onClose={jest.fn()}>
      <p>삭제한 사진은 모든 폴더에서 함께 삭제돼요.</p>
    </Modal>,
  );

  expect(screen.getByText("삭제한 사진은 모든 폴더에서 함께 삭제돼요.")).toBeInTheDocument();
});

test("닫기 버튼을 클릭하면 onClose가 호출된다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <Modal onClose={handleClose}>
      <p>본문</p>
    </Modal>,
  );

  await user.click(screen.getByRole("button", { name: "닫기" }));

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("배경(오버레이)을 클릭하면 onClose가 호출된다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <Modal onClose={handleClose}>
      <p>본문</p>
    </Modal>,
  );

  await user.click(screen.getByTestId("modal-overlay"));

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("카드 내부를 클릭해도 onClose가 호출되지 않는다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <Modal onClose={handleClose}>
      <p>본문</p>
    </Modal>,
  );

  await user.click(screen.getByText("본문"));

  expect(handleClose).not.toHaveBeenCalled();
});
