import { useCallback, useEffect } from "react";
import { useNavigate } from "react-router-dom";

import {
  AccountRoleBadge,
  formatAccountDate,
  useAdminAccountsQuery,
} from "@/entities/admin-account";
import { isSuperAdmin, readValidAdminSession, removeAdminSession } from "@/entities/admin-session";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { Button } from "@/shared/ui/button";
import {
  Actions,
  Cell,
  Count,
  CreatedAt,
  HeadCell,
  LoginId,
  MeTag,
  Name,
  NameCell,
  Notice,
  Page,
  Row,
  RowButton,
  SmallButton,
  Table,
  TableScroll,
  Title,
  TitleGroup,
  TitleRow,
} from "./AdminAccountsPage.styles";

/** 추가·수정·삭제는 다음 이슈에서 붙인다. 그때까지 자리만 보여주고 누를 수 없다. */
const NOT_READY = "준비 중인 기능이에요";

export const AdminAccountsPage = () => {
  const navigate = useNavigate();
  // AdminRoute 가 이미 걸러 주지만, 세션은 화면이 떠 있는 동안에도 만료될 수 있다.
  const session = readValidAdminSession();
  const canManage = session !== null && isSuperAdmin(session.role);

  const { data, error, isPending, refetch } = useAdminAccountsQuery(session?.accessToken ?? "");
  const accounts = data?.accounts ?? [];

  const isUnauthorized = isApiError(error) && error.status === 401;
  // 권한이 없는 계정은 다시 눌러도 같은 답이 온다 — 재시도 버튼을 주지 않는다.
  const isForbidden = isApiError(error) && error.status === 403;

  // 401 을 로그인으로 돌리는 이유는 의견 목록(`AdminFeedbacksPage`)과 같다.
  const leaveToLogin = useCallback(() => {
    removeAdminSession();
    navigate(ROUTES.adminLogin, { replace: true });
  }, [navigate]);

  useEffect(() => {
    if (isUnauthorized) leaveToLogin();
  }, [isUnauthorized, leaveToLogin]);

  return (
    <Page>
      <TitleRow>
        <TitleGroup>
          <Title>계정</Title>
          {data && <Count>{accounts.length}</Count>}
        </TitleGroup>

        {canManage && (
          <SmallButton type="button" tone="primary" disabled title={NOT_READY}>
            + 계정 추가
          </SmallButton>
        )}
      </TitleRow>

      {isPending && <Notice>계정을 불러오고 있어요.</Notice>}

      {/* 로그인 화면으로 옮기는 중이라 여기서 또 안내하지 않는다. */}
      {error !== null && !isUnauthorized && (
        <Notice>
          {isForbidden ? "이 계정으로는 계정 목록을 볼 수 없어요." : "계정을 불러오지 못했어요."}
          {!isForbidden && (
            <Button size="sm" variant="default" onClick={() => void refetch()}>
              다시 시도
            </Button>
          )}
        </Notice>
      )}

      {data && (
        <TableScroll>
          <Table>
            <thead>
              <tr>
                <HeadCell scope="col" width={252}>
                  이름
                </HeadCell>
                <HeadCell scope="col" width={272}>
                  아이디
                </HeadCell>
                <HeadCell scope="col" width={212}>
                  역할
                </HeadCell>
                <HeadCell scope="col" width={192}>
                  생성일
                </HeadCell>
                {/* 동작 칸은 눈에 보이는 제목이 없다. 버튼이 없는 관리자에게는 칸째로 뺀다. */}
                {canManage && <HeadCell scope="col" aria-label="관리" />}
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <Row key={account.adminId}>
                  <Cell>
                    <NameCell>
                      <Name>{account.name}</Name>
                      {account.adminId === session?.adminId && <MeTag>나</MeTag>}
                    </NameCell>
                  </Cell>
                  <Cell>
                    <LoginId>{account.loginId}</LoginId>
                  </Cell>
                  <Cell>
                    <AccountRoleBadge role={account.role} />
                  </Cell>
                  <Cell>
                    <CreatedAt>{formatAccountDate(account.createdAt)}</CreatedAt>
                  </Cell>
                  {canManage && (
                    <Cell>
                      <Actions>
                        <RowButton
                          type="button"
                          tone="default"
                          disabled
                          title={NOT_READY}
                          aria-label={`${account.name} 수정`}
                        >
                          수정
                        </RowButton>
                        <RowButton
                          type="button"
                          tone="danger"
                          disabled
                          title={NOT_READY}
                          aria-label={`${account.name} 삭제`}
                        >
                          삭제
                        </RowButton>
                      </Actions>
                    </Cell>
                  )}
                </Row>
              ))}
            </tbody>
          </Table>
        </TableScroll>
      )}
    </Page>
  );
};
