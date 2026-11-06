import { useRef } from "react";
import { HiCheck } from "react-icons/hi2";

import { useToast } from "@/shared/ui/toast";
import arrowLeft from "../assets/arrow-left.svg";
import { includedPhotos, type ReviewPerson } from "../model/personReview";
import {
  BackButton,
  Check,
  Footer,
  Grid,
  GridButton,
  GridHint,
  Header,
  HeaderTitle,
  Scroll,
  Submit,
  TOAST_ABOVE_BUTTON,
} from "./PersonReview.styles";

interface PersonPhotosProps {
  person: ReviewPerson;
  onToggle: (mediaId: number) => void;
  onDone: () => void;
  /** 그리드 위 안내. 비슷한 사진 모음은 "이 모음에 안 맞는 사진을…"처럼 바꿔 쓴다. */
  hint?: string;
  /** 뒤로 가기 버튼 이름. */
  backLabel?: string;
}

/**
 * 한 사람(또는 비슷한 사진 모음 하나)의 사진 확인 화면 (AI-4·4b). 사진마다 체크가 있고, 이 사람이 아닌 사진을 눌러 체크를 풀면 빠진다.
 * 뺀 사진은 흐리게 남겨 두어 다시 누르면 되돌릴 수 있다. 뺀 결과는 검토 화면이 들고 있다.
 */
export const PersonPhotos = ({
  person,
  onToggle,
  onDone,
  hint = "이 사람이 아닌 사진을 눌러 빼 주세요.",
  backLabel = "사람 목록으로",
}: PersonPhotosProps) => {
  const { showToast } = useToast();
  const closeExcludedToast = useRef<(() => void) | null>(null);

  const count = includedPhotos(person).length;
  const title = person.name ? `${person.name} ${count}장` : `${count}장`;

  return (
    <>
      <Header>
        <BackButton type="button" aria-label={backLabel} onClick={onDone}>
          <img src={arrowLeft} alt="" width={22} height={22} />
        </BackButton>
        <HeaderTitle>{title}</HeaderTitle>
      </Header>
      <Scroll>
        <GridHint>{hint}</GridHint>
        <Grid>
          {person.photos.map((photo, index) => {
            const isExcluded = person.excludedIds.includes(photo.mediaId);
            return (
              <li key={photo.mediaId}>
                <GridButton
                  type="button"
                  role="checkbox"
                  aria-checked={!isExcluded}
                  aria-label={`사진 ${index + 1}`}
                  $excluded={isExcluded}
                  onClick={() => {
                    onToggle(photo.mediaId);
                    const nextExcludedCount = person.excludedIds.length + (isExcluded ? -1 : 1);
                    if (nextExcludedCount > 0) {
                      closeExcludedToast.current = showToast({
                        message: `${nextExcludedCount}장을 뺐어요`,
                        showClose: false,
                        bottom: TOAST_ABOVE_BUTTON,
                      });
                    } else {
                      closeExcludedToast.current?.();
                      closeExcludedToast.current = null;
                    }
                  }}
                >
                  <img src={photo.thumbnailUrl} alt="" loading="lazy" />
                  <Check $checked={!isExcluded} aria-hidden="true">
                    {!isExcluded && <HiCheck />}
                  </Check>
                </GridButton>
              </li>
            );
          })}
        </Grid>
      </Scroll>
      <Footer>
        <Submit type="button" onClick={onDone}>
          완료
        </Submit>
      </Footer>
    </>
  );
};
