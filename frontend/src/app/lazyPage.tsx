import { lazy, Suspense, type ComponentType } from "react";

import { PageSpinner } from "@/shared/ui/spinner";

/**
 * 페이지를 처음 열 때 코드를 받아 오게 나눈다.
 * 받는 동안은 스피너를 띄우고, 받지 못하면 라우트의 errorElement 로 넘어간다.
 */
export const lazyPage = <TModule,>(
  load: () => Promise<TModule>,
  pick: (module: TModule) => ComponentType,
) => {
  const Page = lazy(() => load().then((module) => ({ default: pick(module) })));

  return (
    <Suspense fallback={<PageSpinner label="화면을 불러오는 중이에요." />}>
      <Page />
    </Suspense>
  );
};
