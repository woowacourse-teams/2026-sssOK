import { SHARE_BATCH_SIZE } from "../config";

/**
 * 공유 시트로 넘길 묶음을 나눈다.
 *
 * `share()` 는 사용자 탭 한 번에 한 번만 열린다. 묶음을 코드에서 이어 부를 수 없어서,
 * 버튼을 누를 때마다 앞 묶음 하나씩 넘긴다. 마지막 묶음만 기준보다 작을 수 있다.
 */
export const toShareBatches = <T>(items: T[], size = SHARE_BATCH_SIZE): T[][] => {
  const batches: T[][] = [];

  for (let start = 0; start < items.length; start += size) {
    batches.push(items.slice(start, start + size));
  }

  return batches;
};

/** 몇 번 나눠 넘기게 되는지. 저장 방식 시트가 미리 알려주는 데 쓴다. */
export const shareBatchCountOf = (count: number, size = SHARE_BATCH_SIZE) =>
  Math.ceil(count / size);
