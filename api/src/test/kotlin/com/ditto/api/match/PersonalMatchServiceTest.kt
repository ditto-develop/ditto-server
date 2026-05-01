package com.ditto.api.match

import com.ditto.api.match.service.PersonalMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class PersonalMatchServiceTest(
    private val personalMatchService: PersonalMatchService,
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "1:1 매칭 요청 목록 조회" - {

        "given: 보낸 요청과 받은 요청이 모두 존재할 때" - {
            "when: 퀴즈셋 ID로 조회하면" - {
                "then: 보낸 목록과 받은 목록을 분리하여 반환한다" {
                    val requesterId = 1L
                    val quizSetId = 10L

                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = requesterId, receiverId = 2L, quizSetId = quizSetId)
                    )
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = 3L, receiverId = requesterId, quizSetId = quizSetId)
                    )

                    val result = personalMatchService.getPersonalMatches(requesterId, quizSetId)

                    result.sent.size shouldBe 1
                    result.sent[0].requesterId shouldBe requesterId
                    result.received.size shouldBe 1
                    result.received[0].receiverId shouldBe requesterId
                }
            }
        }

        "given: 다른 퀴즈셋의 요청이 있을 때" - {
            "when: 특정 퀴즈셋 ID로 조회하면" - {
                "then: 해당 퀴즈셋의 요청만 반환한다" {
                    val requesterId = 1L
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = requesterId, receiverId = 2L, quizSetId = 10L)
                    )
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = requesterId, receiverId = 3L, quizSetId = 20L)
                    )

                    val result = personalMatchService.getPersonalMatches(requesterId, 10L)

                    result.sent.size shouldBe 1
                    result.sent[0].quizSetId shouldBe 10L
                }
            }
        }
    }

    "1:1 매칭 요청 생성" - {

        "given: 정상적인 두 사용자가" - {
            "when: 매칭을 요청하면" - {
                "then: PENDING 상태의 매칭이 생성된다" {
                    val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

                    val result = personalMatchService.requestMatch(requesterId = 1L, request = request)

                    result.requesterId shouldBe 1L
                    result.receiverId shouldBe 2L
                    result.quizSetId shouldBe 10L
                    result.status shouldBe PersonalMatchStatus.PENDING
                }
            }
        }

        "given: 본인에게 매칭을 요청하면" - {
            "when: requesterId == receiverId 일 때" - {
                "then: CANNOT_REQUEST_SELF 예외가 발생한다" {
                    val request = PersonalMatchRequest(receiverId = 1L, quizSetId = 10L)

                    shouldThrow<WarnException> {
                        personalMatchService.requestMatch(requesterId = 1L, request = request)
                    }.errorCode shouldBe ErrorCode.CANNOT_REQUEST_SELF
                }
            }
        }

        "given: 이미 PENDING 상태의 매칭 요청이 존재할 때" - {
            "when: 동일한 두 사용자가 다시 요청하면" - {
                "then: MATCH_REQUEST_ALREADY_EXISTS 예외가 발생한다" {
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
                    )
                    val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

                    shouldThrow<WarnException> {
                        personalMatchService.requestMatch(requesterId = 1L, request = request)
                    }.errorCode shouldBe ErrorCode.MATCH_REQUEST_ALREADY_EXISTS
                }
            }
        }

        "given: 역방향 요청이 이미 PENDING 상태로 존재할 때" - {
            "when: B→A 요청이 있는 상태에서 A→B 요청하면" - {
                "then: MATCH_REQUEST_ALREADY_EXISTS 예외가 발생한다" {
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = 10L)
                    )
                    val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

                    shouldThrow<WarnException> {
                        personalMatchService.requestMatch(requesterId = 1L, request = request)
                    }.errorCode shouldBe ErrorCode.MATCH_REQUEST_ALREADY_EXISTS
                }
            }
        }

        "given: 이미 ACCEPTED 상태의 매칭이 존재할 때" - {
            "when: 동일한 두 사용자가 다시 요청하면" - {
                "then: ALREADY_MATCHED 예외가 발생한다" {
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(
                            requesterId = 1L,
                            receiverId = 2L,
                            quizSetId = 10L,
                            status = PersonalMatchStatus.ACCEPTED,
                        )
                    )
                    val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

                    shouldThrow<WarnException> {
                        personalMatchService.requestMatch(requesterId = 1L, request = request)
                    }.errorCode shouldBe ErrorCode.ALREADY_MATCHED
                }
            }
        }
    }

    "1:1 매칭 수락" - {

        "given: PENDING 상태의 매칭이 존재하고 수신자가 요청할 때" - {
            "when: 매칭을 수락하면" - {
                "then: 상태가 ACCEPTED로 변경된다" {
                    val match = personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
                    )

                    val result = personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)

                    result.status shouldBe PersonalMatchStatus.ACCEPTED
                    result.respondedAt shouldBe result.respondedAt  // not null
                }
            }
        }

        "given: 수신자가 아닌 사용자가" - {
            "when: 매칭을 수락하려 하면" - {
                "then: FORBIDDEN 예외가 발생한다" {
                    val match = personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
                    )

                    shouldThrow<WarnException> {
                        personalMatchService.acceptMatch(memberId = 99L, matchId = match.id)
                    }.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }

        "given: 존재하지 않는 매칭 ID로" - {
            "when: 수락을 요청하면" - {
                "then: NOT_FOUND 예외가 발생한다" {
                    shouldThrow<WarnException> {
                        personalMatchService.acceptMatch(memberId = 1L, matchId = 9999L)
                    }.errorCode shouldBe ErrorCode.NOT_FOUND
                }
            }
        }
    }

    "1:1 매칭 거절" - {

        "given: PENDING 상태의 매칭이 존재하고 수신자가 요청할 때" - {
            "when: 매칭을 거절하면" - {
                "then: 상태가 REJECTED로 변경된다" {
                    val match = personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
                    )

                    val result = personalMatchService.rejectMatch(memberId = 2L, matchId = match.id)

                    result.status shouldBe PersonalMatchStatus.REJECTED
                }
            }
        }

        "given: ACCEPTED 상태의 매칭을" - {
            "when: 거절하려 하면" - {
                "then: INVALID_STATUS_TRANSITION 예외가 발생한다" {
                    val match = personalMatchRepository.save(
                        PersonalMatchFixture.create(
                            requesterId = 1L,
                            receiverId = 2L,
                            quizSetId = 10L,
                            status = PersonalMatchStatus.ACCEPTED,
                        )
                    )

                    shouldThrow<WarnException> {
                        personalMatchService.rejectMatch(memberId = 2L, matchId = match.id)
                    }.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }
    }
})
