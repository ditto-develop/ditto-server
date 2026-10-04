package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberNotificationSettingRepository
import com.ditto.domain.member.repository.NicknameReservationRepository
import com.ditto.domain.memberreport.repository.MemberReportImageRepository
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import org.springframework.stereotype.Component

/** 더미 본인의 데이터와, 테스터가 앱에서 더미를 상대로 남긴 신고·차단·제재. */
@Component
class DummyMemberDataCleaner(
    private val memberReportRepository: MemberReportRepository,
    private val memberReportImageRepository: MemberReportImageRepository,
    private val sanctionRepository: SanctionRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val memberNotificationSettingRepository: MemberNotificationSettingRepository,
    private val nicknameReservationRepository: NicknameReservationRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val introNoteRepository: IntroNoteRepository,
) {
    /** 더미가 신고했거나 신고당한 건과 그에 딸린 제재. 지운 id 는 그것을 가리키는 알림을 지우는 데 쓴다. */
    fun deleteReportsAndSanctionsWith(dummyIds: Collection<Long>): DeletedReportIds {
        val reportIds = memberReportRepository.findByReporterIdInOrReportedMemberIdIn(dummyIds, dummyIds).map { it.id }
        val sanctionIds = sanctionRepository.findByMemberIdInOrMemberReportIdIn(dummyIds, reportIds).map { it.id }
        val imageIds = memberReportImageRepository.findByMemberReportIdIn(reportIds).map { it.id }

        memberReportImageRepository.deleteAllByIdInBatch(imageIds)
        memberReportRepository.deleteAllByIdInBatch(reportIds)
        sanctionRepository.deleteAllByIdInBatch(sanctionIds)
        return DeletedReportIds(reportIds = reportIds.toSet(), sanctionIds = sanctionIds.toSet())
    }

    fun deleteOwnedDataOf(dummyIds: Collection<Long>) {
        val blockIds = memberBlockRepository.findByBlockerIdInOrBlockedMemberIdIn(dummyIds, dummyIds).map { it.id }
        memberBlockRepository.deleteAllByIdInBatch(blockIds)
        val settingIds = memberNotificationSettingRepository.findByMemberIdIn(dummyIds).map { it.id }
        memberNotificationSettingRepository.deleteAllByIdInBatch(settingIds)
        val reservationIds = nicknameReservationRepository.findByMemberIdIn(dummyIds).map { it.id }
        nicknameReservationRepository.deleteAllByIdInBatch(reservationIds)
        memberDeviceRepository.deleteAllByIdInBatch(memberDeviceRepository.findByMemberIdIn(dummyIds).map { it.id })
        refreshTokenRepository.deleteAllByIdInBatch(refreshTokenRepository.findByMemberIdIn(dummyIds).map { it.id })
        introNoteRepository.deleteAllByIdInBatch(introNoteRepository.findByMemberIdIn(dummyIds).map { it.id })
    }

    class DeletedReportIds(
        val reportIds: Set<Long>,
        val sanctionIds: Set<Long>,
    )
}
