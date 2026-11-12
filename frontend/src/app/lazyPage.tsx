import { lazy, Suspense, type ComponentType } from "react";

import { PageSpinner } from "@/shared/ui/spinner";

const pageLoaders = new Set<() => Promise<unknown>>();

/**
 * 나눠 둔 페이지 코드를 모두 미리 받아 둔다.
 * 테스트는 화면을 열 때마다 코드를 새로 변환하느라 느린 CI 에서 대기 시간을 넘기므로, 파일 시작 전에 한 번 받아 둔다.
 */
export const preloadLazyPages = () => Promise.allSettled(Array.from(pageLoaders, (load) => load()));

/**
 * 페이지를 처음 열 때 코드를 받아 오게 나눈다.
 * 받는 동안은 스피너를 띄우고, 받지 못하면 라우트의 errorElement 로 넘어간다.
 */
export const lazyPage = <TModule,>(
  load: () => Promise<TModule>,
  pick: (module: TModule) => ComponentType,
) => {
  pageLoaders.add(load);
  const Page = lazy(() => load().then((module) => ({ default: pick(module) })));

  return (
    <Suspense fallback={<PageSpinner label="화면을 불러오는 중이에요." />}>
      <Page />
    </Suspense>
  );
};
