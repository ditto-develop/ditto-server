package com.ditto.domain.chat.entity

import com.ditto.domain.chat.ChatVoteFixture
import com.ditto.domain.chat.repository.ChatVoteOptionRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ChatVoteOptionTest(
    private val chatVoteOptionRepository: ChatVoteOptionRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "선택지 생성" - {
        "when: place 팩토리로 만들면" - {
            "then: PLACE 전용 필드가 채워지고 meetAt 은 비어 있다" {
                val option = chatVoteOptionRepository.save(ChatVoteFixture.place(voteId = 1L))

                option.optionType shouldBe ChatVoteOptionType.PLACE
                option.label shouldBe "성수 카페거리"
                option.meetAt shouldBe null
            }
        }

        "when: time 팩토리로 만들면" - {
            "then: meetAt 만 채워지고 초 이하가 잘린다 — 중복 판정이 분 단위라서다" {
                val option = chatVoteOptionRepository.save(
                    ChatVoteFixture.time(voteId = 1L, meetAt = LocalDateTime.of(2026, 8, 29, 19, 0, 42, 999)),
                )

                option.optionType shouldBe ChatVoteOptionType.TIME
                option.label shouldBe null
                option.meetAt shouldBe LocalDateTime.of(2026, 8, 29, 19, 0)
            }
        }
    }

    "중복 금지" - {
        "given: 같은 투표에 같은 상호명 PLACE 가 있을 때" - {
            "when: 다시 넣으면" - {
                "then: chat_vote_option_uk_1 충돌로 예외가 발생한다" {
                    chatVoteOptionRepository.save(ChatVoteFixture.place(voteId = 1L, label = "성수 카페거리"))

                    shouldThrow<Exception> {
                        chatVoteOptionRepository.saveAndFlush(
                            ChatVoteFixture.place(voteId = 1L, label = "성수 카페거리"),
                        )
                    }
                }
            }
        }

        "given: 같은 투표에 같은 일시 TIME 이 있을 때" - {
            "when: 초만 다른 일시를 넣으면" - {
                "then: 분 단위로 잘려 chat_vote_option_uk_2 충돌로 예외가 발생한다" {
                    val meetAt = LocalDateTime.of(2026, 8, 29, 19, 0)
                    chatVoteOptionRepository.save(ChatVoteFixture.time(voteId = 1L, meetAt = meetAt))

                    shouldThrow<Exception> {
                        chatVoteOptionRepository.saveAndFlush(
                            ChatVoteFixture.time(voteId = 1L, meetAt = meetAt.plusSeconds(30)),
                        )
                    }
                }
            }
        }

        "given: 다른 투표라면" - {
            "when: 같은 상호명·같은 일시를 넣어도" - {
                "then: 제약에 걸리지 않는다 — 중복 판정은 투표 단위다" {
                    chatVoteOptionRepository.save(ChatVoteFixture.place(voteId = 1L, label = "성수 카페거리"))
                    chatVoteOptionRepository.save(ChatVoteFixture.time(voteId = 1L))

                    chatVoteOptionRepository.saveAndFlush(ChatVoteFixture.place(voteId = 2L, label = "성수 카페거리"))
                    chatVoteOptionRepository.saveAndFlush(ChatVoteFixture.time(voteId = 2L))

                    chatVoteOptionRepository.findAll().size shouldBe 4
                }
            }
        }
    }

    "hasSameLabel — 상호명 중복 판정" - {
        "then: 앞뒤 공백과 대소문자를 무시하고 같다고 본다" {
            val option = ChatVoteFixture.place(voteId = 1L, label = "GS25 성수점")

            option.hasSameLabel(" gs25 성수점 ") shouldBe true
            option.hasSameLabel("GS25 성수2점") shouldBe false
        }

        "then: TIME 선택지는 label 이 없어 항상 false 다" {
            ChatVoteFixture.time(voteId = 1L).hasSameLabel("아무거나") shouldBe false
        }
    }

    "입력 순 조회" - {
        "then: id 오름차순 = 입력 순으로 돌려준다 (동표 노출 순서의 근거)" {
            val first = chatVoteOptionRepository.save(ChatVoteFixture.place(voteId = 1L, label = "성수"))
            val second = chatVoteOptionRepository.save(ChatVoteFixture.place(voteId = 1L, label = "강남"))
            val third = chatVoteOptionRepository.save(ChatVoteFixture.time(voteId = 1L))

            chatVoteOptionRepository.findAllByVoteIdOrderByIdAsc(1L).map { it.id } shouldBe
                listOf(first.id, second.id, third.id)
        }
    }
})
