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
        val reports = memberReportRepository.findByReporterIdInOrReportedMemberIdIn(dummyIds, dummyIds)
        val reportIds = reports.map { it.id }
        val sanctions = sanctionRepository.findByMemberIdInOrMemberReportIdIn(dummyIds, reportIds)

        memberReportImageRepository.deleteAllInBatch(memberReportImageRepository.findByMemberReportIdIn(reportIds))
        memberReportRepository.deleteAllInBatch(reports)
        sanctionRepository.deleteAllInBatch(sanctions)
        return DeletedReportIds(reportIds = reportIds.toSet(), sanctionIds = sanctions.map { it.id }.toSet())
    }

    fun deleteOwnedDataOf(dummyIds: Collection<Long>) {
        val blocks = memberBlockRepository.findByBlockerIdInOrBlockedMemberIdIn(dummyIds, dummyIds)
        memberBlockRepository.deleteAllInBatch(blocks)
        val notificationSettings = memberNotificationSettingRepository.findByMemberIdIn(dummyIds)
        memberNotificationSettingRepository.deleteAllInBatch(notificationSettings)
        nicknameReservationRepository.deleteAllInBatch(nicknameReservationRepository.findByMemberIdIn(dummyIds))
        memberDeviceRepository.deleteAllInBatch(memberDeviceRepository.findByMemberIdIn(dummyIds))
        refreshTokenRepository.deleteAllInBatch(refreshTokenRepository.findByMemberIdIn(dummyIds))
        introNoteRepository.deleteAllInBatch(introNoteRepository.findByMemberIdIn(dummyIds))
    }

    class DeletedReportIds(
        val reportIds: Set<Long>,
        val sanctionIds: Set<Long>,
    )
}
