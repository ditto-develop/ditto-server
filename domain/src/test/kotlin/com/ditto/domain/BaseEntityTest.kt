package com.ditto.domain

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

class BaseEntityTest(
    private val memberRepository: MemberRepository,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "BaseEntity Auditing" - {
        "createdAt은 엔티티 저장 시 자동 설정된다" {
            val member = memberRepository.save(Member(nickname = "테스트"))

            member.createdAt shouldNotBe null
        }

        "updatedAt은 엔티티 수정 시 자동 갱신된다" {
            val memberId = transactionTemplate.execute {
                val member = memberRepository.save(Member(nickname = "테스트"))
                entityManager.flush()
                entityManager.clear()
                member.id
            }!!

            val saved = memberRepository.findById(memberId).get()
            val originalUpdatedAt = saved.updatedAt

            Thread.sleep(50)

            transactionTemplate.execute {
                val member = memberRepository.findById(memberId).get()
                member.nickname = "수정됨"
                entityManager.flush()
            }

            val updated = memberRepository.findById(memberId).get()
            updated.updatedAt shouldNotBe originalUpdatedAt
        }

        "createdAt은 엔티티 수정 시 변경되지 않는다" {
            val memberId = transactionTemplate.execute {
                val member = memberRepository.save(Member(nickname = "테스트"))
                entityManager.flush()
                entityManager.clear()
                member.id
            }!!

            val saved = memberRepository.findById(memberId).get()
            val originalCreatedAt = saved.createdAt

            Thread.sleep(50)

            transactionTemplate.execute {
                val member = memberRepository.findById(memberId).get()
                member.nickname = "수정됨"
                entityManager.flush()
            }

            val updated = memberRepository.findById(memberId).get()
            updated.createdAt shouldBe originalCreatedAt
        }
    }
})
