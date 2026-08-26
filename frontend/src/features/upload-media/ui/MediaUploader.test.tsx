import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { MOCK_R2_BASE_URL } from "@/mocks/handlers/upload";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { MAX_IMAGE_BYTES } from "../lib/mediaFileRules";
import { MediaUploader } from "./MediaUploader";
import { UPLOAD_BUTTON_LABEL } from "./UploadButton";

// 백오프를 실제로 기다리면 실패 한 건마다 2초씩 잡아먹는다. 기다림 자체는 따로 확인한다.
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  RETRY_BACKOFF_MS: [0, 0],
}));

const TOKEN = "mock-token-10234";

/** 고른 파일이 실제로 올라가므로, 발급이 403 으로 막히지 않게 먼저 방에 들어간다. */
const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

const renderUploader = () => render(<MediaUploader roomId={MOCK_ROOM_ID} token={TOKEN} />);

/**
 * 스토리지 키는 파일명을 버리지만 확장자는 남긴다. 그래서 확장자로 "이 파일만 깨진다" 를 만든다.
 *
 * 응답을 돌려주지 않으면 기본 핸들러로 넘어가 정상 업로드가 된다 — 가로채기만 하고
 * 목의 기록은 그대로 두려는 것이다. 목이 바이트를 기록해야 완료 등록이 통과한다.
 */
const failUploadsOf = (extension: string) => {
  const keys: string[] = [];

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, ({ request }) => {
      const key = new URL(request.url).pathname;

      keys.push(key);

      if (!key.endsWith(extension)) return undefined;

      return new HttpResponse(null, { status: 500 });
    }),
  );

  return keys;
};

/** 다음 PUT 들을 붙잡아 둔다. 목이 즉시 답하면 "올라가는 중" 인 화면을 볼 수가 없다. */
const holdUploads = () => {
  let release = () => {};
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, async () => {
      await held;

      return new HttpResponse(null, { status: 200 });
    }),
  );

  return () => release();
};

const failureHeading = () => screen.queryByRole("heading", { name: /못올렸어요/ });

const settled = () =>
  waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());

const fileOf = (name: string, size = 1024, type = "image/jpeg") => {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });

  return file;
};

const getUploadButton = () => screen.getByRole("button", { name: UPLOAD_BUTTON_LABEL });

/** 선택기는 접근성 트리에 없다. 버튼이 열어주는 자리라 DOM 에서 직접 집는다. */
const getFileInput = () => {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');

  if (input === null) throw new Error("파일 입력을 찾지 못했다");

  return input;
};

describe("MediaUploader", () => {
  beforeEach(enterRoom);

  it("사진과 영상을 여러 장 고를 수 있는 선택기를 둔다", () => {
    renderUploader();

    const input = getFileInput();

    expect(input).toHaveAttribute("accept", "image/*,video/*");
    expect(input).toHaveAttribute("multiple");
  });

  it("업로드 버튼을 누르면 기기 기본 사진 선택기가 열린다", async () => {
    const user = userEvent.setup();

    renderUploader();

    const openPicker = jest.spyOn(getFileInput(), "click");
    await user.click(getUploadButton());

    expect(openPicker).toHaveBeenCalled();
  });

  /**
   * 라이브 영역이 알림과 같은 순간에 생기면 스크린리더가 변화로 잡지 못한다.
   * 그래서 "없다" 가 아니라 "있는데 비어 있다" 여야 한다.
   */
  it("알림 자리는 고르기 전부터 있고, 비어 있다", () => {
    renderUploader();

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("여러 장을 고르면 선택한 장수를 알린다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("a.jpg"), fileOf("b.png"), fileOf("c.mov")]);

    expect(screen.getByRole("status")).toHaveTextContent("3장을 선택했어요");
  });

  /**
   * 브라우저는 선택기가 직전과 같은 값을 돌려주면 change 를 보내지 않는다.
   * 그래서 고른 직후 입력을 비우는지를 본다 — jsdom 은 그 생략을 흉내내지 않아서,
   * 두 번 고르는 것만으로는 이 회귀를 잡지 못한다.
   */
  it("고른 직후 입력을 비워, 같은 사진을 두 번 골라도 선택 이벤트가 다시 발생한다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("a.jpg"), fileOf("b.jpg")]);

    expect(screen.getByRole("status")).toHaveTextContent("2장을 선택했어요");
    expect(getFileInput()).toHaveValue("");
  });

  it("알림을 닫으면 사라진다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("a.jpg")]);
    await user.click(screen.getByRole("button", { name: "알림 닫기" }));

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("걸러진 파일이 있으면 몇 장이 왜 제외됐는지 알린다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [
      fileOf("a.jpg"),
      // 사파리는 파일명이 .HEIC 인데 image/jpeg 를 실어 보내기도 한다
      fileOf("IMG_0001.HEIC"),
      fileOf("big.png", MAX_IMAGE_BYTES + 1, "image/png"),
    ]);

    const notice = screen.getByRole("status");

    expect(notice).toHaveTextContent("1장을 선택했어요");
    expect(notice).toHaveTextContent("2장은 올릴 수 없어요");
    expect(notice).toHaveTextContent("이미지와 영상만 올릴 수 있어요 (1장)");
    expect(notice).toHaveTextContent("사진은 10MB까지 올릴 수 있어요 (1장)");
  });

  it("같은 사유로 걸러진 파일은 한 줄로 묶어 장수만 보여준다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("IMG_0001.HEIC"), fileOf("IMG_0002.HEIC")]);

    expect(screen.getByRole("status")).toHaveTextContent("이미지와 영상만 올릴 수 있어요 (2장)");
    expect(screen.queryByText(/IMG_0001/)).not.toBeInTheDocument();
  });

  it("accept 를 통과한 파일도 확장자로 다시 거른다", async () => {
    // accept 는 선택기의 권고일 뿐이라, 데스크톱에서 "모든 파일" 로 바꾸면 그대로 넘어온다
    const user = userEvent.setup({ applyAccept: false });

    renderUploader();
    await user.upload(getFileInput(), [fileOf("note.txt", 10, "text/plain")]);

    const notice = screen.getByRole("status");

    expect(notice).toHaveTextContent("1장은 올릴 수 없어요");
    expect(notice).not.toHaveTextContent("선택했어요");
  });

  it("전부 올라가면 실패 모달이 뜨지 않는다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("첫째.jpg"), fileOf("둘째.jpg")]);
    await settled();

    expect(failureHeading()).not.toBeInTheDocument();
  });

  it("깨진 파일이 있으면 그 장수로 실패 모달이 뜬다", async () => {
    const user = userEvent.setup();

    failUploadsOf(".png");
    renderUploader();
    await user.upload(getFileInput(), [fileOf("첫째.jpg"), fileOf("둘째.png", 1024, "image/png")]);

    // 고른 건 두 장인데 깨진 건 한 장이다. 장수는 실패분만 센다.
    expect(
      await screen.findByRole("heading", { name: "앗, 1장을 못올렸어요" }),
    ).toBeInTheDocument();
  });

  it("재시도를 누르면 깨진 파일만 다시 올라간다", async () => {
    const user = userEvent.setup();

    const keys = failUploadsOf(".png");

    renderUploader();
    await user.upload(getFileInput(), [fileOf("첫째.jpg"), fileOf("둘째.png", 1024, "image/png")]);
    await screen.findByRole("heading", { name: "앗, 1장을 못올렸어요" });

    // 첫 판의 기록은 지우고, 재시도가 새로 쏘는 것만 본다.
    keys.length = 0;

    await user.click(screen.getByRole("button", { name: "재시도" }));
    await screen.findByRole("heading", { name: "앗, 1장을 못올렸어요" });

    // 멀쩡히 올라간 첫째.jpg 를 또 올리면 갤러리에 같은 사진이 두 장 생긴다.
    expect(keys.length).toBeGreaterThan(0);
    expect(keys.every((key) => key.endsWith(".png"))).toBe(true);
  });

  it("재시도하는 동안에는 모달 대신 진행 바가 보인다", async () => {
    const user = userEvent.setup();

    failUploadsOf(".png");
    renderUploader();
    await user.upload(getFileInput(), [fileOf("둘째.png", 1024, "image/png")]);
    await screen.findByRole("heading", { name: "앗, 1장을 못올렸어요" });

    const release = holdUploads();

    await user.click(screen.getByRole("button", { name: "재시도" }));

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(failureHeading()).not.toBeInTheDocument();

    release();
    await settled();
  });

  it("모달을 닫으면 사라진다", async () => {
    const user = userEvent.setup();

    failUploadsOf(".png");
    renderUploader();
    await user.upload(getFileInput(), [fileOf("둘째.png", 1024, "image/png")]);
    await screen.findByRole("heading", { name: "앗, 1장을 못올렸어요" });

    await user.click(screen.getByText("닫기"));

    expect(failureHeading()).not.toBeInTheDocument();
  });
});
