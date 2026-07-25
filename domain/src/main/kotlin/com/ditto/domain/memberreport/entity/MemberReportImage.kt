package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

/** 신고에 첨부된 이미지. S3 비공개 버킷의 객체 키를 저장한다. */
@Entity
@Table(
    name = "member_report_image",
    uniqueConstraints = [
        UniqueConstraint(name = "member_report_image_uk_1", columnNames = ["member_report_id", "display_order"]),
    ],
)
class MemberReportImage private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("신고 ID")
    @Column(name = "member_report_id", nullable = false)
    val memberReportId: Long,

    @Comment("S3 객체 키")
    @Column(name = "object_key", nullable = false, length = 200)
    val objectKey: String,

    @Comment("첨부 순서 (0부터)")
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
) : BaseEntity() {

    companion object {
        const val MAX_COUNT = 3

        /** 신고 하나의 첨부 이미지 전체를 생성한다 — 개수 상한·중복 금지 불변식을 여기서 강제한다. */
        fun attachAll(
            memberReportId: Long,
            objectKeys: List<String>,
        ): List<MemberReportImage> {
            if (objectKeys.size > MAX_COUNT) {
                throw WarnException(ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED)
            }
            if (objectKeys.size != objectKeys.distinct().size) {
                throw WarnException(ErrorCode.INVALID_REPORT_IMAGE_KEY)
            }
            return objectKeys.mapIndexed { index, objectKey ->
                MemberReportImage(
                    memberReportId = memberReportId,
                    objectKey = objectKey,
                    displayOrder = index,
                )
            }
        }
    }
}
