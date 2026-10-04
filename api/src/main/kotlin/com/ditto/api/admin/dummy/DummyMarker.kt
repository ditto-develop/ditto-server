package com.ditto.api.admin.dummy

/** 더미 회원을 가려내는 닉네임 접두어. 닉네임 규칙이 '-'를 막아 실회원은 이 접두어를 가질 수 없다. */
object DummyMarker {
    const val NICKNAME_PREFIX = "dummy-"

    fun isDummy(nickname: String): Boolean = nickname.startsWith(NICKNAME_PREFIX)
}
