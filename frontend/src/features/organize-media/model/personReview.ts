import type { MediaItem } from "@/entities/media";
import type { OrganizeApplyGroup, OrganizeGroup } from "../api/types";

/**
 * 사람별 나누기 검토 화면이 들고 있는 한 사람(카드).
 *
 * 고른 이름과 뺀 사진은 반영하기 전까지 화면에서만 들고 있다. 서버 결과는 건드리지 않는다.
 * 같은 이름으로 두 카드를 합치면 `groupIds` 에 원래 그룹을 모두 남겨 반영 때 함께 넘긴다.
 */
export interface ReviewPerson {
  /** 카드 키. 처음 그룹의 groupId 다. 합쳐도 바뀌지 않는다. */
  id: string;
  groupIds: string[];
  photos: MediaItem[];
  /** 사진 확인 화면에서 체크를 푼 사진. 다시 누르면 되돌릴 수 있어 목록에서 지우지 않는다. */
  excludedIds: number[];
  name: string | null;
}

export interface PersonReview {
  people: ReviewPerson[];
}

export const createPersonReview = (groups: OrganizeGroup[]): PersonReview => ({
  people: groups
    .filter((group): group is OrganizeGroup & { groupId: string } => group.groupId !== null)
    .map((group) => ({
      id: group.groupId,
      groupIds: [group.groupId],
      photos: group.photos,
      excludedIds: [],
      name: null,
    })),
});

/** 뺀 사진을 제외한, 폴더에 들어갈 사진. */
export const includedPhotos = (person: ReviewPerson) =>
  person.photos.filter((photo) => !person.excludedIds.includes(photo.mediaId));

export const isEmptied = (person: ReviewPerson) =>
  person.photos.length > 0 && includedPhotos(person).length === 0;

/** 이 이름을 이미 고른 다른 카드. 없으면 null 이다. */
export const ownerOfName = (review: PersonReview, personId: string, name: string) =>
  review.people.find(
    (person) => person.id !== personId && !isEmptied(person) && person.name === name,
  ) ?? null;

const update = (
  review: PersonReview,
  personId: string,
  change: (person: ReviewPerson) => ReviewPerson,
): PersonReview => ({
  people: review.people.map((person) => (person.id === personId ? change(person) : person)),
});

/**
 * 카드에 이름을 붙인다. 이미 고른 이름을 다시 고르면 선택이 풀린다.
 * 다른 카드가 쓰는 이름인지는 부르는 쪽이 `ownerOfName` 으로 먼저 묻는다.
 */
export const toggleName = (review: PersonReview, personId: string, name: string) =>
  update(review, personId, (person) => ({
    ...person,
    name: person.name === name ? null : name,
  }));

/**
 * `sourceId` 카드를 `targetId` 카드에 합친다. 사진은 겹치지 않게 이어 붙이고,
 * 이름은 이미 그 이름을 가진 `targetId` 쪽을 따른다.
 */
export const mergePeople = (review: PersonReview, targetId: string, sourceId: string) => {
  const source = review.people.find((person) => person.id === sourceId);
  if (!source || targetId === sourceId) return review;

  return {
    people: review.people
      .filter((person) => person.id !== sourceId)
      .map((person) => {
        if (person.id !== targetId) return person;
        const ids = new Set(person.photos.map((photo) => photo.mediaId));
        return {
          ...person,
          groupIds: [...person.groupIds, ...source.groupIds],
          photos: [...person.photos, ...source.photos.filter((photo) => !ids.has(photo.mediaId))],
          excludedIds: [...new Set([...person.excludedIds, ...source.excludedIds])],
        };
      }),
  };
};

/** 사진을 빼거나, 뺀 사진을 다시 넣는다. */
export const togglePhoto = (review: PersonReview, personId: string, mediaId: number) => {
  const before = review.people.find((person) => person.id === personId);
  const next = update(review, personId, (person) => ({
    ...person,
    excludedIds: person.excludedIds.includes(mediaId)
      ? person.excludedIds.filter((id) => id !== mediaId)
      : [...person.excludedIds, mediaId],
  }));
  const after = next.people.find((person) => person.id === personId);
  if (!before || !after || !isEmptied(before) || isEmptied(after)) return next;

  const name = after.name?.trim();
  const isTaken =
    !!name &&
    next.people.some(
      (person) => person.id !== personId && !isEmptied(person) && person.name?.trim() === name,
    );
  return isTaken ? update(next, personId, (person) => ({ ...person, name: null })) : next;
};

/** 방에 이미 같은 이름의 폴더가 있어 이름을 바꿔야 하는 카드. 없으면 null 이다. */
export const conflictOf = ({ people }: PersonReview, folderNames: readonly string[]) =>
  people.find(
    (person) => person.name !== null && !isEmptied(person) && folderNames.includes(person.name),
  ) ?? null;

/**
 * 하단 안내와 버튼 문구.
 * - 이미 있는 폴더 이름을 고름: 이름을 바꾸라고 알리고 버튼은 비활성 (AI-3d)
 * - 아무도 안 고름: 안내만 있고 버튼은 비활성
 * - 일부만 고름: 남는 사람 수를 알리고, 고른 사람 수만큼 폴더를 만든다
 * - 모두 고름: 안내 없이 폴더를 만든다
 */
export const footerOf = (review: PersonReview, folderNames: readonly string[] = []) => {
  const conflict = conflictOf(review, folderNames);
  if (conflict) {
    return {
      hint: `‘${conflict.name}’ 폴더는 이름을 바꿔야 만들 수 있어요`,
      submit: "폴더 만들기",
      canSubmit: false,
    };
  }

  const people = review.people.filter((person) => !isEmptied(person));
  const named = people.filter((person) => person.name !== null).length;
  const unnamed = people.length - named;

  if (named === 0) {
    return { hint: "이름을 고르면 폴더가 생겨요", submit: "폴더 만들기", canSubmit: false };
  }
  return {
    hint: unnamed > 0 ? `이름을 안 고른 ${unnamed}명은 폴더 없이 전체에 남아요` : null,
    submit: `${named}명의 폴더 만들기`,
    canSubmit: true,
  };
};

/**
 * 반영 요청에 실을 그룹. 이름을 고른 카드 하나가 폴더 하나다.
 *
 * - 뺀 사진은 싣지 않는다. 남는 사진이 없는 카드는 만들 폴더가 없어 뺀다.
 * - 같은 이름으로 합친 카드는 처음 그룹의 ID 로 한 번만 보낸다. 같은 폴더명이 두 번 가면 서버가
 *   두 번째를 이름 충돌로 거절해 전체가 실패하기 때문이다.
 * - '나' 칩의 값은 내 닉네임이라, 고른 이름이 그대로 폴더명이 된다.
 */
export const applyGroupsOf = (people: readonly ReviewPerson[]): OrganizeApplyGroup[] =>
  people.flatMap((person) => {
    const folderName = person.name?.trim();
    const mediaIds = includedPhotos(person).map((photo) => photo.mediaId);
    return folderName && mediaIds.length > 0
      ? [{ groupId: person.groupIds[0], folderName, mediaIds }]
      : [];
  });
