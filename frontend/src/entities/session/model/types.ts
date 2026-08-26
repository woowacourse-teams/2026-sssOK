export interface CreateAnonymousRequest {
  nickname: string;
}

export interface AnonymousSession {
  accessToken: string;
  userId: number;
  nickname: string;
  expiresAt: string;
}
