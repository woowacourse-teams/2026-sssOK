import styled from "@emotion/styled";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { isApiError } from "@/shared/api";
import { colors, spacing, typography } from "@/shared/styles/tokens";
import { HiBell, HiPhoto } from "react-icons/hi2";
import { DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import { getNotificationSetting, requestPhotos, updateNotificationSetting } from "./api";

interface Props {
  roomId: number;
  userId: number;
  accessToken: string;
  onRequested: () => void;
  onRequestLimited: () => void;
}

export const RoomNotificationMenu = ({
  roomId,
  userId,
  accessToken,
  onRequested,
  onRequestLimited,
}: Props) => {
  const queryClient = useQueryClient();
  const queryKey = ["room-notification-setting", roomId, userId] as const;
  // 캐시는 이용자 ID로 나누고 인증 토큰은 키에 넣지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  const setting = useQuery({
    queryKey,
    queryFn: () => getNotificationSetting(roomId, accessToken),
  });
  const update = useMutation({
    mutationFn: (enabled: boolean) => updateNotificationSetting(roomId, accessToken, enabled),
    onSuccess: (data) => queryClient.setQueryData(queryKey, data),
  });
  const request = useMutation({
    mutationFn: () => requestPhotos(roomId, accessToken),
    onSuccess: onRequested,
    onError: (error) => {
      if (isApiError(error) && error.status === 429) onRequestLimited();
    },
  });
  const enabled = setting.data?.enabled;

  return (
    <>
      <ToggleItem
        $muted={enabled === false}
        icon={<HiBell />}
        role="switch"
        aria-label="방 알림"
        aria-checked={enabled ?? false}
        disabled={enabled === undefined || update.isPending}
        onClick={() => {
          if (enabled !== undefined) update.mutate(!enabled);
        }}
      >
        방 알림
        <ToggleTrack $checked={enabled ?? false} aria-hidden="true" />
      </ToggleItem>
      {setting.isPending && <Notice role="status">알림 설정을 불러오는 중이에요.</Notice>}
      {setting.isError && (
        <Notice role="alert">
          알림 설정을 불러오지 못했어요.{" "}
          <button type="button" onClick={() => void setting.refetch()}>
            다시 시도
          </button>
        </Notice>
      )}
      {update.isError && (
        <Notice role="alert">알림 설정을 바꾸지 못했어요. 다시 시도해 주세요.</Notice>
      )}
      <DropdownMenuItem
        icon={<HiPhoto />}
        disabled={enabled !== true || update.isPending || request.isPending}
        onClick={() => request.mutate()}
      >
        {request.isPending ? "보내는 중…" : "사진 요청하기"}
      </DropdownMenuItem>
      {request.isError && (
        <Notice role="alert">
          {isApiError(request.error) && request.error.status === 429
            ? "잠시 후 다시 사진을 요청해 주세요."
            : "사진 요청을 보내지 못했어요. 다시 시도해 주세요."}
        </Notice>
      )}
    </>
  );
};

const Notice = styled.p`
  padding: 4px ${spacing[12]};
  color: ${colors.textSecondary};
  ${typography.caption2}
`;

const ToggleItem = styled(DropdownMenuItem)<{ $muted: boolean }>`
  color: ${({ $muted }) => ($muted ? colors.textSecondary : colors.textStrong)};
  &:hover {
    color: ${({ $muted }) => ($muted ? colors.textSecondary : colors.primary)};
  }
  > span {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: ${spacing[12]};
    width: 100%;
  }
`;

const ToggleTrack = styled.span<{ $checked: boolean }>`
  display: block;
  flex: none;
  width: 32px;
  height: 20px;
  border-radius: 999px;
  background: ${({ $checked }) => ($checked ? colors.primary : colors.borderDefault)};
  &::before {
    content: "";
    display: block;
    width: 14px;
    height: 14px;
    margin: 3px;
    border-radius: 50%;
    background: ${colors.backgroundDefault};
    transform: translateX(${({ $checked }) => ($checked ? "12px" : "0")});
  }
`;
