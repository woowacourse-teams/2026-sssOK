import { useEffect, useState } from "react";

import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { Spinner } from "@/shared/ui/spinner";
import { Stack } from "@/shared/ui/stack";
import { Toast } from "@/shared/ui/toast";
import arrowLeft from "../assets/arrow-left.svg";
import type { OrganizeApplyGroup, OrganizeResult } from "../api/types";
import {
  applyGroupsOf,
  conflictOf,
  createPersonReview,
  footerOf,
  includedPhotos,
  mergePeople,
  ownerOfName,
  type PersonReview,
  toggleName,
  togglePhoto,
} from "../model/personReview";
import { type NameChip, PersonCard } from "./PersonCard";
import { PersonPhotos } from "./PersonPhotos";
import {
  BackButton,
  Cards,
  Centered,
  Column,
  ConfirmBody,
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

/** 하단 영역(안내 29 + 버튼 56 + 여백 28) 바로 위에 합친 결과 토스트를 띄운다 (AI-3c). */
const TOAST_BOTTOM = "calc(112px + env(safe-area-inset-bottom, 0px))";
const TOAST_MS = 2500;

export type PersonReviewSource =
  { state: "loading" } | { state: "failed" } | { state: "ready"; result: OrganizeResult };

interface PersonReviewScreenProps {
  source: PersonReviewSource;
  /** 내 이름. 칩에는 "나" 로 보인다. */
  myName: string;
  /** 이름 칩으로 보여 줄 다른 방 멤버 이름. */
  memberNames: string[];
  /** 방에 이미 있는 폴더 이름. 같은 이름을 고르면 이름을 바꾸게 한다. */
  folderNames: string[];
  onClose: () => void;
  onRetry: () => void;
  /** 이름을 고른 사람들로 폴더를 만든다. 뺀 사진은 빠져 있고, 같은 이름은 한 그룹으로 합쳐 있다. */
  onSubmit: (groups: OrganizeApplyGroup[]) => void;
  /** 폴더를 만드는 중이면 버튼을 막는다. */
  isSubmitting?: boolean;
  /** 폴더를 만들지 못한 까닭. 화면에 남아 다시 누를 수 있다. */
  submitError?: string | null;
  onDismissError?: () => void;
}

const BackHeader = ({ label, onClick }: { label: string; onClick: () => void }) => (
  <Header>
    <BackButton type="button" aria-label="갤러리로 돌아가기" onClick={onClick}>
      <img src={arrowLeft} alt="" width={22} height={22} />
    </BackButton>
    <HeaderTitle>{label}</HeaderTitle>
  </Header>
);

/**
 * 사람별 나누기 검토 화면. 결과를 읽는 동안·못 읽었을 때도 같은 틀 안에서 보여 준다.
 * 고른 이름과 뺀 사진은 이 화면만 들고 있어서, 나가면 사라진다.
 */
export const PersonReviewScreen = ({ source, ...props }: PersonReviewScreenProps) => {
  // 검토하는 동안 뒤의 갤러리가 같이 스크롤되지 않게 막는다.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  return (
    <Screen role="dialog" aria-modal="true" aria-label="사람별로 나누기">
      <Column>
        {source.state === "ready" ? (
          <Review result={source.result} {...props} />
        ) : (
          <>
            <BackHeader label="사람별로 나누기" onClick={props.onClose} />
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

type ReviewProps = Omit<PersonReviewScreenProps, "source" | "onRetry"> & { result: OrganizeResult };

interface PendingMerge {
  personId: string;
  ownerId: string;
  name: string;
}

const Review = ({
  result,
  myName,
  memberNames,
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
  const [typedNames, setTypedNames] = useState<string[]>([]);
  const [mergedNotice, setMergedNotice] = useState<{ key: number; message: string } | null>(null);

  useEffect(() => {
    if (!mergedNotice) return;
    const timer = window.setTimeout(() => setMergedNotice(null), TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [mergedNotice]);

  const chips: NameChip[] = [
    { value: myName, label: "나" },
    ...[...new Set([...memberNames, ...typedNames])]
      .filter((name) => name !== myName)
      .map((name) => ({ value: name, label: name })),
  ];
  const opened = review.people.find((person) => person.id === openedId) ?? null;
  const footer = footerOf(review, folderNames);
  const conflict = conflictOf(review, folderNames);

  /** 다른 카드가 쓰는 이름이면 합칠지 먼저 묻는다. */
  const pickName = (personId: string, name: string) => {
    const owner = ownerOfName(review, personId, name);
    if (owner) {
      setPendingMerge({ personId, ownerId: owner.id, name });
      return;
    }
    setReview((current) => toggleName(current, personId, name));
  };

  /** 직접 입력한 이름은 칩으로도 남기고, 늘 그 이름을 고른다. */
  const typeName = (personId: string, name: string) => {
    setTypedNames((current) => (current.includes(name) ? current : [...current, name]));
    const person = review.people.find((item) => item.id === personId);
    if (person?.name !== name) pickName(personId, name);
  };

  const merge = ({ ownerId, personId, name }: PendingMerge) => {
    const next = mergePeople(review, ownerId, personId);
    const owner = next.people.find((person) => person.id === ownerId);
    setReview(next);
    setPendingMerge(null);
    if (owner) {
      setMergedNotice((current) => ({
        key: (current?.key ?? 0) + 1,
        message: `${name} 님 사진을 합쳤어요 (${includedPhotos(owner).length}장)`,
      }));
    }
  };

  if (opened) {
    return (
      <PersonPhotos
        person={opened}
        onToggle={(mediaId) => setReview((current) => togglePhoto(current, opened.id, mediaId))}
        onDone={() => setOpenedId(null)}
      />
    );
  }

  return (
    <>
      <BackHeader label="사람별로 나누기" onClick={onClose} />
      <Scroll>
        <Heading>
          <Title>{review.people.length}명을 찾았어요.</Title>
          <Subtitle>같은 이름을 고르면 한 폴더로 합쳐져요.</Subtitle>
        </Heading>
        <Cards>
          {review.people.map((person) => (
            <PersonCard
              key={person.id}
              person={person}
              chips={chips}
              needsRename={conflict?.id === person.id}
              onOpenPhotos={() => setOpenedId(person.id)}
              onPickName={(name) => pickName(person.id, name)}
              onTypeName={(name) => typeName(person.id, name)}
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
          onClick={() => onSubmit(applyGroupsOf(review.people))}
        >
          {isSubmitting ? "폴더를 만드는 중" : footer.submit}
        </Submit>
      </Footer>

      {mergedNotice && (
        <Toast
          key={mergedNotice.key}
          message={mergedNotice.message}
          showClose={false}
          bottom={TOAST_BOTTOM}
          onClose={() => setMergedNotice(null)}
        />
      )}

      {submitError && (
        <Toast
          tone="error"
          message={submitError}
          bottom={TOAST_BOTTOM}
          onClose={() => onDismissError?.()}
        />
      )}

      {pendingMerge && (
        <Modal onClose={() => setPendingMerge(null)} showClose={false}>
          <ConfirmBody>
            <h2>{pendingMerge.name} 님이 이미 있어요. 같은 사람인가요?</h2>
            <Stack gap={8}>
              <Button onClick={() => merge(pendingMerge)}>네, 합칠게요</Button>
              <Button variant="default" onClick={() => setPendingMerge(null)}>
                아니요
              </Button>
            </Stack>
          </ConfirmBody>
        </Modal>
      )}
    </>
  );
};
