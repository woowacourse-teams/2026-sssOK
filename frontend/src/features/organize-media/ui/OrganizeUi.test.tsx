import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { MyOrganizeJob } from "../model/useOrganizeJob";
import { OrganizeCancelSheet } from "./OrganizeCancelSheet";
import { OrganizeKindSheet } from "./OrganizeKindSheet";
import { OrganizeLimitModal } from "./OrganizeLimitModal";
import { OrganizeStatusBar } from "./OrganizeStatusBar";

const jobOf = (overrides: Partial<MyOrganizeJob> = {}): MyOrganizeJob => ({
  jobId: "org_1",
  status: "RUNNING",
  percent: 45,
  photoCount: 40,
  retryable: true,
  peopleCount: null,
  ...overrides,
});

const barHandlers = () => ({
  onCancel: jest.fn(),
  onReview: jest.fn(),
  onRetry: jest.fn(),
  onDismiss: jest.fn(),
});

describe("OrganizeKindSheet", () => {
  it("사람별로만 고를 수 있고 비슷한 사진은 준비 중이다", async () => {
    const onStart = jest.fn();
    render(<OrganizeKindSheet isGuest={false} onStart={onStart} onClose={jest.fn()} />);

    expect(screen.getByRole("radio", { name: /비슷한 사진/ })).toBeDisabled();
    expect(screen.getByText("준비 중")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "얼굴 분석해서 정리하기" }));
    expect(onStart).toHaveBeenCalledWith("PERSON");
  });

  it("로그인하지 않았으면 웹에서는 앱을 설치하지 않으면 한 번만 체험할 수 있다고 알린다", () => {
    render(<OrganizeKindSheet isGuest onStart={jest.fn()} onClose={jest.fn()} />);

    expect(
      screen.getByText("앱을 설치하지 않으면 AI 정리는 한 번만 체험할 수 있어요."),
    ).toBeInTheDocument();
  });

  it("앱 안에서는 설치 대신 로그인하지 않으면 한 번만 체험할 수 있다고 알린다", () => {
    window.ReactNativeWebView = { postMessage: jest.fn() };
    try {
      render(<OrganizeKindSheet isGuest onStart={jest.fn()} onClose={jest.fn()} />);

      expect(
        screen.getByText("로그인하지 않으면 AI 정리는 한 번만 체험할 수 있어요."),
      ).toBeInTheDocument();
    } finally {
      delete window.ReactNativeWebView;
    }
  });
});

describe("OrganizeLimitModal", () => {
  it("200장을 넘으면 한도와 지금 장수를 알린다", async () => {
    const onClose = jest.fn();
    render(<OrganizeLimitModal limit={{ reason: "tooMany", count: 230 }} onClose={onClose} />);

    expect(screen.getByRole("heading", { name: "사진이 너무 많아요" })).toBeInTheDocument();
    expect(screen.getByText(/200장까지/)).toBeInTheDocument();
    expect(screen.getByText(/230장 있어요/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("장수를 모르면 한도만 알린다", () => {
    render(<OrganizeLimitModal limit={{ reason: "tooMany", count: null }} onClose={jest.fn()} />);

    expect(screen.queryByText(/장 있어요/)).not.toBeInTheDocument();
  });

  it("나눌 사진이 없으면 그렇게 알린다", () => {
    render(<OrganizeLimitModal limit={{ reason: "empty" }} onClose={jest.fn()} />);

    expect(screen.getByRole("heading", { name: "나눌 사진이 없어요" })).toBeInTheDocument();
  });

  it("체험을 다 쓰면 앱 설치 대신 로그인을 권한다", async () => {
    const onClose = jest.fn();
    const onLogin = jest.fn();
    render(
      <OrganizeLimitModal
        limit={{ reason: "trialExhausted" }}
        onClose={onClose}
        onLogin={onLogin}
      />,
    );

    expect(screen.getByRole("heading", { name: "AI 정리를 다 썼어요" })).toBeInTheDocument();
    expect(screen.queryByText(/앱을 설치/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "로그인하기" }));
    expect(onLogin).toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "다음에 할게요" }));
    expect(onClose).toHaveBeenCalled();
  });
});

describe("OrganizeStatusBar", () => {
  it("나누는 중에는 전체 진행률과 그만큼 본 장수를 보여 준다", async () => {
    const handlers = barHandlers();
    render(<OrganizeStatusBar job={jobOf()} {...handlers} />);

    expect(screen.getByText("18 / 40")).toBeInTheDocument();
    expect(screen.getByText("나누는 중... 45%")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "45");

    await userEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(handlers.onCancel).toHaveBeenCalled();
  });

  it("진행 정보를 아직 못 받았으면 0장째로 보여 준다", () => {
    render(<OrganizeStatusBar job={jobOf({ percent: null })} {...barHandlers()} />);

    expect(screen.getByText("나누는 중...")).toBeInTheDocument();
    expect(screen.getByText("0 / 40")).toBeInTheDocument();
  });

  it("접수만 된 상태는 분석 대기로 보여 준다", () => {
    render(
      <OrganizeStatusBar job={jobOf({ status: "QUEUED", percent: null })} {...barHandlers()} />,
    );

    expect(screen.getByText(/분석 대기 중/)).toBeInTheDocument();
  });

  it("완료되면 찾은 사람 수와 확인하기를 보여 준다", async () => {
    const handlers = barHandlers();
    render(<OrganizeStatusBar job={jobOf({ status: "READY", peopleCount: 6 })} {...handlers} />);

    expect(screen.getByText("6명을 찾았어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "확인하기" }));
    expect(handlers.onReview).toHaveBeenCalled();
  });

  it("실패하면 다시 시도를 보여 준다", async () => {
    const handlers = barHandlers();
    render(<OrganizeStatusBar job={jobOf({ status: "FAILED" })} {...handlers} />);

    expect(screen.getByText("사진을 나누지 못했어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(handlers.onRetry).toHaveBeenCalled();
  });

  it("다시 시도할 수 없는 실패는 닫기만 둔다", () => {
    render(
      <OrganizeStatusBar job={jobOf({ status: "FAILED", retryable: false })} {...barHandlers()} />,
    );

    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
  });

  it("찾은 사람이 없으면 사람 없음 알림을 보여 준다", () => {
    render(
      <OrganizeStatusBar job={jobOf({ status: "READY", peopleCount: 0 })} {...barHandlers()} />,
    );

    expect(screen.getByText("사람이 찍힌 사진을 찾지 못했어요")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
  });
});

describe("OrganizeCancelSheet", () => {
  it("멈추기와 계속 나누기를 나눠 부른다", async () => {
    const onStop = jest.fn();
    const onKeepGoing = jest.fn();
    render(<OrganizeCancelSheet onStop={onStop} onKeepGoing={onKeepGoing} />);

    await userEvent.click(screen.getByRole("button", { name: "계속 나누기" }));
    expect(onKeepGoing).toHaveBeenCalled();
    expect(onStop).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "멈추기" }));
    expect(onStop).toHaveBeenCalled();
  });

  it("결과를 닫을 때는 결과를 버린다는 문구로 묻는다", async () => {
    const onStop = jest.fn();
    const onKeepGoing = jest.fn();
    render(<OrganizeCancelSheet kind="result" onStop={onStop} onKeepGoing={onKeepGoing} />);

    expect(screen.getByRole("dialog", { name: "정리한 결과를 취소할까요?" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "결과 남겨 두기" }));
    expect(onKeepGoing).toHaveBeenCalled();
    expect(onStop).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    expect(onStop).toHaveBeenCalled();
  });
});
