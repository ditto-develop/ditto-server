package com.ditto.admin.auth

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class AdminPrincipalTest : FreeSpec({
    "이름과 이메일이 모두 있으면 '이름(이메일)'" {
        AdminPrincipal(1L, "관리자", "admin@ditto.pics").displayName shouldBe "관리자(admin@ditto.pics)"
    }
    "이름만 있으면 이름" {
        AdminPrincipal(1L, "관리자", null).displayName shouldBe "관리자"
    }
    "이메일만 있으면 이메일" {
        AdminPrincipal(1L, null, "admin@ditto.pics").displayName shouldBe "admin@ditto.pics"
    }
    "둘 다 없으면 회원 ID" {
        AdminPrincipal(7L, null, null).displayName shouldBe "회원 #7"
    }
})
