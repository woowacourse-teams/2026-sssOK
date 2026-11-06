import { useEffect, useState } from "react";

import { Button } from "@/shared/ui/button";
import { Spinner } from "@/shared/ui/spinner";
import { Toast } from "@/shared/ui/toast";
import arrowLeft from "../assets/arrow-left.svg";
import type { OrganizeResult } from "../api/types";
import { createPersonReview, type PersonReview, togglePhoto } from "../model/personReview";
import { renameCard, similarFooterOf } from "../model/similarReview";
import { PersonPhotos } from "./PersonPhotos";
import {
  BackButton,
  Cards,
  Centered,
  Column,
  Footer,
  Header,
  HeaderTitle,
  Heading,
  Hint,
  Screen,
  Scroll,
  Submit,
  Subtitle,
  Title,
} from "./PersonReview.styles";
import type { PersonReviewSource } from "./PersonReviewScreen";
import { SimilarCard } from "./SimilarCard";

const SCREEN_TITLE = "비슷한 사진 나누기";
/** 하단 영역(안내 29 + 버튼 56 + 여백 28) 바로 위에 띄운다. */
const TOAST_BOTTOM = "calc(112px + env(safe-area-inset-bottom, 0px))";
const TOAST_MS = 2500;

interface SimilarReviewScreenProps {
  source: PersonReviewSource;
  /** 방에 이미 있는 폴더 이름. 같은 이름을 쓰면 이름을 바꾸게 한다. */
  folderNames: string[];
  onClose: () => void;
  onRetry: () => void;
}

const BackHeader = ({ onClick }: { onClick: () => void }) => (
  <Header>
    <BackButton type="button" aria-label="갤러리로 돌아가기" onClick={onClick}>
      <img src={arrowLeft} alt="" width={22} height={22} />
    </BackButton>
    <HeaderTitle>{SCREEN_TITLE}</HeaderTitle>
  </Header>
);

/**
 * 비슷한 사진 나누기 검토 화면 (AI-S2·S3·S4·S4b). 사람별 검토와 같은 틀이고,
 * 모음마다 이름을 직접 붙이거나 안 맞는 사진을 뺀다. 붙인 이름과 뺀 사진은 이 화면만 들고 있어 나가면 사라진다.
 */
export const SimilarReviewScreen = ({ source, ...props }: SimilarReviewScreenProps) => {
  // 검토하는 동안 뒤의 갤러리가 같이 스크롤되지 않게 막는다.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  return (
    <Screen role="dialog" aria-modal="true" aria-label={SCREEN_TITLE}>
      <Column>
        {source.state === "ready" ? (
          <Review result={source.result} {...props} />
        ) : (
          <>
            <BackHeader onClick={props.onClose} />
            <Centered>
              {source.state === "loading" ? (
                <Spinner label="결과를 불러오는 중" />
              ) : (
                <>
                  <p role="alert">결과를 불러오지 못했어요.</p>
                  <Button variant="default" onClick={props.onRetry}>
                    다시 불러오기
                  </Button>
                </>
              )}
            </Centered>
          </>
        )}
      </Column>
    </Screen>
  );
};

type ReviewProps = Omit<SimilarReviewScreenProps, "source" | "onRetry"> & {
  result: OrganizeResult;
};

const Review = ({ result, folderNames, onClose }: ReviewProps) => {
  const [review, setReview] = useState<PersonReview>(() => createPersonReview(result.groups));
  const [openedId, setOpenedId] = useState<string | null>(null);
  /** 폴더로 반영하는 건 아직 없다(#728). 버튼을 누르면 준비 중이라고만 알린다. */
  const [isSubmitPending, setIsSubmitPending] = useState(false);

  useEffect(() => {
    if (!isSubmitPending) return;
    const timer = window.setTimeout(() => setIsSubmitPending(false), TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [isSubmitPending]);

  const opened = review.people.find((card) => card.id === openedId) ?? null;
  const footer = similarFooterOf(review, folderNames);

  if (opened) {
    return (
      <PersonPhotos
        person={opened}
        hint="이 모음에 안 맞는 사진을 눌러 빼 주세요."
        backLabel="모음 목록으로"
        onToggle={(mediaId) => setReview((current) => togglePhoto(current, opened.id, mediaId))}
        onDone={() => setOpenedId(null)}
      />
    );
  }

  return (
    <>
      <BackHeader onClick={onClose} />
      <Scroll>
        <Heading>
          <Title>사진을 {review.people.length}개로 묶었어요.</Title>
          <Subtitle>같은 이름을 쓰면 한 폴더로 합쳐져요.</Subtitle>
        </Heading>
        <Cards>
          {review.people.map((card) => (
            <SimilarCard
              key={card.id}
              card={card}
              onOpenPhotos={() => setOpenedId(card.id)}
              onRename={(value) => setReview((current) => renameCard(current, card.id, value))}
            />
          ))}
        </Cards>
      </Scroll>
      <Footer>
        {footer.hint && <Hint>{footer.hint}</Hint>}
        <Submit
          type="button"
          disabled={!footer.canSubmit}
          onClick={() => setIsSubmitPending(true)}
        >
          {footer.submit}
        </Submit>
      </Footer>

      {isSubmitPending && (
        <Toast
          message="폴더 만들기는 곧 열려요."
          showClose={false}
          bottom={TOAST_BOTTOM}
          onClose={() => setIsSubmitPending(false)}
        />
      )}
    </>
  );
};
