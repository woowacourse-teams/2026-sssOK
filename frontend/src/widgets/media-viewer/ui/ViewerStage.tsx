import { HiChevronLeft, HiChevronRight } from "react-icons/hi2";
import type { GalleryItem } from "@/entities/media";
import { isVideoItem } from "../lib/isVideoItem";
import { useViewerCarousel } from "../model/useViewerCarousel";
import { ViewerMedia } from "./ViewerMedia";
import { Stage, Viewport, Slide, TapZone, PreviousButton, NextButton } from "./ViewerStage.styles";

interface ViewerStageProps {
  current: GalleryItem;
  previous: GalleryItem | undefined;
  next: GalleryItem | undefined;
  onChange: (mediaId: number) => void;
}

export const ViewerStage = ({ current, previous, next, onChange }: ViewerStageProps) => {
  const items = [previous, current, next].filter((item): item is GalleryItem => item !== undefined);
  const { ref, events } = useViewerCarousel(
    items.map((item) => item.mediaId),
    current.mediaId,
    onChange,
  );
  const movePrevious = (): void => {
    if (previous) onChange(previous.mediaId);
  };
  const moveNext = (): void => {
    if (next) onChange(next.mediaId);
  };

  return (
    <Stage>
      <Viewport ref={ref} {...events}>
        {items.map((item) => {
          const active = item.mediaId === current.mediaId;
          return (
            <Slide key={item.mediaId} aria-hidden={!active}>
              <ViewerMedia key={item.type} item={item} active={active} />
              {active && !isVideoItem(item) && (
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
            </Slide>
          );
        })}
      </Viewport>
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
