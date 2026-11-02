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

test("ESC 를 누르면 onClose가 호출된다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <Modal onClose={handleClose}>
      <p>본문</p>
    </Modal>,
  );

  await user.keyboard("{Escape}");

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("닫힌 뒤에는 ESC 에 반응하지 않는다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  const { unmount } = render(
    <Modal onClose={handleClose}>
      <p>본문</p>
    </Modal>,
  );

  unmount();
  await user.keyboard("{Escape}");

  expect(handleClose).not.toHaveBeenCalled();
});

test("title 을 넘기면 제목과 닫기 버튼을 함께 그린다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <Modal onClose={handleClose} title={<h2>의견 #41</h2>}>
      <p>본문</p>
    </Modal>,
  );

  expect(screen.getByRole("heading", { name: "의견 #41" })).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: "닫기" }));

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("title 이 있어도 showClose 가 false 면 닫기 버튼을 그리지 않는다", () => {
  render(
    <Modal onClose={jest.fn()} title={<h2>의견 #41</h2>} showClose={false}>
      <p>본문</p>
    </Modal>,
  );

  expect(screen.getByRole("heading", { name: "의견 #41" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "닫기" })).not.toBeInTheDocument();
});

test("열릴 때 이름 있는 네이티브 모달로 포커스를 옮긴다", () => {
  render(
    <Modal onClose={jest.fn()} showClose={false}>
      <h2>올릴 수 없는 형식이에요</h2>
      <button>확인</button>
    </Modal>,
  );
  const dialog = screen.getByRole("dialog", { name: "올릴 수 없는 형식이에요" });
  expect(dialog.tagName).toBe("DIALOG");
  expect(dialog).toHaveAttribute("open");
  expect(dialog).toHaveFocus();
});

test("Tab과 Shift+Tab이 모달 안에서 순환하고 Enter로 확인한다", async () => {
  const user = userEvent.setup();
  const onClose = jest.fn();
  render(
    <Modal onClose={onClose} showClose={false}>
      <h2>업로드 오류</h2>
      <button>다시 고르기</button>
      <button onClick={onClose}>확인</button>
    </Modal>,
  );
  const first = screen.getByRole("button", { name: "다시 고르기" });
  const last = screen.getByRole("button", { name: "확인" });
  await user.tab();
  expect(first).toHaveFocus();
  await user.tab({ shift: true });
  expect(last).toHaveFocus();
  await user.tab();
  expect(first).toHaveFocus();
  await user.tab();
  await user.keyboard("{Enter}");
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("다시 렌더링해도 포커스를 빼앗지 않고 닫으면 이전 위치로 복귀한다", async () => {
  const user = userEvent.setup();
  const trigger = document.createElement("button");
  document.body.append(trigger);
  trigger.focus();
  const { rerender, unmount } = render(
    <Modal onClose={() => {}}>
      <h2>안내</h2>
      <button>확인</button>
    </Modal>,
  );
  await user.click(screen.getByRole("button", { name: "확인" }));
  rerender(
    <Modal onClose={() => {}}>
      <h2>안내</h2>
      <button>확인</button>
    </Modal>,
  );
  expect(screen.getByRole("button", { name: "확인" })).toHaveFocus();
  unmount();
  expect(trigger).toHaveFocus();
  trigger.remove();
});
