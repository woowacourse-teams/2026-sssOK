import type { SliderMark } from "@/shared/ui/slider";

const DAYS_IN_WEEK = 7;
// 모바일 폭에서 라벨이 겹치지 않는 눈금 수. 양 끝을 빼고 이만큼만 둔다.
const MAX_INNER_MARKS = 5;

const toLabel = (days: number) =>
  days % DAYS_IN_WEEK === 0 ? `${days / DAYS_IN_WEEK}주` : `${days}일`;

/**
 * 만료 기간 슬라이더 눈금. 양 끝과 그 사이의 주 단위 값만 보여준다.
 * 최댓값이 늘어 주 단위 눈금이 많아지면 2주, 3주 … 간격으로 넓힌다.
 */
export const createExpiryDayMarks = (min: number, max: number): SliderMark[] => {
  const interval = DAYS_IN_WEEK * Math.max(1, Math.ceil(max / DAYS_IN_WEEK / MAX_INNER_MARKS));
  const values = [min];

  for (let days = interval; days < max; days += interval) {
    // 끝값과 너무 붙은 눈금은 라벨이 겹치므로 뺀다.
    if (days > min && max - days >= interval / 2) {
      values.push(days);
    }
  }

  values.push(max);

  return [...new Set(values)].map((days) => ({ value: days, label: toLabel(days) }));
};
