import { QueryErrorResetBoundary } from "@tanstack/react-query";
import { Suspense } from "react";
import { ErrorBoundary } from "react-error-boundary";

import { PageSpinner } from "@/shared/ui/spinner";
import { RoomListContent } from "./RoomListContent";
import { RoomListError } from "./RoomListError";
import { RoomListSectionHeading } from "./RoomListSectionHeading";

export const RoomListSection = () => {
  return (
    <>
      <RoomListSectionHeading />
      <QueryErrorResetBoundary>
        {({ reset }) => (
          <ErrorBoundary onReset={reset} FallbackComponent={RoomListError}>
            <Suspense fallback={<PageSpinner label="방 목록을 불러오는 중이에요" />}>
              <RoomListContent />
            </Suspense>
          </ErrorBoundary>
        )}
      </QueryErrorResetBoundary>
    </>
  );
};
