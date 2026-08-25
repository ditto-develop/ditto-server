package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatVoteFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class ChatVoteChoiceRepositoryTest(
    private val chatVoteChoiceRepository: ChatVoteChoiceRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "표 저장" - {
        "given: 같은 회원이 같은 선택지에 이미 표를 던졌을 때" - {
            "when: 또 던지면" - {
                "then: chat_vote_choice_uk_1 충돌로 예외가 발생한다" {
                    chatVoteChoiceRepository.save(ChatVoteFixture.choice(voteId = 1L, optionId = 5L, memberId = 1L))

                    shouldThrow<Exception> {
                        chatVoteChoiceRepository.saveAndFlush(
                            ChatVoteFixture.choice(voteId = 1L, optionId = 5L, memberId = 1L),
                        )
                    }
                }
            }
        }
    }

    "findAllByVoteId — 상세 조회·치환의 읽기 경로" - {
        "then: 그 투표의 표만 돌려준다" {
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(voteId = 1L, optionId = 5L, memberId = 1L))
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(voteId = 1L, optionId = 5L, memberId = 2L))
            chatVoteChoiceRepository.save(ChatVoteFixture.choice(voteId = 2L, optionId = 9L, memberId = 1L))

            chatVoteChoiceRepository.findAllByVoteId(1L).map { it.memberId }.toSet() shouldBe setOf(1L, 2L)
        }
    }
})
