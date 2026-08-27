import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { UploadFailureModal } from "./UploadFailureModal";

const renderModal = (count = 2) => {
  const onRetry = jest.fn();
  const onClose = jest.fn();

  render(<UploadFailureModal count={count} onRetry={onRetry} onClose={onClose} />);

  return { onRetry, onClose };
};

/**
 * 모달 자체의 X 와 본문의 버튼이 접근성 이름을 "닫기" 로 나눠 갖는다. 둘 다 닫는 버튼이라
 * 겹치는 게 맞다. 대신 X 는 `aria-label` 로, 본문 버튼은 글자로 잡아 서로 구분한다.
 */
const closeButton = () => screen.getByText("닫기");
const dismissIcon = () => screen.getByLabelText("닫기");

describe("UploadFailureModal", () => {
  it("못 올린 장수를 제목에 그대로 보여준다", () => {
    renderModal(2);

    expect(screen.getByRole("heading", { name: "앗, 2장을 못올렸어요" })).toBeInTheDocument();
  });

  it("장수는 넘겨받은 값을 따른다", () => {
    renderModal(13);

    expect(screen.getByRole("heading", { name: "앗, 13장을 못올렸어요" })).toBeInTheDocument();
  });

  it("재시도를 누르면 onRetry 가 불린다", async () => {
    const user = userEvent.setup();
    const { onRetry, onClose } = renderModal();

    await user.click(screen.getByRole("button", { name: "재시도" }));

    expect(onRetry).toHaveBeenCalledTimes(1);
    // 재시도는 닫기를 겸하지 않는다. 닫을지 말지는 부르는 쪽이 정한다.
    expect(onClose).not.toHaveBeenCalled();
  });

  it("닫기를 누르면 onClose 가 불린다", async () => {
    const user = userEvent.setup();
    const { onRetry, onClose } = renderModal();

    await user.click(closeButton());

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onRetry).not.toHaveBeenCalled();
  });

  it("모달 위쪽 X 로도 닫힌다", async () => {
    const user = userEvent.setup();
    const { onClose } = renderModal();

    await user.click(dismissIcon());

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("마스코트는 읽어주지 않는다 — 제목이 이미 같은 말을 한다", () => {
    renderModal();

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
});
