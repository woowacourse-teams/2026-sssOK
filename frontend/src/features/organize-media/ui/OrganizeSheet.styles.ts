import styled from "@emotion/styled";

import { colors, radius, spacing } from "@/shared/styles/tokens";

// 정리 시트의 본문과 버튼. 시트 틀과 반응형은 공용 BottomSheet가 담당한다.
export const Submit = styled.button`
  width: 100%;
  height: 56px;
  border-radius: ${radius[16]};
  background: ${colors.primary};
  font-size: 16px;
  font-weight: 700;
  color: ${colors.textInverse};

  &:active:not(:disabled) {
    background: ${colors.primaryPressed};
  }

  /* 막힌 정리 버튼(AI-1b · 817:3121). 회색 바탕에 흐린 글자다. */
  &:disabled {
    background: #edebe7;
    color: #a8a49d;
    cursor: not-allowed;
  }
`;

/** 확인 시트 버튼 묶음. 제목 덩어리와 24px 떨어진다(AI-1 · 814:2726 아래 여백). */
export const Actions = styled.div`
  padding-top: ${spacing[24]};
`;

/** 제목 옆 얼굴 그림. 24px 칸 세 개가 5px씩 겹친다(814:2727). */
export const Crowd = styled.span`
  display: flex;
  flex: none;
`;

export const Avatar = styled.span`
  position: relative;
  flex: none;
  width: 24px;
  height: 24px;

  &:not(:last-of-type) {
    margin-right: -5px;
  }

  /* 그림자 몫으로 칸보다 14.22% 크게 그린다. */
  > img {
    position: absolute;
    inset: -14.22%;
    display: block;
    max-width: none;
  }
`;

export const Later = styled.button`
  width: 100%;
  height: 44px;
  margin-top: ${spacing[4]};
  border-radius: ${radius[16]};
  font-size: 15px;
  font-weight: 700;
  color: ${colors.textSecondary};
`;

/** 제목 아래 본문 한 줄(G-3b). 15px, 위 6px·아래 20px 여백. */
export const Body = styled.p`
  width: 100%;
  padding: 6px 0 ${spacing[20]};
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
  color: ${colors.textPrimary};
  /* 낱말 중간에서 줄이 바뀌지 않게 한다. */
  word-break: keep-all;
`;
