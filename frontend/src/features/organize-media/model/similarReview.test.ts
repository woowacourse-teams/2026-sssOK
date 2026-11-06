import type { MediaItem } from "@/entities/media";
import { createPersonReview, togglePhoto } from "./personReview";
import { renameCard, similarApplyGroupsOf, similarFooterOf } from "./similarReview";

const photo = (mediaId: number) => ({ mediaId, thumbnailUrl: `/t/${mediaId}.webp` }) as MediaItem;

const reviewOf = (sizes: number[]) => {
  let next = 1;
  return createPersonReview(
    sizes.map((size, index) => ({
      groupId: `s${index + 1}`,
      photoCount: size,
      photos: Array.from({ length: size }, () => photo(next++)),
    })),
  );
};

describe("similarReview", () => {
  it("공백뿐인 이름은 지우고, 입력 중인 글자는 그대로 둔다", () => {
    const review = reviewOf([2]);

    expect(renameCard(review, "s1", "  ").people[0].name).toBeNull();
    expect(renameCard(review, "s1", "잔디 ").people[0].name).toBe("잔디 ");
  });

  it("아무 모음에도 이름이 없으면 버튼이 비활성이다", () => {
    expect(similarFooterOf(reviewOf([2, 3]))).toEqual({
      hint: "이름을 붙이면 폴더가 생겨요",
      submit: "폴더 만들기",
      canSubmit: false,
    });
  });

  it("일부만 이름을 붙이면 남는 모음 수를 알리고 만들 폴더 수를 적는다", () => {
    const review = renameCard(reviewOf([2, 3, 1]), "s1", "잔디밭 풍경");

    expect(similarFooterOf(review)).toEqual({
      hint: "이름을 안 붙인 2개는 폴더 없이 전체에 남아요",
      submit: "1개 폴더 만들기",
      canSubmit: true,
    });
  });

  it("같은 이름을 쓴 모음은 한 폴더로 합치고 뺀 사진은 싣지 않는다", () => {
    let review = reviewOf([2, 2, 1]);
    review = renameCard(review, "s1", "바다");
    review = renameCard(review, "s2", " 바다 ");
    review = togglePhoto(review, "s2", 3);

    expect(similarApplyGroupsOf(review.people)).toEqual([
      { groupId: "s1", folderName: "바다", mediaIds: [1, 2, 4] },
    ]);
    expect(similarFooterOf(review).submit).toBe("1개 폴더 만들기");
  });

  it("방에 같은 이름 폴더가 있으면 이름을 바꾸라고 알리고 막는다", () => {
    const review = renameCard(reviewOf([2]), "s1", "여행");

    expect(similarFooterOf(review, ["여행"])).toMatchObject({
      hint: "‘여행’ 폴더는 이름을 바꿔야 만들 수 있어요",
      canSubmit: false,
    });
  });
});
