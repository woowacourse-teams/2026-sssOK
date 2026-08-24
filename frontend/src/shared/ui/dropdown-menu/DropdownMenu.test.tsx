import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DropdownMenu } from "./DropdownMenu";

test("전달한 children을 그대로 렌더링한다", () => {
  render(
    <DropdownMenu onClose={jest.fn()}>
      <button type="button">방 설정</button>
      <button type="button">방 삭제</button>
    </DropdownMenu>,
  );

  expect(screen.getByRole("button", { name: "방 설정" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "방 삭제" })).toBeInTheDocument();
});

test("배경(오버레이)을 클릭하면 onClose가 호출된다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <DropdownMenu onClose={handleClose}>
      <button type="button">방 설정</button>
    </DropdownMenu>,
  );

  await user.click(screen.getByTestId("dropdown-menu-overlay"));

  expect(handleClose).toHaveBeenCalledTimes(1);
});

test("메뉴 내부를 클릭해도 onClose가 호출되지 않는다", async () => {
  const user = userEvent.setup();
  const handleClose = jest.fn();
  render(
    <DropdownMenu onClose={handleClose}>
      <button type="button">방 설정</button>
    </DropdownMenu>,
  );

  await user.click(screen.getByRole("button", { name: "방 설정" }));

  expect(handleClose).not.toHaveBeenCalled();
});
