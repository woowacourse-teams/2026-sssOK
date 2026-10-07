import { HiCheck } from "react-icons/hi2";

import photo1 from "@/shared/assets/onboarding/photo-1.webp";
import photo2 from "@/shared/assets/onboarding/photo-2.webp";
import photo3 from "@/shared/assets/onboarding/photo-3.webp";
import photo4 from "@/shared/assets/onboarding/photo-4.webp";
import photo5 from "@/shared/assets/onboarding/photo-5.webp";
import photo6 from "@/shared/assets/onboarding/photo-6.webp";

import { progressBetween, useSceneClock } from "../model/useSceneClock";
import { Album, Photo, PhotoCheck, PhotoOwner } from "./OnboardingPreview.styles";
import { PreviewSelectionBar, PreviewToast } from "./OnboardingPreviewBars";
import { BarSlot, SceneTopSpace } from "./OnboardingPreviewBars.styles";

/**
 * 미리보기 갤러리에 쓰는 사진. 정사각형으로 잘라 240px 로 줄이고 메타데이터를 지웠다.
 * 사진이 늦게 뜨는 동안에는 사진과 비슷한 색을 바탕으로 깐다.
 * 앞의 네 장이 "내가" 올리는 사진이고, 친구들이 올린 나머지 두 장을 골라 받는다.
 */
const MY_PHOTOS = [
  { id: 3, src: photo3, color: "#B9C4CF", owner: "나" },
  { id: 5, src: photo5, color: "#D9D6D2", owner: "나" },
  { id: 2, src: photo2, color: "#CDB59A", owner: "나" },
  { id: 6, src: photo6, color: "#8E9196", owner: "나" },
];
const FRIEND_PHOTOS = [
  { id: 1, src: photo1, color: "#6FC3C9", owner: "민지" },
  { id: 4, src: photo4, color: "#C9B49A", owner: "도윤" },
];
const ALL_PHOTOS = [...MY_PHOTOS, ...FRIEND_PHOTOS];

/**
 * 업로드 장면의 시점(ms). 친구 사진이 있는 방에 내 사진 네 장을 올린다.
 * 한 장이 다 올라갈 때마다 그 사진이 갤러리 앞에 붙고, 다 올라가면 결과 토스트가 뜬다.
 */
const UPLOAD = { from: 100, to: 600, toastIn: 700 };

/** 다운로드 장면의 시점(ms). 친구 사진 두 장을 골라 다운로드를 누른다. */
const DOWNLOAD = { picks: [300, 700], press: 1250, release: 1450, toastIn: 1550 };
const DOWNLOAD_COUNT = DOWNLOAD.picks.length;

interface SceneProps {
  /** 지금 돌고 있는 장면인지. 아니면 마지막 모습에 멈춰 있다 */
  running: boolean;
}

const PhotoTile = ({ photo, picked }: { photo: (typeof ALL_PHOTOS)[number]; picked?: boolean }) => (
  <Photo $color={photo.color}>
    <img src={photo.src} alt="" />
    {picked !== undefined && <PhotoCheck $picked={picked}>{picked && <HiCheck />}</PhotoCheck>}
    <PhotoOwner $mine={photo.owner === "나"}>{photo.owner}</PhotoOwner>
  </Photo>
);

/** 다 올라간 사진부터 갤러리 앞에 붙고, 끝나면 결과 토스트가 뜬다 */
export const UploadScene = ({ running }: SceneProps) => {
  const elapsed = useSceneClock(running);
  const percent = progressBetween(elapsed, UPLOAD.from, UPLOAD.to);
  const completedCount = Math.floor((percent / 100) * MY_PHOTOS.length);
  // 뒤의 사진부터 올라가 앞에 붙는다. 다 올라가면 골라 받기 장면과 같은 순서가 된다
  const photos = [...MY_PHOTOS.slice(MY_PHOTOS.length - completedCount), ...FRIEND_PHOTOS];

  return (
    <>
      <SceneTopSpace />
      <Album>
        {photos.map((photo) => (
          <PhotoTile key={photo.id} photo={photo} />
        ))}
      </Album>
      <BarSlot>
        {elapsed >= UPLOAD.toastIn && (
          <PreviewToast message={`사진 ${MY_PHOTOS.length}장이 업로드됐어요`} />
        )}
      </BarSlot>
    </>
  );
};

/** 사진을 고르면 선택 바가 뜨고, 다운로드를 누르면 결과 토스트가 뜬다 */
export const DownloadScene = ({ running }: SceneProps) => {
  const elapsed = useSceneClock(running);
  const pickedCount = DOWNLOAD.picks.filter((at) => elapsed >= at).length;

  return (
    <>
      <SceneTopSpace />
      <Album>
        {ALL_PHOTOS.map((photo) => {
          const pickOrder = FRIEND_PHOTOS.indexOf(photo);
          return (
            <PhotoTile
              key={photo.id}
              photo={photo}
              picked={pickOrder !== -1 && pickOrder < pickedCount}
            />
          );
        })}
      </Album>
      <BarSlot>
        {pickedCount > 0 && elapsed < DOWNLOAD.release && (
          <PreviewSelectionBar count={pickedCount} pressed={elapsed >= DOWNLOAD.press} />
        )}
        {elapsed >= DOWNLOAD.toastIn && (
          <PreviewToast message={`사진 ${DOWNLOAD_COUNT}장이 다운로드됐어요`} />
        )}
      </BarSlot>
    </>
  );
};
