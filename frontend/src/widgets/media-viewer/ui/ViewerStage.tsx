import { useRef } from "react";
import { HiChevronLeft, HiChevronRight } from "react-icons/hi2";
import type { GalleryItem } from "@/entities/media";
import { isVideoItem } from "../lib/isVideoItem";
import { ViewerMedia } from "./ViewerMedia";
import { Stage, TapZone, PreviousButton, NextButton } from "./ViewerStage.styles";

interface ViewerStageProps {
  current: GalleryItem;
  previous: GalleryItem | undefined;
  next: GalleryItem | undefined;
  onChange: (mediaId: number) => void;
}

export const ViewerStage = ({ current, previous, next, onChange }: ViewerStageProps) => {
  const touchStart = useRef<{ x: number; y: number } | null>(null);
  const movePrevious = (): void => {
    if (previous) onChange(previous.mediaId);
  };
  const moveNext = (): void => {
    if (next) onChange(next.mediaId);
  };
  return (
    <Stage
      onTouchStart={(event) => {
        const touch = event.touches[0];
        touchStart.current =
          event.touches.length === 1 ? { x: touch.clientX, y: touch.clientY } : null;
      }}
      onTouchMove={(event) => {
        if (event.touches.length > 1) touchStart.current = null;
      }}
      onTouchCancel={() => {
        touchStart.current = null;
      }}
      onTouchEnd={(event) => {
        const start = touchStart.current;
        touchStart.current = null;
        if (!start) return;

        const touch = event.changedTouches[0];
        const dx = touch.clientX - start.x;
        const dy = touch.clientY - start.y;
        if (Math.abs(dx) < 60 || Math.abs(dx) <= Math.abs(dy)) return;
        if (dx < 0 && next) moveNext();
        if (dx > 0 && previous) movePrevious();
      }}
    >
      <ViewerMedia key={`${current.mediaId}:${current.type}`} item={current} />
      {!isVideoItem(current) && (
        <>
          <TapZone
            data-testid="viewer-tap-previous"
            aria-hidden="true"
            $side="left"
            onClick={movePrevious}
          />
          <TapZone
            data-testid="viewer-tap-next"
            aria-hidden="true"
            $side="right"
            onClick={moveNext}
          />
        </>
      )}
      <PreviousButton
        type="button"
        aria-label="이전 사진"
        disabled={!previous}
        onClick={movePrevious}
      >
        <HiChevronLeft />
      </PreviousButton>
      <NextButton type="button" aria-label="다음 사진" disabled={!next} onClick={moveNext}>
        <HiChevronRight />
      </NextButton>
    </Stage>
  );
};
