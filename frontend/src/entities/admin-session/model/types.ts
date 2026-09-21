export interface AdminLoginRequest {
  loginId: string;
  password: string;
}

export interface AdminSession {
  accessToken: string;
  adminId: number;
  loginId: string;
  name: string;
  /**
   * 지금은 `SUPER_ADMIN`·`ADMIN` 두 값만 온다. 그래도 유니온으로 좁히지 않는다 —
   * 서버는 역할과 권한을 분리해 두어 역할이 늘어날 수 있고, 두 값만 있다고 가정해
   * 분기해 두면 역할이 하나 추가될 때 화면이 조용히 어긋난다. 저장만 하고 판단에는 쓰지 않는다.
   */
  role: string;
  expiresAt: string;
}
