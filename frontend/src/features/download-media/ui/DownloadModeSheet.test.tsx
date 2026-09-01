import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DownloadModeSheet } from "./DownloadModeSheet";
import { prefersShareSheet } from "../lib/prefersShareSheet";

// 기기 판별 자체는 prefersShareSheet.test 가 지킨다. 여기서는 갈림길의 결과만 본다.
jest.mock("../lib/prefersShareSheet", () => ({ prefersShareSheet: jest.fn() }));

const prefersShareSheetMock = prefersShareSheet as jest.MockedFunction<typeof prefersShareSheet>;

const renderSheet = ({ isPhone = false, count = 2 } = {}) => {
  prefersShareSheetMock.mockReturnValue(isPhone);

  const onSubmit = jest.fn();
  const onClose = jest.fn();

  render(
    <DownloadModeSheet count={count} roomCode="7K93QX2S" onSubmit={onSubmit} onClose={onClose} />,
  );

  return { onSubmit, onClose };
};

describe("DownloadModeSheet", () => {
  it("고른 장수를 제목에 말한다", () => {
    renderSheet({ count: 7 });

    expect(screen.getByText("7개를 어떻게 받을까요?")).toBeInTheDocument();
  });

  // 항목이 셋이 되면 고르는 부담이 늘고, 폰에서는 닿지 않는 항목이 끼게 된다.
  it("항목은 언제나 두 개다", () => {
    renderSheet();

    expect(screen.getAllByRole("radio")).toHaveLength(2);
  });

  it("데스크톱에서는 위 항목이 개별로 저장이다", () => {
    renderSheet({ isPhone: false });

    expect(screen.getByText("개별로 저장")).toBeInTheDocument();
    expect(screen.queryByText("사진첩에 저장")).not.toBeInTheDocument();
  });

  /**
   * 실기기에서 확인할 수 없는 #122·#123 완료 조건이다.
   * 폰에서 낱장 저장은 첫 장만 받아지므로 그 자리를 사진첩이 대신한다.
   */
  it("폰에서는 위 항목이 사진첩에 저장으로 바뀐다", () => {
    renderSheet({ isPhone: true });

    expect(screen.getByText("사진첩에 저장")).toBeInTheDocument();
    expect(screen.queryByText("개별로 저장")).not.toBeInTheDocument();
  });

  it("zip 항목이 받게 될 파일명을 미리 보여준다", () => {
    renderSheet();

    expect(screen.getByText("ShareDrop_7K93QX2S.zip")).toBeInTheDocument();
  });

  it("고른 방식을 그대로 넘긴다", async () => {
    const { onSubmit } = renderSheet({ isPhone: false });

    await userEvent.click(screen.getByRole("radio", { name: /개별로 저장/ }));
    await userEvent.click(screen.getByRole("button", { name: "다운로드" }));

    expect(onSubmit).toHaveBeenCalledWith("individual");
  });

  // 여러 장을 받을 때 실제로 끝까지 동작하는 쪽이 기본이어야 한다.
  it("아무것도 고치지 않으면 zip 으로 받는다", async () => {
    const { onSubmit } = renderSheet();

    await userEvent.click(screen.getByRole("button", { name: "다운로드" }));

    expect(onSubmit).toHaveBeenCalledWith("zip");
  });

  it("시트를 닫기만 하면 받기를 시작하지 않는다", async () => {
    const { onSubmit, onClose } = renderSheet();

    // 시트는 바깥을 눌러 닫는다 (`BottomSheet` 참고).
    await userEvent.click(screen.getByTestId("bottom-sheet-overlay"));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
