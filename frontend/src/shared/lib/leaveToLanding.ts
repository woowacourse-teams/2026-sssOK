import { ROUTES } from "@/shared/config";

/** 랜딩은 SPA 밖에 있어서 라우터로 갈 수 없다. 지금 기록을 바꿔 끼워 뒤로 가기로 돌아오지 않게 한다. */
export const leaveToLanding = (): void => window.location.replace(ROUTES.landing);
