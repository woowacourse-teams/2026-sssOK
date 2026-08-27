import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { UploadProgressBarProps } from "./UploadProgressBar";
import { UploadProgressBar } from "./UploadProgressBar";

const renderBar = (props: Partial<UploadProgressBarProps> = {}) =>
  render(
    <UploadProgressBar
      completedCount={0}
      totalCount={1}
      percent={0}
      onCancel={jest.fn()}
      {...props}
    />,
  );

describe("UploadProgressBar", () => {
  it("몇 장 중 몇 장이 끝났는지 보여준다", () => {
    renderBar({ completedCount: 20, totalCount: 24 });

    expect(screen.getByText("20 / 24")).toBeInTheDocument();
  });

  it("퍼센트를 글자와 값 양쪽으로 알린다 — 눈으로도, 보조 기기로도 읽힌다", () => {
    renderBar({ percent: 62 });

    const progressbar = screen.getByRole("progressbar", { name: "업로드 진행률" });

    expect(progressbar).toHaveTextContent("업로드 중... 62%");
    expect(progressbar).toHaveAttribute("aria-valuenow", "62");
  });

  it("장수와 퍼센트는 서로 다른 것을 센다 — 어긋나 보여도 그대로 그린다", () => {
    // 작은 사진 두 장이 끝나고 큰 영상 한 장이 남았다. 2/3 장이지만 회선으로는 2% 다.
    renderBar({ completedCount: 2, totalCount: 3, percent: 2 });

    expect(screen.getByText("2 / 3")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveTextContent("업로드 중... 2%");
  });

  it("취소를 누르면 되묻지 않고 곧바로 전달한다", async () => {
    const user = userEvent.setup();
    const onCancel = jest.fn();
    renderBar({ onCancel });

    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
