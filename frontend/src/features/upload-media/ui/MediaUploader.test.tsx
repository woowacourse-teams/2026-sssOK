import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { MAX_IMAGE_BYTES } from "../lib/mediaFileRules";
import { MediaUploader } from "./MediaUploader";
import { UPLOAD_BUTTON_LABEL } from "./UploadButton";

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
  it("사진과 영상을 여러 장 고를 수 있는 선택기를 둔다", () => {
    render(<MediaUploader />);

    const input = getFileInput();

    expect(input).toHaveAttribute("accept", "image/*,video/*");
    expect(input).toHaveAttribute("multiple");
  });

  it("업로드 버튼을 누르면 기기 기본 사진 선택기가 열린다", async () => {
    const user = userEvent.setup();

    render(<MediaUploader />);

    const openPicker = jest.spyOn(getFileInput(), "click");
    await user.click(getUploadButton());

    expect(openPicker).toHaveBeenCalled();
  });

  /**
   * 라이브 영역이 알림과 같은 순간에 생기면 스크린리더가 변화로 잡지 못한다.
   * 그래서 "없다" 가 아니라 "있는데 비어 있다" 여야 한다.
   */
  it("알림 자리는 고르기 전부터 있고, 비어 있다", () => {
    render(<MediaUploader />);

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("여러 장을 고르면 선택한 장수를 알린다", async () => {
    const user = userEvent.setup();

    render(<MediaUploader />);
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

    render(<MediaUploader />);
    await user.upload(getFileInput(), [fileOf("a.jpg"), fileOf("b.jpg")]);

    expect(screen.getByRole("status")).toHaveTextContent("2장을 선택했어요");
    expect(getFileInput()).toHaveValue("");
  });

  it("알림을 닫으면 사라진다", async () => {
    const user = userEvent.setup();

    render(<MediaUploader />);
    await user.upload(getFileInput(), [fileOf("a.jpg")]);
    await user.click(screen.getByRole("button", { name: "알림 닫기" }));

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("걸러진 파일이 있으면 몇 장이 왜 제외됐는지 알린다", async () => {
    const user = userEvent.setup();

    render(<MediaUploader />);
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

    render(<MediaUploader />);
    await user.upload(getFileInput(), [fileOf("IMG_0001.HEIC"), fileOf("IMG_0002.HEIC")]);

    expect(screen.getByRole("status")).toHaveTextContent("이미지와 영상만 올릴 수 있어요 (2장)");
    expect(screen.queryByText(/IMG_0001/)).not.toBeInTheDocument();
  });

  it("accept 를 통과한 파일도 확장자로 다시 거른다", async () => {
    // accept 는 선택기의 권고일 뿐이라, 데스크톱에서 "모든 파일" 로 바꾸면 그대로 넘어온다
    const user = userEvent.setup({ applyAccept: false });

    render(<MediaUploader />);
    await user.upload(getFileInput(), [fileOf("note.txt", 10, "text/plain")]);

    const notice = screen.getByRole("status");

    expect(notice).toHaveTextContent("1장은 올릴 수 없어요");
    expect(notice).not.toHaveTextContent("선택했어요");
  });
});
