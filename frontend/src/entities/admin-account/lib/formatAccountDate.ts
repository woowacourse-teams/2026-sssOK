/**
 * `createdAt`(ISO 8601, UTC) 을 한국 날짜 `YYYY-MM-DD` 로 바꾼다.
 *
 * 서울로 못박는 이유는 의견 시각(`formatFeedbackTime`)과 같다. UTC 날짜를 그대로 쓰면
 * 오전 9시 전에 만든 계정이 하루 전에 만든 것으로 보인다.
 */
const formatter = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export const formatAccountDate = (createdAt: string): string | null => {
  const time = Date.parse(createdAt);

  // 읽지 못한 시각은 비워 둔다. `Invalid Date` 를 그대로 찍는 것보다 낫다.
  if (Number.isNaN(time)) return null;

  const parts = formatter.formatToParts(new Date(time));
  const read = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? "";

  return `${read("year")}-${read("month")}-${read("day")}`;
};
