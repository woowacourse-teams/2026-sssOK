import { useEffect, useState } from "react";

import { Button } from "@/shared/ui/button";
import { Spinner } from "@/shared/ui/spinner";
import { useToast } from "@/shared/ui/toast";
import arrowLeft from "../assets/arrow-left.svg";
import type { OrganizeApplyGroup, OrganizeResult } from "../api/types";
import {
  createPersonReview,
  includedPhotos,
  mergePeople,
  type PersonReview,
  togglePhoto,
} from "../model/personReview";
import {
  folderNameOf,
  renameCard,
  similarConflictOf,
  similarFooterOf,
  similarApplyGroupsOf,
  similarOwnerOf,
} from "../model/similarReview";
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
  TOAST_ABOVE_HINT,
} from "./PersonReview.styles";
import type { PersonReviewSource } from "./PersonReviewScreen";
import { SimilarCard } from "./SimilarCard";
import { SimilarMergeSheet } from "./SimilarMergeSheet";

const SCREEN_TITLE = "비슷한 사진 나누기";

interface SimilarReviewScreenProps {
  source: PersonReviewSource;
  /** 방에 이미 있는 폴더 이름. 같은 이름을 쓰면 이름을 바꾸게 한다. */
  folderNames: string[];
  onClose: () => void;
  onRetry: () => void;
  /** 이름을 붙인 모음으로 폴더를 만든다. 뺀 사진은 빠져 있고, 같은 이름은 한 그룹으로 합쳐 있다. */
  onSubmit: (groups: OrganizeApplyGroup[]) => void;
  /** 폴더를 만드는 중이면 버튼을 막는다. */
  isSubmitting?: boolean;
  /** 폴더를 만들지 못한 까닭. 화면에 남아 다시 누를 수 있다. */
  submitError?: string | null;
  onDismissError?: () => void;
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
 * 다른 모음과 같은 이름을 붙이면 합칠지 묻고(AI-S3b·S3c), 방에 있는 폴더 이름이면 바꾸게 한다(AI-S3d).
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

/** 합칠지 묻는 중인 두 모음. `cardId` 가 방금 이름을 붙인 쪽, `ownerId` 가 그 이름을 먼저 쓴 쪽이다. */
interface PendingMerge {
  cardId: string;
  ownerId: string;
  name: string;
}

const Review = ({
  result,
  folderNames,
  onClose,
  onSubmit,
  isSubmitting = false,
  submitError = null,
  onDismissError,
}: ReviewProps) => {
  const [review, setReview] = useState<PersonReview>(() => createPersonReview(result.groups));
  const [openedId, setOpenedId] = useState<string | null>(null);
  const [pendingMerge, setPendingMerge] = useState<PendingMerge | null>(null);
  const { showToast } = useToast();
  useEffect(() => {
    if (!submitError) return;
    showToast({ message: submitError, tone: "error", bottom: TOAST_ABOVE_HINT });
    onDismissError?.();
  }, [submitError, onDismissError, showToast]);

  const opened = review.people.find((card) => card.id === openedId) ?? null;
  const footer = similarFooterOf(review, folderNames);
  const conflict = similarConflictOf(review, folderNames);

  /** 이름을 다 쓰면, 같은 이름을 먼저 쓴 모음이 있을 때 합칠지 묻는다. */
  const commitName = (cardId: string) => {
    const owner = similarOwnerOf(review, cardId);
    const name = owner && folderNameOf(owner);
    if (owner && name) setPendingMerge({ cardId, ownerId: owner.id, name });
  };

  const merge = ({ cardId, ownerId, name }: PendingMerge) => {
    const next = mergePeople(review, ownerId, cardId);
    const owner = next.people.find((card) => card.id === ownerId);
    setReview(next);
    setPendingMerge(null);
    if (owner) {
      showToast({
        message: `${name} 사진을 합쳤어요 (${includedPhotos(owner).length}장)`,
        bottom: TOAST_ABOVE_HINT,
      });
    }
  };

  /** 합치지 않으면 방금 붙인 이름을 떼어 이름 없는 모음으로 돌린다. */
  const cancelMerge = ({ cardId }: PendingMerge) => {
    setReview((current) => renameCard(current, cardId, ""));
    setPendingMerge(null);
  };

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
              needsRename={conflict?.id === card.id}
              onOpenPhotos={() => setOpenedId(card.id)}
              onRename={(value) => setReview((current) => renameCard(current, card.id, value))}
              onCommit={() => commitName(card.id)}
            />
          ))}
        </Cards>
      </Scroll>
      <Footer>
        {footer.hint && <Hint>{footer.hint}</Hint>}
        <Submit
          type="button"
          disabled={!footer.canSubmit || isSubmitting}
          aria-busy={isSubmitting}
          onClick={() => onSubmit(similarApplyGroupsOf(review.people))}
        >
          {isSubmitting ? "폴더를 만드는 중" : footer.submit}
        </Submit>
      </Footer>

      {pendingMerge && (
        <SimilarMergeSheet
          name={pendingMerge.name}
          onMerge={() => merge(pendingMerge)}
          onCancel={() => cancelMerge(pendingMerge)}
        />
      )}
    </>
  );
};
