/**
 * 작업들을 **동시에 `limit` 개까지만** 돌린다. 하나가 끝나면 대기 중인 다음 것이 출발한다.
 *
 * 업로드가 뭔지 모른다. `Promise` 를 돌려주는 함수면 무엇이든 받는다.
 *
 * `run` 이 **던지면 전체가 거절된다** (`Promise.all` 과 같다). 파일 하나가 깨져도 나머지가
 * 계속되어야 하는 쪽은 부르는 쪽이므로, `run` 은 실패를 예외가 아니라 **값으로** 돌려줘야 한다.
 */
export const runWithLimit = async <T, R>(
  items: T[],
  limit: number,
  run: (item: T) => Promise<R>,
): Promise<R[]> => {
  const results = new Array<R>(items.length);
  let nextIndex = 0;

  /**
   * 대기열에서 하나씩 집어 처리하다가 남은 게 없으면 끝난다.
   * 번호를 집고 올리는 사이에 `await` 가 없어서, 두 일꾼이 같은 번호를 집지 않는다.
   */
  const worker = async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;

      // 결과는 완료 순서가 아니라 **원래 자리**에 넣는다. 부르는 쪽이 입력과 짝지어야 한다.
      results[index] = await run(items[index]);
    }
  };

  const workerCount = Math.min(Math.max(limit, 1), items.length);

  await Promise.all(Array.from({ length: workerCount }, worker));

  return results;
};
