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
} from "./PersonReview.styles";

/** 하단 여백(16) + '완료'(56) + 간격(10) 위에 토스트를 띄운다 (AI-4b). */
const TOAST_MS = 2500;

interface PersonPhotosProps {
  person: ReviewPerson;
  onToggle: (mediaId: number) => void;
  onDone: () => void;
}

/**
 * 한 사람의 사진 확인 화면 (AI-4·4b). 사진마다 체크가 있고, 이 사람이 아닌 사진을 눌러 체크를 풀면 빠진다.
 * 뺀 사진은 흐리게 남겨 두어 다시 누르면 되돌릴 수 있다. 뺀 결과는 검토 화면이 들고 있다.
 */
export const PersonPhotos = ({ person, onToggle, onDone }: PersonPhotosProps) => {
  const { showToast } = useToast();

  const count = includedPhotos(person).length;
  const title = person.name ? `${person.name} ${count}장` : `${count}장`;

  return (
    <>
      <Header>
        <BackButton type="button" aria-label="사람 목록으로" onClick={onDone}>
          <img src={arrowLeft} alt="" width={22} height={22} />
        </BackButton>
        <HeaderTitle>{title}</HeaderTitle>
      </Header>
      <Scroll>
        <GridHint>이 사람이 아닌 사진을 눌러 빼 주세요.</GridHint>
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
                      showToast({
                        message: `${nextExcludedCount}장을 뺐어요`,
                        duration: TOAST_MS,
                        showClose: false,
                      });
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
