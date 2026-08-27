package com.sssok.application.port.out;

import java.io.OutputStream;

// 실패 시 지금까지 쓴 조각을 스토리지에서 정리(abort)할 수 있는 출력 스트림.
// close()는 정상 완료를, abort()는 실패했을 때 완료되지 않은 업로드가 스토리지에 남지 않도록 정리한다.
// 둘 중 하나만 호출해야 한다 — abort() 이후 close()를 부르면 이미 정리된 업로드를 완료 처리하려다 실패한다.
public abstract class AbortableOutputStream extends OutputStream {

    public abstract void abort();
}
