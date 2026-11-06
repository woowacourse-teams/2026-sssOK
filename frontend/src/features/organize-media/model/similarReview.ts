import type { OrganizeApplyGroup } from "../api/types";
import { includedPhotos, type PersonReview, type ReviewPerson } from "./personReview";

/**
 * 비슷한 사진 나누기 검토. 모음 하나가 사람별 검토의 카드 하나와 같아서 `PersonReview` 를 그대로 쓴다.
 * 다른 점은 이름을 칩으로 고르지 않고 입력칸에 바로 쓰는 것이다. 그래서 `name` 에는 입력 중인 글자가
 * 그대로 들어 있고, 앞뒤 공백을 뺀 값이 폴더 이름이 된다.
 */

/** 폴더 이름이 될 값. 비었으면 이름을 안 붙인 모음이다. */
export const folderNameOf = (card: ReviewPerson) => card.name?.trim() || null;

/** 입력칸 글자를 모음 이름으로 둔다. 공백뿐이면 이름을 지운다. */
export const renameCard = (review: PersonReview, cardId: string, value: string): PersonReview => ({
  people: review.people.map((card) =>
    card.id === cardId ? { ...card, name: value.trim() ? value : null } : card,
  ),
});

/** 이 모음과 같은 이름을 먼저 쓴 다른 모음. 없으면 null 이다. 이름을 다 쓴 뒤 합칠지 물을 때 쓴다 (AI-S3b). */
export const similarOwnerOf = ({ people }: PersonReview, cardId: string) => {
  const self = people.find((card) => card.id === cardId);
  const folderName = self ? folderNameOf(self) : null;
  if (folderName === null) return null;
  return people.find((card) => card.id !== cardId && folderNameOf(card) === folderName) ?? null;
};

/** 방에 이미 같은 이름의 폴더가 있는 모음. 없으면 null 이다. */
export const similarConflictOf = ({ people }: PersonReview, folderNames: readonly string[]) =>
  people.find((card) => {
    const name = folderNameOf(card);
    return name !== null && folderNames.includes(name);
  }) ?? null;

/**
 * 반영 요청에 실을 그룹. 같은 이름을 쓴 모음은 한 폴더로 합친다 ("같은 이름을 쓰면 한 폴더로 합쳐져요").
 * 같은 폴더명이 두 번 가면 서버가 두 번째를 이름 충돌로 거절하므로, 처음 모음의 ID 로 한 번만 보낸다.
 * 뺀 사진은 싣지 않고, 남는 사진이 없으면 만들 폴더가 없어 뺀다.
 */
export const similarApplyGroupsOf = (people: readonly ReviewPerson[]): OrganizeApplyGroup[] => {
  const byName = new Map<string, OrganizeApplyGroup>();
  for (const card of people) {
    const folderName = folderNameOf(card);
    const mediaIds = includedPhotos(card).map((photo) => photo.mediaId);
    if (!folderName || mediaIds.length === 0) continue;

    const group = byName.get(folderName);
    if (group) {
      group.mediaIds = [...new Set([...group.mediaIds, ...mediaIds])];
    } else {
      byName.set(folderName, { groupId: card.groupIds[0], folderName, mediaIds });
    }
  }
  return [...byName.values()];
};

/**
 * 하단 안내와 버튼 문구 (AI-S2·S3).
 * - 이미 있는 폴더 이름을 씀: 이름을 바꿔야 한다고 알리고 버튼은 비활성 (AI-S3d)
 * - 아무 모음에도 이름이 없음: 안내만 있고 버튼은 비활성
 * - 일부만 이름을 붙임: 남는 모음 수를 알리고, 만들 폴더 수를 버튼에 적는다
 * - 모두 이름을 붙임: 안내 없이 폴더를 만든다
 */
export const similarFooterOf = (review: PersonReview, folderNames: readonly string[] = []) => {
  const conflict = similarConflictOf(review, folderNames);
  if (conflict) {
    return {
      hint: `‘${folderNameOf(conflict)}’ 폴더는 이름을 바꿔야 만들 수 있어요`,
      submit: "폴더 만들기",
      canSubmit: false,
    };
  }

  const folderCount = similarApplyGroupsOf(review.people).length;
  if (folderCount === 0) {
    return { hint: "이름을 붙이면 폴더가 생겨요", submit: "폴더 만들기", canSubmit: false };
  }

  const unnamed = review.people.filter((card) => folderNameOf(card) === null).length;
  return {
    hint: unnamed > 0 ? `이름을 안 붙인 ${unnamed}개는 폴더 없이 전체에 남아요` : null,
    submit: `${folderCount}개 폴더 만들기`,
    canSubmit: true,
  };
};
