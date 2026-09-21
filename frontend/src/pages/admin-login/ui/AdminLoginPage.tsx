import type { FormEvent } from "react";

import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { useAdminLogin } from "../model/useAdminLogin";
import { Description, Fields, Form, Heading, LockNote, Page, Title } from "./AdminLoginPage.styles";

export const AdminLoginPage = () => {
  const {
    loginId,
    password,
    errorMessage,
    lockNote,
    isPending,
    canSubmit,
    isCountingDown,
    remainingSeconds,
    changeLoginId,
    changePassword,
    submit,
  } = useAdminLogin();

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void submit();
  };

  const buttonLabel = isCountingDown
    ? `${remainingSeconds}초 후 다시 시도할 수 있어요`
    : isPending
      ? "로그인 중..."
      : "로그인";

  return (
    <Page>
      <Form onSubmit={handleSubmit}>
        <Heading>
          <Badge size="md" variant="soft">
            관리자
          </Badge>
          <Title>쏙 관리자 로그인</Title>
          <Description>발급받은 관리자 계정으로 로그인해주세요.</Description>
        </Heading>

        <Fields>
          <Input
            label="아이디"
            name="loginId"
            value={loginId}
            autoComplete="username"
            placeholder="아이디를 입력하세요"
            onValueChange={changeLoginId}
          />

          <Input
            label="비밀번호"
            name="password"
            type="password"
            value={password}
            autoComplete="current-password"
            placeholder="비밀번호를 입력하세요"
            errorMessage={errorMessage}
            onValueChange={changePassword}
          />
        </Fields>

        <Button type="submit" disabled={!canSubmit}>
          {buttonLabel}
        </Button>

        {lockNote && <LockNote role="status">{lockNote}</LockNote>}
      </Form>
    </Page>
  );
};
