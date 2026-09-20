import { http, HttpResponse } from "msw";

import type { AdminFeedback } from "@/entities/feedback";
import { API_BASE_URL } from "@/shared/config";

/** 서버 기본값과 맞춘다 (의견 목록 조회 명세). */
const DEFAULT_SIZE = 20;
const MAX_SIZE = 100;

const UA = {
  iPhone:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
  iPhoneOld:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
  iPhoneKakao:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.4.0",
  galaxy:
    "Mozilla/5.0 (Linux; Android 14; SM-S911N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
  galaxyTab:
    "Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
  mac: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
  iPad: "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
  windows:
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
  curl: "curl/8.4.0",
} as const;

interface Seed {
  content: string;
  roomId: number;
  roomName: string;
  memberId: number;
  nickname: string | null;
  userAgent: string | null;
  frontendVersion: string | null;
  /** KST. 최신순으로 적는다 — 아래로 갈수록 오래된 의견이다. */
  createdAt: string;
}

/**
 * 앞의 여덟 건은 디자인(`Admin Feedbacks · 전체`)에 그려진 그대로다.
 * 뒤는 커서 페이지네이션이 실제로 돌도록 채운 것이다 — 한 페이지(20건)를 넘겨야
 * `hasNext` 와 `nextCursor` 가 화면에서 검증된다.
 */
const SEEDS: Seed[] = [
  {
    content: "사진 30장 올리는데 5분 넘게 걸렸어요. 중간에 멈춘 줄 알고 몇 번이나 껐다 켰습니다.",
    roomId: 128,
    roomName: "제주도 여행",
    memberId: 871,
    nickname: "민수",
    userAgent: UA.iPhone,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-16T08:41:00+09:00",
  },
  {
    content: "다운로드 눌렀는데 zip이 안 열려요. 압축 파일이 손상되었다고 뜹니다.",
    roomId: 204,
    roomName: "동아리 MT",
    memberId: 1123,
    nickname: "지우",
    userAgent: UA.galaxy,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-16T08:12:00+09:00",
  },
  {
    content: "폴더를 만들었는데 다른 사람 화면에서는 안 보인대요. 새로고침하면 보인다고 하네요.",
    roomId: 91,
    roomName: "회사 워크샵",
    memberId: 640,
    nickname: "해니",
    userAgent: UA.mac,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-15T22:36:00+09:00",
  },
  {
    content:
      "방이 언제 사라지는지 좀 더 잘 보였으면 좋겠어요. 23시간 남았다는 게 작아서 못 봤습니다.",
    roomId: 312,
    roomName: "가족 여행",
    memberId: 1508,
    nickname: "도영",
    userAgent: UA.iPhoneOld,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-15T20:04:00+09:00",
  },
  {
    content: "링크 공유할 때 카톡에서 미리보기가 안 떠요.",
    roomId: 77,
    roomName: "졸업 사진",
    memberId: 402,
    nickname: "수빈",
    userAgent: UA.iPhoneKakao,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-15T18:47:00+09:00",
  },
  {
    content: "동영상을 올렸는데 갤러리에서 재생이 안 됩니다. 썸네일만 보이고 눌러도 반응이 없어요.",
    roomId: 156,
    roomName: "체육대회",
    memberId: 933,
    nickname: "가현",
    userAgent: UA.galaxyTab,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-15T15:19:00+09:00",
  },
  {
    content: "닉네임을 잘못 적었는데 바꿀 방법을 못 찾겠어요.",
    roomId: 245,
    roomName: "스터디 모임",
    memberId: 1290,
    nickname: "의찬",
    userAgent: UA.iPhoneOld,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-15T12:58:00+09:00",
  },
  {
    content: "사진 여러 장 고를 때 하나씩 눌러야 해서 불편해요.",
    roomId: 19,
    roomName: "부산 여행",
    memberId: 118,
    nickname: "윤돌",
    userAgent: UA.iPad,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-15T10:23:00+09:00",
  },

  {
    content: "업로드 중에 화면을 끄면 다시 처음부터 올라가요.",
    roomId: 88,
    roomName: "동창회",
    memberId: 501,
    nickname: "현우",
    userAgent: UA.galaxy,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-15T09:40:00+09:00",
  },
  {
    content: "사진 순서가 올린 순서랑 달라요.",
    roomId: 132,
    roomName: "베이킹 클래스",
    memberId: 722,
    nickname: "지민",
    userAgent: UA.windows,
    frontendVersion: "fe-v0.1.0",
    createdAt: "2026-09-15T08:05:00+09:00",
  },
  // 헤더 없이 들어온 의견. userAgent·frontendVersion 이 둘 다 null 이어도 목록이 멀쩡해야 한다.
  {
    content: "의견 남기는 곳을 찾기가 어려웠습니다.",
    roomId: 300,
    roomName: "테스트 방",
    memberId: 1601,
    nickname: null,
    userAgent: null,
    frontendVersion: null,
    createdAt: "2026-09-14T23:12:00+09:00",
  },
  {
    content: "QR로 들어왔는데 이름 입력 화면이 두 번 나왔어요.",
    roomId: 51,
    roomName: "결혼식",
    memberId: 205,
    nickname: "서연",
    userAgent: UA.iPhone,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-14T21:33:00+09:00",
  },
  {
    content: "사진을 지웠는데 목록에서 바로 안 사라져요.",
    roomId: 174,
    roomName: "캠핑",
    memberId: 889,
    nickname: "태윤",
    userAgent: UA.mac,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-14T19:50:00+09:00",
  },
  {
    content: "방 만들 때 기간을 더 길게 잡고 싶어요.",
    roomId: 66,
    roomName: "봄 소풍",
    memberId: 333,
    nickname: "나은",
    userAgent: UA.galaxy,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-14T17:02:00+09:00",
  },
  {
    content: "큰 동영상은 올리다가 실패했다고 떠요.",
    roomId: 291,
    roomName: "공연 뒤풀이",
    memberId: 1402,
    nickname: "준호",
    userAgent: UA.windows,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-14T14:48:00+09:00",
  },
  // 어느 규칙에도 걸리지 않는 UA. 기기·브라우저 자리가 비어도 나머지는 보여야 한다.
  {
    content: "API로 직접 올려봤는데 응답이 느려요.",
    roomId: 400,
    roomName: "내부 테스트",
    memberId: 1700,
    nickname: "봇",
    userAgent: UA.curl,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-14T11:20:00+09:00",
  },
  {
    content: "갤러리에서 위로 스크롤하면 가끔 맨 위로 튀어요.",
    roomId: 143,
    roomName: "제주 한달살기",
    memberId: 960,
    nickname: "예린",
    userAgent: UA.iPad,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-14T09:11:00+09:00",
  },
  {
    content: "사진을 확대했을 때 화질이 많이 깨져 보여요.",
    roomId: 210,
    roomName: "전시회",
    memberId: 1155,
    nickname: "동현",
    userAgent: UA.iPhone,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-13T22:40:00+09:00",
  },
  {
    content: "방 이름을 바꿨는데 공유 링크에는 예전 이름이 떠요.",
    roomId: 37,
    roomName: "연말 모임",
    memberId: 260,
    nickname: "채원",
    userAgent: UA.galaxyTab,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-13T20:15:00+09:00",
  },
  {
    content: "다운로드가 끝났는데 알림이 없어서 계속 기다렸어요.",
    roomId: 188,
    roomName: "워크샵 2일차",
    memberId: 1020,
    nickname: "시우",
    userAgent: UA.mac,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-13T18:33:00+09:00",
  },

  {
    content: "폴더 이름을 더 길게 쓰고 싶어요.",
    roomId: 77,
    roomName: "동아리 MT",
    memberId: 1019,
    nickname: "하준",
    userAgent: UA.galaxy,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-13T15:27:00+09:00",
  },
  {
    content: "사진 올린 사람 이름이 안 보여요.",
    roomId: 122,
    roomName: "봉사활동",
    memberId: 610,
    nickname: "유진",
    userAgent: UA.windows,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-13T13:04:00+09:00",
  },
  {
    content: "링크를 열면 가끔 흰 화면만 나옵니다.",
    roomId: 255,
    roomName: "생일 파티",
    memberId: 1340,
    nickname: "서준",
    userAgent: UA.iPhoneKakao,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-13T10:52:00+09:00",
  },
  {
    content: "선택한 사진만 따로 내려받고 싶어요.",
    roomId: 93,
    roomName: "MT 2차",
    memberId: 470,
    nickname: "민정",
    userAgent: UA.iPad,
    frontendVersion: "fe-v0.0.8",
    createdAt: "2026-09-12T23:18:00+09:00",
  },
  {
    content: "방에 들어간 사람이 몇 명인지 보고 싶어요.",
    roomId: 168,
    roomName: "축제 준비",
    memberId: 1088,
    nickname: "건우",
    userAgent: UA.mac,
    frontendVersion: "fe-v0.0.8",
    createdAt: "2026-09-12T21:06:00+09:00",
  },
  {
    content: "업로드 실패한 사진만 다시 올릴 수 있으면 좋겠어요.",
    roomId: 201,
    roomName: "여름 휴가",
    memberId: 1211,
    nickname: "소율",
    userAgent: UA.galaxy,
    frontendVersion: "fe-v0.0.9",
    createdAt: "2026-09-12T19:44:00+09:00",
  },
];

/** 최신순으로 적은 씨앗에 번호를 붙인다 — 위가 큰 번호여야 `feedbackId` 내림차순이 된다. */
const FEEDBACKS: AdminFeedback[] = SEEDS.map((seed, index) => ({
  feedbackId: SEEDS.length - index,
  ...seed,
  createdAt: new Date(seed.createdAt).toISOString(),
}));

const invalidParameter = (name: string) =>
  HttpResponse.json(
    { code: "INVALID_REQUEST_PARAMETER", message: `${name} 값의 형식이 올바르지 않습니다` },
    { status: 400 },
  );

/** 숫자가 아니면 null. 서버가 400 으로 돌려주는 자리다. */
const readNumber = (raw: string | null): number | null => {
  if (raw === null) return null;

  const value = Number(raw);

  return Number.isInteger(value) ? value : null;
};

export const feedbackHandlers = [
  http.get(`${API_BASE_URL}/admin/feedbacks`, ({ request }) => {
    // 관리자 토큰이 아니면 권한 판정 전에 토큰 단계에서 걸린다.
    if (!request.headers.get("Authorization")?.startsWith("Bearer ")) {
      return HttpResponse.json(
        { code: "UNAUTHORIZED", message: "인증이 필요합니다" },
        { status: 401 },
      );
    }

    const params = new URL(request.url).searchParams;

    const rawSize = params.get("size");
    const size = rawSize === null ? DEFAULT_SIZE : readNumber(rawSize);

    if (size === null || size < 1 || size > MAX_SIZE) return invalidParameter("size");

    const rawCursor = params.get("cursor");
    const cursor = rawCursor === null ? null : readNumber(rawCursor);

    if (rawCursor !== null && cursor === null) return invalidParameter("cursor");

    let start = 0;

    if (cursor !== null) {
      const index = FEEDBACKS.findIndex(({ feedbackId }) => feedbackId === cursor);

      // 없는 의견을 가리키는 커서는 400 이다 — 빈 목록으로 얼버무리지 않는다.
      if (index === -1) return invalidParameter("cursor");

      start = index + 1;
    }

    const page = FEEDBACKS.slice(start, start + size);
    const hasNext = start + size < FEEDBACKS.length;

    return HttpResponse.json({
      data: {
        feedbacks: page,
        nextCursor: hasNext ? (page[page.length - 1]?.feedbackId ?? null) : null,
        hasNext,
      },
    });
  }),
];
