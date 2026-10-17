package com.ditto.domain.feedback.entity

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

@Entity
@Table(
    name = "feedback_image",
    uniqueConstraints = [
        UniqueConstraint(name = "feedback_image_uk_1", columnNames = ["feedback_id", "display_order"]),
    ],
)
class FeedbackImage private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("의견 ID")
    @Column(name = "feedback_id", nullable = false)
    val feedbackId: Long,

    @Comment("S3 객체 키")
    @Column(name = "object_key", nullable = false, length = 200)
    val objectKey: String,

    @Comment("첨부 순서 (0부터)")
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
) : BaseEntity() {

    companion object {
        const val MAX_COUNT = 3

        fun attachAll(feedbackId: Long, objectKeys: List<String>): List<FeedbackImage> {
            if (objectKeys.size > MAX_COUNT) {
                throw WarnException(ErrorCode.ATTACHMENT_IMAGE_LIMIT_EXCEEDED)
            }
            if (objectKeys.size != objectKeys.distinct().size) {
                throw WarnException(ErrorCode.INVALID_ATTACHMENT_IMAGE_KEY)
            }
            return objectKeys.mapIndexed { index, objectKey ->
                FeedbackImage(feedbackId = feedbackId, objectKey = objectKey, displayOrder = index)
            }
        }
    }
}
