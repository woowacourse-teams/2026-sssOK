package com.ssssok.cookies

import android.webkit.CookieManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class CookiePersistenceModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("SssokCookiePersistence")

    // I/O는 JS/UI 스레드 밖에서 수행한다. 쿠키 값은 JS로 반환하지 않는다.
    AsyncFunction("flush") {
      CookieManager.getInstance().flush()
    }
  }
}
