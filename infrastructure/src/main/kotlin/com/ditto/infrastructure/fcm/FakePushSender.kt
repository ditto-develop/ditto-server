package com.ditto.infrastructure.fcm

import io.github.oshai.kotlinlogging.KotlinLogging

/** 로컬·테스트용 — 자격증명 없이 뜨고, 보낸 셈 치고 로그만 남긴다. */
class FakePushSender : PushSender {

    override fun send(message: PushMessage, onDeadTokens: (List<String>) -> Unit) {
        logger.info { "[FakePush] tokens=${message.tokens.size}개, title=${message.title}, data=${message.data}" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
