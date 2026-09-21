import { useMemo, useState } from "react";

import { collectFrontendVersions, type AdminFeedback } from "@/entities/feedback";

/**
 * 프론트 버전으로 의견을 거른다.
 *
 * 서버에 버전 필터 파라미터가 없어서 **이미 불러온 페이지 안에서만** 거른다. 다음 페이지를
 * 불러오면 칩이 늘어날 수 있고, 거른 결과도 그때 함께 늘어난다.
 */
export const useVersionFilter = (feedbacks: AdminFeedback[]) => {
  const [selectedVersion, setSelectedVersion] = useState<string | null>(null);

  const versions = useMemo(() => collectFrontendVersions(feedbacks), [feedbacks]);

  const visibleFeedbacks = useMemo(
    () =>
      selectedVersion === null
        ? feedbacks
        : feedbacks.filter(({ frontendVersion }) => frontendVersion === selectedVersion),
    [feedbacks, selectedVersion],
  );

  return {
    versions,
    selectedVersion,
    visibleFeedbacks,
    // 같은 칩을 다시 누르면 선택을 푼다 — 전체로 돌아갈 다른 버튼이 디자인에 없다.
    toggleVersion: (version: string) =>
      setSelectedVersion((current) => (current === version ? null : version)),
  };
};
