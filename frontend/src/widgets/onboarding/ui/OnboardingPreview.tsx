import { useRef } from "react";

import { useFitScale } from "../model/useFitScale";
import { PREVIEW_STEP_DURATIONS_MS, usePreviewStep } from "../model/usePreviewStep";
import {
  ChatCard,
  ChatRoomName,
  HiddenText,
  LinkBubble,
  LinkPreview,
  Preview,
  ReplyBubble,
  Scene,
  SceneCanvas,
  SceneStage,
  StepButton,
  StepItem,
  StepList,
  StepNumber,
  StepProgress,
  StepTitle,
} from "./OnboardingPreview.styles";
import { PREVIEW_BAR_HEIGHT } from "./OnboardingPreviewBars.styles";
import { DownloadScene, UploadScene } from "./OnboardingPreviewScenes";

const STEPS = [
  { title: "링크 보내고 🔗", caption: "방을 만들고 단톡방에 링크를 보내요" },
  { title: "업로드 하고 👉", caption: "다 같이 사진을 올려 한곳에 모아요" },
  { title: "골라 받고 ⬇️", caption: "원하는 것만 골라 원본으로 받아요" },
] as const;

/**
 * 장면을 그린 크기. 실제 화면 폭(343px)으로 갤러리(233px)와 하단 바(56px)를 그리고,
 * 미리보기 칸에 맞춰 장면 전체를 줄이거나 키운다.
 */
const SCENE_SIZE = { width: 343, height: 233 + 12 + PREVIEW_BAR_HEIGHT, padding: 12, max: 1.4 };

/** 움직임을 줄인 환경에서 멈춰 둘 장면. 쏙의 핵심인 "골라 받기" 를 보여준다. */
const STATIC_SCENE = STEPS.length - 1;

/**
 * 홈에서 쏙을 어떻게 쓰는지 세 장면으로 돌려 보여준다.
 *
 * 그림은 장식이라 스크린 리더와 키보드에서 뺀다(aria-hidden·inert). 장면이 바뀔 때마다
 * 다시 읽히면 시끄럽기만 하다. 같은 내용은 아래 단계 목록이 글로 한 번만 전한다.
 */
export const OnboardingPreview = () => {
  const { step, goTo, round, reducedMotion } = usePreviewStep();
  const visibleScene = step ?? STATIC_SCENE;
  const stageRef = useRef<HTMLDivElement>(null);
  const { scale, fillWidth, fillHeight } = useFitScale(stageRef, SCENE_SIZE);

  /** 지금 장면만 시간을 흘린다. 장면에 들어올 때마다 새로 그려 처음부터 튼다. */
  const sceneKey = (index: number) => (visibleScene === index ? `on-${round}` : "off");
  const isRunning = (index: number) => !reducedMotion && visibleScene === index;

  return (
    <Preview>
      <SceneStage ref={stageRef} aria-hidden="true" inert>
        <SceneCanvas
          style={{
            width: fillWidth,
            height: fillHeight,
            transform: `translate(-50%, -50%) scale(${scale})`,
          }}
        >
          <Scene key={sceneKey(0)} $active={visibleScene === 0}>
            <ChatCard>
              <ChatRoomName>공쥬들의 방 👑</ChatRoomName>
              <LinkBubble>
                여기다 사진 다 올려줘!
                <LinkPreview>
                  <b>쏙 · 공쥬들 휴가 🏝️ </b>
                  ssssok.com/rooms/…
                </LinkPreview>
              </LinkBubble>
              <ReplyBubble $delay={1000}>오케이!</ReplyBubble>
              <ReplyBubble $delay={1600}>방금 사진 올렸어용</ReplyBubble>
            </ChatCard>
          </Scene>

          <Scene key={sceneKey(1)} $active={visibleScene === 1}>
            <UploadScene running={isRunning(1)} />
          </Scene>

          <Scene key={sceneKey(2)} $active={visibleScene === 2}>
            <DownloadScene running={isRunning(2)} />
          </Scene>
        </SceneCanvas>
      </SceneStage>

      <StepList aria-label="쏙 사용 방법">
        {STEPS.map((item, index) => {
          const isActive = step === null || step === index;
          return (
            <StepItem key={item.title}>
              <StepButton
                type="button"
                $active={isActive}
                aria-current={step === index ? "step" : undefined}
                onClick={() => goTo(index)}
              >
                <StepNumber $active={isActive}>{index + 1}</StepNumber>
                <StepTitle $active={isActive}>{item.title}</StepTitle>
                <HiddenText>{item.caption}</HiddenText>
                <StepProgress
                  key={round}
                  aria-hidden="true"
                  $state={
                    step === null || index < step ? "done" : step === index ? "running" : "idle"
                  }
                  $duration={PREVIEW_STEP_DURATIONS_MS[index]}
                />
              </StepButton>
            </StepItem>
          );
        })}
      </StepList>
    </Preview>
  );
};
