import { canUploadTo } from "./uploadPermission";

const HOST_ID = 10234;
const GUEST_ID = 20567;

describe("canUploadTo", () => {
  it("모두 올릴 수 있는 방이면 참여자도 올린다", () => {
    expect(canUploadTo({ uploadPolicy: "everyone", hostId: HOST_ID }, GUEST_ID)).toBe(true);
  });

  it("방장만 올리는 방이면 방장만 올린다", () => {
    expect(canUploadTo({ uploadPolicy: "host", hostId: HOST_ID }, HOST_ID)).toBe(true);
    expect(canUploadTo({ uploadPolicy: "host", hostId: HOST_ID }, GUEST_ID)).toBe(false);
  });
});
