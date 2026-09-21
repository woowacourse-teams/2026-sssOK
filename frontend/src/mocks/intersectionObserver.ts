/**
 * jsdom 에는 IntersectionObserver 가 없다. 무한 스크롤을 쓰는 화면은 그냥 렌더링만 해도
 * `new IntersectionObserver` 에서 터지므로, 테스트 환경 전체에 가짜를 깔아 둔다.
 *
 * 가짜는 아무 것도 관찰하지 않는다 — 실제로 "화면에 들어왔다" 고 알리는 것은
 * `triggerIntersection` 을 부른 테스트뿐이다. 이렇게 해야 다음 페이지를 언제 부르는지
 * 테스트가 정하고, 무관한 테스트가 뜻밖에 요청을 하나 더 내보내지 않는다.
 */
interface Registered {
  callback: IntersectionObserverCallback;
  targets: Set<Element>;
}

const registered: Registered[] = [];

class MockIntersectionObserver {
  private entry: Registered;

  constructor(callback: IntersectionObserverCallback) {
    this.entry = { callback, targets: new Set() };
    registered.push(this.entry);
  }

  observe(target: Element) {
    this.entry.targets.add(target);
  }

  unobserve(target: Element) {
    this.entry.targets.delete(target);
  }

  disconnect() {
    this.entry.targets.clear();

    const index = registered.indexOf(this.entry);

    if (index !== -1) registered.splice(index, 1);
  }

  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }

  readonly root = null;
  readonly rootMargin = "";
  readonly thresholds: readonly number[] = [];
}

export const installIntersectionObserverMock = () => {
  Object.defineProperty(globalThis, "IntersectionObserver", {
    writable: true,
    configurable: true,
    value: MockIntersectionObserver,
  });
};

/** 관찰 중인 대상이 모두 화면에 들어왔다고 알린다. */
export const triggerIntersection = () => {
  // 부르는 쪽이 disconnect 하며 목록을 줄일 수 있어 복사본을 돈다.
  [...registered].forEach(({ callback, targets }) => {
    const entries = [...targets].map(
      (target) => ({ target, isIntersecting: true }) as IntersectionObserverEntry,
    );

    if (entries.length > 0) callback(entries, {} as IntersectionObserver);
  });
};

export const resetIntersectionObservers = () => {
  registered.length = 0;
};
