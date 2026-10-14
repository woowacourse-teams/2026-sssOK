import { useEffect, useMemo } from "react";
import { Navigate, useLocation, useParams } from "react-router-dom";

import chromeMascotImage from "@/shared/assets/mascot-chrome.png";
import { ROUTES } from "@/shared/config";
import { Stack } from "@/shared/ui/stack";
import { buildChromeIntentUrl, openChrome, shouldOpenInChrome } from "../lib/browserEntry";
import {
  ActionButton,
  Actions,
  Content,
  Description,
  Image,
  Page,
  Title,
} from "./BrowserEntryPage.styles";

const ATTEMPT_KEY_PREFIX = "sssok:chrome-entry-attempted:";

export const BrowserEntryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const location = useLocation();
  const needsChrome = shouldOpenInChrome(navigator.userAgent);

  const { entryUrl, chromeIntentUrl, attemptKey } = useMemo(() => {
    const queryAndHash = `${location.search}${location.hash}`;
    const entry = new URL(`${ROUTES.roomEntry(code)}${queryAndHash}`, window.location.origin);
    const fallback = new URL(`${ROUTES.roomInvite(code)}${queryAndHash}`, window.location.origin);

    return {
      entryUrl: `${entry.pathname}${entry.search}${entry.hash}`,
      chromeIntentUrl: buildChromeIntentUrl(entry, fallback),
      attemptKey: `${ATTEMPT_KEY_PREFIX}${fallback.href}`,
    };
  }, [code, location.hash, location.search]);

  useEffect(() => {
    if (!needsChrome || sessionStorage.getItem(attemptKey) === "true") return;

    sessionStorage.setItem(attemptKey, "true");
    openChrome(chromeIntentUrl);
  }, [attemptKey, chromeIntentUrl, needsChrome]);

  if (!needsChrome) {
    return <Navigate to={entryUrl} replace />;
  }

  return (
    <Page>
      <Content>
        <Image src={chromeMascotImage} alt="Chrome 브라우저 이용을 안내하는 캐릭터" />

        <Stack gap={8} align="center">
          <Title>Chrome에서 열어주세요</Title>
          <Description>
            {"여러 장의 사진을 한 번에 저장할 수 있도록\nChrome 브라우저에서 이용해 주세요."}
          </Description>
        </Stack>
      </Content>

      <Actions>
        <ActionButton onClick={() => openChrome(chromeIntentUrl)}>Chrome에서 계속하기</ActionButton>
      </Actions>
    </Page>
  );
};
