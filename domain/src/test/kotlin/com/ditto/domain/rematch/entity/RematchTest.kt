package com.ditto.domain.rematch.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource

class RematchTest(
    private val rematchRepository: RematchRepository,
    transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 7, 26, 12, 0)

    "페어 정규화" - {
        "given: memberIdA=5, memberIdB=2 처럼 앞 회원이 더 클 때" - {
            "when: 저장하면" - {
                "then: memberId1=min(2), memberId2=max(5) 로 정규화되어 저장된다" {
                    val pair = rematchRepository.save(
                        RematchFixture.create(memberIdA = 5L, memberIdB = 2L),
                    )

                    pair.id shouldNotBe 0L
                    pair.memberId1 shouldBe 2L
                    pair.memberId2 shouldBe 5L
                    pair.status shouldBe RematchStatus.WAITING
                    pair.counterpartOf(2L) shouldBe 5L
                    pair.counterpartOf(5L) shouldBe 2L
                }
            }
        }

        "given: 같은 회원 ID 두 개로" - {
            "when: 쌍을 만들면" - {
                "then: 자기 자신과의 쌍이라 거부된다" {
                    val exception = shouldThrow<WarnException> {
                        RematchFixture.create(memberIdA = 1L, memberIdB = 1L)
                    }

                    exception.errorCode shouldBe ErrorCode.SELF_REMATCH_NOT_ALLOWED
                }
            }
        }

        "given: 주간 키는 OperationWeek 가 월요일을 강제하므로" - {
            "when: 쌍을 저장하면" - {
                "then: weekStartedOn 과 operationWeek 가 같은 주를 가리킨다" {
                    val pair = rematchRepository.save(RematchFixture.create())

                    pair.weekStartedOn shouldBe RematchFixture.WEEK.startedOn
                    pair.operationWeek shouldBe RematchFixture.WEEK
                }
            }
        }
    }

    "쌍 유일성" - {
        "given: 소스 그룹 1의 (1,2) 쌍이 이미 있을 때" - {
            "when: 순서만 바꾼 (2,1) 쌍을 같은 소스 그룹에 저장하면" - {
                "then: 동일한 UK (sourceGroupMatchId, memberId1, memberId2) 충돌로 예외가 발생한다" {
                    rematchRepository.save(
                        RematchFixture.create(sourceGroupMatchId = 1L, memberIdA = 1L, memberIdB = 2L),
                    )

                    shouldThrow<Exception> {
                        rematchRepository.saveAndFlush(
                            RematchFixture.create(sourceGroupMatchId = 1L, memberIdA = 2L, memberIdB = 1L),
                        )
                    }
                }
            }

            "when: 같은 weekStartedOn 의 같은 쌍을 다른 소스 그룹에 저장하면" - {
                "then: 소스 그룹별로 각각 생성된다" {
                    rematchRepository.save(
                        RematchFixture.create(sourceGroupMatchId = 1L, memberIdA = 1L, memberIdB = 2L),
                    )

                    val saved = rematchRepository.saveAndFlush(
                        RematchFixture.create(sourceGroupMatchId = 2L, memberIdA = 1L, memberIdB = 2L),
                    )

                    saved.id shouldNotBe 0L
                }
            }
        }
    }

    "재매칭 선택 제출" - {
        "given: 양쪽 모두 미응답인 쌍에서" - {
            "when: 한쪽만 true 로 제출하면" - {
                "then: WAITING 을 유지하고 본인 선택만 확정된다" {
                    val pair = RematchFixture.create(memberIdA = 1L, memberIdB = 2L)

                    pair.submitWants(1L, true, now)

                    pair.status shouldBe RematchStatus.WAITING
                    pair.wantsOf(1L) shouldBe true
                    pair.wantsOf(2L) shouldBe null
                    pair.matchedAt shouldBe null
                }
            }

            "when: 양쪽 모두 true 로 제출하면" - {
                "then: MATCHED 로 전이하고 matchedAt 이 기록된다" {
                    val pair = RematchFixture.create(memberIdA = 1L, memberIdB = 2L)

                    pair.submitWants(1L, true, now)
                    pair.submitWants(2L, true, now)

                    pair.status shouldBe RematchStatus.MATCHED
                    pair.matchedAt shouldBe now
                }
            }

            "when: 한쪽이 false 로 응답을 마치면" - {
                "then: CANCELLED 로 전이하고 matchedAt 은 남지 않는다" {
                    val pair = RematchFixture.create(memberIdA = 1L, memberIdB = 2L)

                    pair.submitWants(1L, true, now)
                    pair.submitWants(2L, false, now)

                    pair.status shouldBe RematchStatus.CANCELLED
                    pair.matchedAt shouldBe null
                }
            }

            "when: 쌍에 속하지 않은 회원이 제출하면" - {
                "then: 쌍에 속하지 않은 회원이라 거부된다" {
                    val pair = RematchFixture.create(memberIdA = 1L, memberIdB = 2L)

                    val exception = shouldThrow<WarnException> { pair.submitWants(99L, true, now) }

                    exception.errorCode shouldBe ErrorCode.NOT_REMATCH_PAIR_MEMBER
                }
            }
        }

        "given: 이미 선택을 확정한 회원이" - {
            "when: 같은 쌍에 다시 제출하면" - {
                "then: 제출은 최종이라 거부된다" {
                    val pair = RematchFixture.create(memberIdA = 1L, memberIdB = 2L)
                    pair.submitWants(1L, true, now)

                    val exception = shouldThrow<WarnException> { pair.submitWants(1L, false, now) }

                    exception.errorCode shouldBe ErrorCode.REMATCH_ALREADY_SUBMITTED
                }
            }
        }

        "given: 이미 CANCELLED 로 확정된 쌍에" - {
            "when: 다시 제출하면" - {
                "then: 성사 여부가 확정된 쌍이라 거부된다" {
                    val pair = RematchFixture.create(memberIdA = 1L, memberIdB = 2L)
                    pair.submitWants(1L, false, now)
                    pair.submitWants(2L, true, now)

                    val exception = shouldThrow<WarnException> { pair.submitWants(2L, true, now) }

                    exception.errorCode shouldBe ErrorCode.REMATCH_PAIR_ALREADY_SETTLED
                }
            }
        }
    }

    // 이 테스트의 DB는 H2다. 잠금이 판정을 직렬화한다는 계약은 검증하지만 InnoDB의 잠금 시맨틱까지
    // 보장하지는 않는다 — 프로덕션 MySQL 검증 범위는 ADR 0011 참조.
    "동시 제출" - {
        "given: 양쪽 회원이 같은 쌍에" - {
            "when: 동시에 true 를 제출하면" - {
                "then: 행 잠금이 판정을 직렬화해 상호 선택을 놓치지 않고 MATCHED 로 수렴한다" {
                    val pair = rematchRepository.save(
                        RematchFixture.create(memberIdA = 1L, memberIdB = 2L),
                    )
                    val transactionTemplate = TransactionTemplate(transactionManager)
                    val startLatch = CountDownLatch(1)
                    val executor = Executors.newFixedThreadPool(2)

                    val submissions = listOf(1L, 2L).map { memberId ->
                        executor.submit {
                            startLatch.await()
                            transactionTemplate.execute {
                                val locked = rematchRepository.findWithLockById(pair.id)
                                    ?: throw IllegalStateException("쌍이 존재해야 한다")
                                locked.submitWants(memberId, true, now)
                            }
                        }
                    }
                    startLatch.countDown()
                    submissions.forEach { it.get() }
                    executor.shutdown()

                    val settled = rematchRepository.findById(pair.id).orElseThrow()
                    settled.status shouldBe RematchStatus.MATCHED
                    settled.matchedAt shouldNotBe null
                }
            }
        }
    }
})
