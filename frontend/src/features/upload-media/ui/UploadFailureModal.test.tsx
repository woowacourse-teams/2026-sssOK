import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { UploadFailureModal } from "./UploadFailureModal";
import type { FailedUpload, UploadFailureCode } from "../model/types";

const failureOf = (fileName: string, code: UploadFailureCode = "UPLOAD_FAILED"): FailedUpload => ({
  mediaId: 1,
  fileName,
  code,
  message: "",
  file: new File([], fileName),
});

const renderModal = (failures = [failureOf("IMG_3390.png"), failureOf("movie_02.mov")]) => {
  const onRetry = jest.fn();
  const onClose = jest.fn();

  render(<UploadFailureModal failures={failures} onRetry={onRetry} onClose={onClose} />);

  return { onRetry, onClose };
};

describe("UploadFailureModal", () => {
  it("못 올린 장수를 제목에 그대로 보여준다", () => {
    renderModal();

    expect(screen.getByRole("heading", { name: "앗, 2장을 못 올렸어요" })).toBeInTheDocument();
  });

  it("장수는 넘겨받은 목록을 따른다", () => {
    renderModal(Array.from({ length: 13 }, (_, i) => failureOf(`IMG_${i}.png`)));

    expect(screen.getByRole("heading", { name: "앗, 13장을 못 올렸어요" })).toBeInTheDocument();
  });

  /**
   * 장수만 알려주면 재시도를 누를지 판단할 근거가 없다 — 회선 문제면 다시 누르면 되고,
   * 특정 파일만 계속 걸리면 그 파일을 빼야 한다 (시안 07g).
   */
  it("어느 파일이 왜 깨졌는지 파일마다 보여준다", () => {
    renderModal([failureOf("IMG_3390.png"), failureOf("movie_02.mov", "MEDIA_NOT_FOUND")]);

    expect(screen.getByText("IMG_3390.png")).toBeInTheDocument();
    expect(screen.getByText("네트워크 오류")).toBeInTheDocument();
    expect(screen.getByText("movie_02.mov")).toBeInTheDocument();
    expect(screen.getByText("찾을 수 없음")).toBeInTheDocument();
  });

  it("재시도를 누르면 onRetry 가 불린다", async () => {
    const user = userEvent.setup();
    const { onRetry, onClose } = renderModal();

    await user.click(screen.getByRole("button", { name: "실패만 재시도" }));

    expect(onRetry).toHaveBeenCalledTimes(1);
    // 재시도는 닫기를 겸하지 않는다. 닫을지 말지는 부르는 쪽이 정한다.
    expect(onClose).not.toHaveBeenCalled();
  });

  it("닫기를 누르면 onClose 가 불린다", async () => {
    const user = userEvent.setup();
    const { onRetry, onClose } = renderModal();

    await user.click(screen.getByRole("button", { name: "닫기" }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onRetry).not.toHaveBeenCalled();
  });

  /** 카드 안에 닫기가 이미 있다. X 를 또 두면 같은 일을 하는 버튼이 둘이 된다 (시안 07g). */
  it("오른쪽 위 X 를 그리지 않는다", () => {
    renderModal();

    expect(screen.getAllByRole("button", { name: "닫기" })).toHaveLength(1);
  });
});
