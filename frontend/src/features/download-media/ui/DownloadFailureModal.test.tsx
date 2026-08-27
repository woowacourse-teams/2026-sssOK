import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DownloadFailureModal } from "./DownloadFailureModal";

const renderModal = (props: Partial<Parameters<typeof DownloadFailureModal>[0]> = {}) => {
  const onRetry = jest.fn();
  const onClose = jest.fn();

  render(
    <DownloadFailureModal
      count={2}
      message="아직 처리 중이에요"
      isRetryable
      onRetry={onRetry}
      onClose={onClose}
      {...props}
    />,
  );

  return { onRetry, onClose };
};

describe("DownloadFailureModal", () => {
  it("못 받은 장수를 제목에 말한다", () => {
    renderModal();

    expect(screen.getByRole("heading", { name: "앗, 2장을 못 받았어요" })).toBeInTheDocument();
  });

  it("셀 장수가 없으면 장수를 말하지 않는다", () => {
    renderModal({ count: 0 });

    expect(screen.getByRole("heading", { name: "앗, 받지 못했어요" })).toBeInTheDocument();
  });

  // 업로드 모달과 달리 사유를 말한다 — 기다리면 되는 실패와 그렇지 않은 실패가 섞여서다.
  it("왜 못 받았는지 사유를 보여준다", () => {
    renderModal({ message: "받는 중인 요청이 많아요" });

    expect(screen.getByText("받는 중인 요청이 많아요")).toBeInTheDocument();
  });

  it("다시 받아볼 수 없는 실패에는 재시도를 내주지 않는다", () => {
    renderModal({ isRetryable: false });

    expect(screen.queryByRole("button", { name: "재시도" })).not.toBeInTheDocument();
    // 모달 자체의 X 도 "닫기" 라서 둘이 잡힌다. 나갈 길이 남아 있는지만 본다.
    expect(screen.getAllByRole("button", { name: "닫기" }).length).toBeGreaterThan(0);
  });

  it("재시도를 누르면 부르는 쪽에 넘긴다", async () => {
    const { onRetry } = renderModal();

    await userEvent.click(screen.getByRole("button", { name: "재시도" }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
