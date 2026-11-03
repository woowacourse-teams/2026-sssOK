import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportMediaModal } from "./ReportMediaModal";

it("사유를 선택해야 신고할 수 있다", async () => {
  const onSubmit = jest.fn();
  render(<ReportMediaModal onClose={jest.fn()} onSubmit={onSubmit} />);
  expect(screen.getAllByRole("radio")).toHaveLength(4);
  expect(screen.getByRole("button", { name: "신고" })).toBeDisabled();
  await userEvent.click(screen.getByRole("radio", { name: "저작권 침해" }));
  await userEvent.click(screen.getByRole("button", { name: "신고" }));
  expect(onSubmit).toHaveBeenCalledTimes(1);
});

it("취소하면 제출하지 않고 닫는다", async () => {
  const onClose = jest.fn();
  const onSubmit = jest.fn();
  render(<ReportMediaModal onClose={onClose} onSubmit={onSubmit} />);
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(onSubmit).not.toHaveBeenCalled();
});
