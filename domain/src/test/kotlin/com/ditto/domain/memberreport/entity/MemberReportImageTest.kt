package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MemberReportImageTest : FreeSpec(
    {
        "attachAll" - {
            "첨부 순서대로 이미지를 생성한다" {
                val images = MemberReportImage.attachAll(
                    memberReportId = 1L,
                    objectKeys = listOf("user-reports/1/a", "user-reports/1/b"),
                )

                images.size shouldBe 2
                images[0].objectKey shouldBe "user-reports/1/a"
                images[0].displayOrder shouldBe 0
                images[1].displayOrder shouldBe 1
            }

            "최대 장수를 초과하면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    MemberReportImage.attachAll(
                        memberReportId = 1L,
                        objectKeys = List(4) { "user-reports/1/key-$it" },
                    )
                }

                exception.errorCode shouldBe ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED
            }

            "같은 키를 중복 첨부하면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    MemberReportImage.attachAll(
                        memberReportId = 1L,
                        objectKeys = listOf("user-reports/1/a", "user-reports/1/a"),
                    )
                }

                exception.errorCode shouldBe ErrorCode.INVALID_REPORT_IMAGE_KEY
            }

            "빈 목록이면 빈 리스트를 반환한다" {
                MemberReportImage.attachAll(memberReportId = 1L, objectKeys = emptyList()) shouldBe emptyList()
            }
        }
    },
)
