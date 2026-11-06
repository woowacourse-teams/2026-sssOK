import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { MediaItem } from "@/entities/media";
import type { OrganizeResult } from "../api/types";
import { SimilarReviewScreen } from "./SimilarReviewScreen";

const photo = (mediaId: number) =>
  ({ mediaId, fileName: `IMG_${mediaId}.jpg`, thumbnailUrl: `/t/${mediaId}.webp` }) as MediaItem;

const resultOf = (sizes: number[]): OrganizeResult => {
  let next = 1;
  return {
    jobId: "job",
    kind: "SIMILAR",
    resultVersion: 1,
    photoCount: sizes.reduce((sum, size) => sum + size, 0),
    removedCount: 0,
    groups: sizes.map((size, index) => ({
      groupId: `s${index + 1}`,
      photoCount: size,
      photos: Array.from({ length: size }, () => photo(next++)),
    })),
    unassigned: { groupId: null, photoCount: 0, photos: [] },
    errors: [],
  };
};

const renderScreen = (sizes = [9, 5, 2]) => {
  const onSubmit = jest.fn();
  render(
    <SimilarReviewScreen
      source={{ state: "ready", result: resultOf(sizes) }}
      folderNames={[]}
      onClose={jest.fn()}
      onRetry={jest.fn()}
      onSubmit={onSubmit}
    />,
  );
  return { onSubmit };
};

describe("SimilarReviewScreen", () => {
  it("묶은 모음 수와 모음마다 이름 입력칸을 보여 주고, 이름이 없으면 폴더 만들기를 막는다", () => {
    renderScreen();

    expect(screen.getByRole("heading", { name: "사진을 3개로 묶었어요." })).toBeInTheDocument();
    expect(screen.getAllByText("어떤 사진인가요?")).toHaveLength(3);
    expect(screen.getAllByPlaceholderText("폴더 이름 입력")).toHaveLength(3);
    expect(screen.getByText("+5")).toBeInTheDocument();
    expect(screen.getByText("이름을 붙이면 폴더가 생겨요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();
  });

  it("이름을 붙이면 남는 모음 안내와 함께 그 모음으로 폴더를 만든다", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderScreen();

    await user.type(screen.getAllByRole("textbox", { name: "폴더 이름" })[0], "잔디밭 풍경");

    expect(screen.getByText("이름을 안 붙인 2개는 폴더 없이 전체에 남아요")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "1개 폴더 만들기" }));
    expect(onSubmit).toHaveBeenCalledWith([
      { groupId: "s1", folderName: "잔디밭 풍경", mediaIds: [1, 2, 3, 4, 5, 6, 7, 8, 9] },
    ]);
  });

  it("모음을 눌러 사진을 빼면 '1장을 뺐어요' 가 보이고 모음 장수가 줄어든다", async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByRole("button", { name: "이름 없는 모음 사진 5장 확인" }));
    expect(screen.getByRole("heading", { name: "5장" })).toBeInTheDocument();
    expect(screen.getByText("이 모음에 안 맞는 사진을 눌러 빼 주세요.")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "사진 5" }));
    expect(screen.getByText("1장을 뺐어요")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "4장" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "완료" }));
    expect(screen.getByRole("button", { name: "이름 없는 모음 사진 4장 확인" })).toBeInTheDocument();
  });
});
