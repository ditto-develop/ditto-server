package com.ditto.api.admin.qa

/** QA 콘솔의 리다이렉트 경로. 동작을 마치면 보던 섹션·방으로 돌아간다. */
object QaRoutes {
    const val CONSOLE_PATH = "/admin/qa"
    const val PERSONAL_SECTION = "redirect:$CONSOLE_PATH#personal"
    const val GROUP_SECTION = "redirect:$CONSOLE_PATH#group"
    const val ROOMS_SECTION = "redirect:$CONSOLE_PATH#rooms"

    fun room(roomId: Long): String = "redirect:$CONSOLE_PATH/rooms/$roomId"

    /** 콘솔 밖이나 다른 호스트로는 보내지 않는다. */
    fun backTo(returnTo: String): String =
        "redirect:" + (returnTo.takeIf { it.startsWith(CONSOLE_PATH) && !it.contains("//") } ?: CONSOLE_PATH)
}
