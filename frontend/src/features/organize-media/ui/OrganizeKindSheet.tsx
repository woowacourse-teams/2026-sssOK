import { useId, useState } from "react";
import { createPortal } from "react-dom";

import { isAppWebView } from "@/shared/lib";

import type { OrganizeKind } from "../api/types";
import avatar1 from "../assets/avatar-1.svg";
import avatar2 from "../assets/avatar-2.svg";
import avatar3 from "../assets/avatar-3.svg";
import checkIcon from "../assets/check.svg";
import similarCard from "../assets/similar-card.svg";
import similarSceneCenter from "../assets/similar-scene-center.svg";
import similarSceneLeft from "../assets/similar-scene-left.svg";
import similarSceneRight from "../assets/similar-scene-right.svg";
import {
  Avatar,
  CheckMark,
  Crowd,
  Label,
  Notice,
  Option,
  Options,
  SceneCard,
  Soon,
  Thumb,
  ThumbArt,
  ThumbFrame,
} from "./OrganizeKindSheet.styles";
import { Dialog, Handle, Later, Overlay, Submit, Title } from "./OrganizeSheet.styles";

interface KindOption {
  kind: OrganizeKind;
  label: string;
  cta: string;
  ready: boolean;
}

const KINDS: KindOption[] = [
  { kind: "PERSON", label: "사람별로", cta: "얼굴 분석해서 정리하기", ready: true },
  { kind: "SIMILAR", label: "비슷한 사진", cta: "비슷한 사진 묶기", ready: true },
];

/**
 * 조금씩 다른 풍경 카드 세 장(피그마 661:2754). 좌우 카드가 ±8° 기울고 가운데 카드가 맨 위다.
 * 그리는 순서가 겹치는 순서라 왼쪽·오른쪽·가운데 순으로 둔다.
 */
const SIMILAR_SCENES = [
  { src: similarSceneLeft, offset: -1 },
  { src: similarSceneRight, offset: 1 },
  { src: similarSceneCenter, offset: 0 },
] as const;

/** 시트는 분석 전에 뜨므로 방의 사진을 쓰지 않는다. 어느 방에서나 같은 예시 그림을 보여 준다. */
const KindThumb = ({ kind }: { kind: OrganizeKind }) =>
  kind === "PERSON" ? (
    <Crowd>
      {[avatar1, avatar2, avatar3].map((src) => (
        <Avatar key={src}>
          <img src={src} alt="" width={38} height={38} />
        </Avatar>
      ))}
    </Crowd>
  ) : (
    <>
      {SIMILAR_SCENES.map(({ src, offset }) => (
        <SceneCard key={src} $offset={offset}>
          <img src={similarCard} alt="" width={66} height={58} />
          <img src={src} alt="" width={62} height={54} />
        </SceneCard>
      ))}
    </>
  );

interface OrganizeKindSheetProps {
  /** 로그인하지 않은 사람. 한 번만 체험할 수 있다는 걸 시작 전에 알린다. */
  isGuest: boolean;
  isStarting?: boolean;
  onStart: (kind: OrganizeKind) => void;
  onClose: () => void;
}

/** '자동으로 정리하기' 를 누르면 뜨는 정리 방식 선택 시트. '사람별로' 와 '비슷한 사진' 중에 고른다. */
export const OrganizeKindSheet = ({
  isGuest,
  isStarting = false,
  onStart,
  onClose,
}: OrganizeKindSheetProps) => {
  const [kind, setKind] = useState<OrganizeKind>("PERSON");
  const name = useId();
  const titleId = useId();
  const current = KINDS.find((option) => option.kind === kind) ?? KINDS[0];
  // 장수 한도는 시트를 열기 전에 확인한다(`organizeLimitOf`). 여기까지 왔으면 시작할 수 있다.

  return createPortal(
    <Overlay data-testid="organize-kind-overlay" onClick={onClose}>
      <Dialog
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
      >
        <Handle aria-hidden="true" />
        <Title id={titleId}>어떻게 정리할까요?</Title>

        <Options role="radiogroup" aria-labelledby={titleId}>
          {KINDS.map(({ kind: value, label, cta, ready }) => {
            const isSelected = kind === value;
            return (
              <Option key={value} $disabled={!ready}>
                <input
                  type="radio"
                  name={name}
                  value={value}
                  aria-label={cta}
                  checked={isSelected}
                  disabled={!ready}
                  onChange={() => setKind(value)}
                />
                <ThumbFrame aria-hidden="true">
                  <Thumb $selected={isSelected}>
                    <ThumbArt $blurred={!ready}>
                      <KindThumb kind={value} />
                    </ThumbArt>
                    {isSelected && (
                      <CheckMark>
                        <img src={checkIcon} alt="" width={14} height={14} />
                      </CheckMark>
                    )}
                    {!ready && <Soon>준비 중</Soon>}
                  </Thumb>
                </ThumbFrame>
                <Label $selected={isSelected}>{label}</Label>
              </Option>
            );
          })}
        </Options>

        {isGuest && (
          <Notice>
            {/* 웹에서는 로그인할 곳이 앱이라 설치를 권하고, 앱 안에서는 로그인을 권한다. */}
            {isAppWebView()
              ? "로그인하지 않으면 AI 정리는 한 번만 체험할 수 있어요."
              : "앱을 설치하지 않으면 AI 정리는 한 번만 체험할 수 있어요."}
          </Notice>
        )}

        <Submit type="button" disabled={isStarting} onClick={() => onStart(current.kind)}>
          {current.cta}
        </Submit>
        <Later type="button" onClick={onClose}>
          다음에 할게요
        </Later>
      </Dialog>
    </Overlay>,
    document.body,
  );
};
