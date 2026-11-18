import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

// 정리 시트의 본문과 보조 버튼. 시트 틀과 주 버튼은 다른 시트와 같은 공용 BottomSheet·Button 을 쓴다.

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

/**
 * 주 버튼 아래 회색 글자 버튼. 배경이 없어 높이가 그대로 빈 공간으로 보이므로
 * 공용 Button(55px)보다 낮게 둔다. 시트 아래 여백이 커 보이지 않게 하려는 것이다.
 */
export const Later = styled.button`
  width: 100%;
  height: 44px;
  margin-top: ${spacing[4]};
  border-radius: ${radius[12]};
  ${typography.label5}
  color: ${colors.textSecondary};
`;

/** 제목 아래 본문 한 줄(G-3b). 버튼과 20px 떨어진다. */
export const Body = styled.p`
  width: 100%;
  padding-bottom: ${spacing[20]};
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
  color: ${colors.textPrimary};
  /* 낱말 중간에서 줄이 바뀌지 않게 한다. */
  word-break: keep-all;
`;
