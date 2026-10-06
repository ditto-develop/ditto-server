package com.ditto.api.admin.qa

/** 모두에게 원하면 더미끼리도 재매칭 방이 여럿 생겨, 실회원에게만 원하는 첫 항목을 기본으로 둔다. */
enum class QaBulkRematchChoice(val label: String) {
    REAL_MEMBERS_ONLY("실회원에게만 재매칭 원함"),
    EVERYONE("모두에게 재매칭 원함"),
    NOBODY("아무에게도 재매칭 원하지 않음"),
    ;

    fun wantsToward(targetIsDummy: Boolean): Boolean =
        when (this) {
            REAL_MEMBERS_ONLY -> !targetIsDummy
            EVERYONE -> true
            NOBODY -> false
        }
}
