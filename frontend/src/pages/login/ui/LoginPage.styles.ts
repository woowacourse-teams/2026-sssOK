import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main({
  minHeight: "100svh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  padding: `calc(${spacing[24]} + env(safe-area-inset-top, 0px)) clamp(16px, 5vw, 24px) calc(${spacing[24]} + env(safe-area-inset-bottom, 0px))`,
  background: colors.backgroundDefault,
});

export const Content = styled.div({
  flex: 1,
  width: "100%",
  maxWidth: 360,
  minWidth: 0,
  display: "flex",
  flexDirection: "column",
  gap: spacing[32],
  textAlign: "center",
});

export const Intro = styled.div({
  flex: 1,
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: spacing[24],
  paddingBlock: spacing[24],
  "@media (max-height: 600px)": {
    gap: spacing[16],
    paddingBlock: spacing[8],
  },
});

export const Illustration = styled.img({
  display: "block",
  width: 150,
  height: "auto",
  flexShrink: 0,
  objectFit: "contain",
});

export const IntroText = styled.div({
  display: "flex",
  flexDirection: "column",
  gap: spacing[16],
});

export const Heading = styled.h1({
  fontSize: "clamp(28px, 8vw, 36px)",
  fontWeight: 800,
  lineHeight: 1.3,
  color: colors.textStrong,
  letterSpacing: "-0.04em",
});

export const Highlight = styled.span({ color: colors.primary });

export const Description = styled.p({
  fontSize: "clamp(14px, 4vw, 16px)",
  fontWeight: 500,
  lineHeight: 1.6,
  color: colors.textSecondary,
});

export const Actions = styled.div({
  flexShrink: 0,
  width: "100%",
  display: "flex",
  flexDirection: "column",
  gap: spacing[12],
});

export const SocialButton = styled.button<{ $provider: "kakao" | "apple" }>(({ $provider }) => ({
  ...typography.label3,
  fontFamily: "inherit",
  width: "100%",
  minHeight: 54,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  gap: spacing[8],
  padding: `${spacing[12]} ${spacing[24]}`,
  border: 0,
  borderRadius: 12,
  background: $provider === "kakao" ? "#FEE500" : "#000000",
  color: $provider === "kakao" ? "rgba(0, 0, 0, 0.85)" : "#FFFFFF",
  cursor: "pointer",
  "& img, & svg": {
    display: "block",
    width: 20,
    height: 20,
    flexShrink: 0,
  },
  "&:disabled": { cursor: "wait", opacity: 0.65 },
  "&:focus-visible": { outline: `3px solid ${colors.primary}`, outlineOffset: 4 },
}));

export const Notice = styled.p({
  ...typography.caption1,
  color: colors.textSecondary,
  minHeight: 20,
});
