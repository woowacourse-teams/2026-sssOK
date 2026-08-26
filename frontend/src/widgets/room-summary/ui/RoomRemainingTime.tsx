import { useEffect, useState } from "react";
import { HiOutlineClock } from "react-icons/hi2";

import { RemainingTimeText } from "./RoomSummary.styles";

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;

export const formatRemainingTime = (expiresAt: string, now: number = Date.now()) => {
  const remaining = new Date(expiresAt).getTime() - now;

  if (Number.isNaN(remaining) || remaining <= 0) return "만료됨";

  const totalMinutes = Math.floor(remaining / MINUTE);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours > 0) return `${hours}시간 ${minutes}분`;
  if (totalMinutes > 0) return `${totalMinutes}분`;

  return `${Math.floor(remaining / SECOND)}초`;
};

interface RoomRemainingTimeProps {
  expiresAt: string;
}

export const RoomRemainingTime = ({ expiresAt }: RoomRemainingTimeProps) => {
  const [now, setNow] = useState(() => Date.now());
  const remaining = new Date(expiresAt).getTime() - now;
  const isUrgent = Number.isNaN(remaining) || remaining <= HOUR;

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(Date.now()), SECOND);

    return () => window.clearInterval(intervalId);
  }, []);

  return (
    <RemainingTimeText dateTime={expiresAt} $urgent={isUrgent}>
      <HiOutlineClock aria-hidden="true" />
      {formatRemainingTime(expiresAt, now)}
    </RemainingTimeText>
  );
};
