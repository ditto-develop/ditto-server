package com.ditto.api.support

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/** 순수 함수라 통합 테스트가 아닌 단위 테스트로 둔다. */
class RunCatchingExceptionsTest : FreeSpec({

    "성공하면 결과를 값으로 담는다" {
        val result = runCatchingExceptions { "ok" }

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe "ok"
    }

    // 배치 루프가 항목 하나의 실패를 흡수하고 다음으로 넘어가려면 실패가 값이어야 한다.
    "Exception 은 실패로 담아 호출부로 돌려준다" {
        val result = runCatchingExceptions { throw IllegalStateException("한 항목 실패") }

        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldBe "한 항목 실패"
    }

    // 이 함수가 존재하는 이유. runCatching 은 Throwable 을 잡으므로 OutOfMemoryError 까지 로그 한 줄로
    // 묻히고, 이미 불안정한 JVM 에서 다음 항목 처리를 계속 시도하게 된다.
    // onFailure 안의 throw 가 Result 로 감싸이지 않고 호출부까지 전파되는지도 이 단언이 확인한다.
    "Error 는 삼키지 않고 그대로 올린다" {
        shouldThrow<OutOfMemoryError> {
            runCatchingExceptions { throw OutOfMemoryError("치명 오류") }
        }.message shouldBe "치명 오류"
    }
})
