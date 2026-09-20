/**
 * `createdAt`(ISO 8601, UTC) 을 한국 시간 `MM-DD HH:mm` 으로 바꾼다.
 *
 * 관리자는 한국에서 본다. UTC 를 그대로 보여주면 "오전에 들어온 의견" 이 전날 밤으로 보인다.
 * 브라우저 시간대에 맡기지 않고 서울로 못박는 건, 관리자가 해외에서 열어도 팀이 이야기하는
 * 시각과 같아야 하기 때문이다.
 */
const formatter = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  // h23 으로 못박지 않으면 엔진에 따라 자정이 `24:00` 으로 나온다.
  hourCycle: "h23",
});

export const formatFeedbackTime = (createdAt: string): string | null => {
  const time = Date.parse(createdAt);

  // 읽지 못한 시각은 비워 둔다. `Invalid Date` 를 그대로 찍는 것보다 낫다.
  if (Number.isNaN(time)) return null;

  const parts = formatter.formatToParts(new Date(time));
  const read = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? "";

  return `${read("month")}-${read("day")} ${read("hour")}:${read("minute")}`;
};
