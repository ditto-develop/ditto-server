package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberNotificationSettingRepository
import com.ditto.domain.member.repository.NicknameReservationRepository
import com.ditto.domain.memberreport.repository.MemberReportImageRepository
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.repository.SanctionRepository
import org.springframework.stereotype.Component

/** 더미 본인의 데이터와, 더미가 낀 신고·차단·제재. 더미가 실회원을 신고해 생긴 제재도 포함한다. */
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
    /** 더미가 신고했거나 신고당한 건. */
    fun findReportIdsWith(dummyIds: Collection<Long>): Set<Long> =
        memberReportRepository.findByReporterIdInOrReportedMemberIdIn(dummyIds, dummyIds).map { it.id }.toSet()

    /** 더미가 받은 제재와, 지울 신고에서 나온 제재(더미가 신고한 실회원의 제재 포함). */
    fun findSanctionsWith(dummyIds: Collection<Long>, reportIds: Collection<Long>): List<Sanction> =
        sanctionRepository.findByMemberIdInOrMemberReportIdIn(dummyIds, reportIds)

    fun deleteReportsAndSanctions(reportIds: Collection<Long>, sanctionIds: Collection<Long>) {
        val imageIds = memberReportImageRepository.findByMemberReportIdIn(reportIds).map { it.id }
        memberReportImageRepository.deleteAllByIdInBatch(imageIds)
        memberReportRepository.deleteAllByIdInBatch(reportIds)
        sanctionRepository.deleteAllByIdInBatch(sanctionIds)
    }

    /** 차단·알림 설정·닉네임 예약·기기·리프레시 토큰·소개노트. */
    fun deleteAccountDataOf(dummyIds: Collection<Long>) {
        val blockIds = memberBlockRepository.findByBlockerIdInOrBlockedMemberIdIn(dummyIds, dummyIds).map { it.id }
        memberBlockRepository.deleteAllByIdInBatch(blockIds)
        val settingIds = memberNotificationSettingRepository.findByMemberIdIn(dummyIds).map { it.id }
        memberNotificationSettingRepository.deleteAllByIdInBatch(settingIds)
        val reservationIds = nicknameReservationRepository.findByMemberIdIn(dummyIds).map { it.id }
        nicknameReservationRepository.deleteAllByIdInBatch(reservationIds)
        val deviceIds = memberDeviceRepository.findByMemberIdIn(dummyIds).map { it.id }
        memberDeviceRepository.deleteAllByIdInBatch(deviceIds)
        val refreshTokenIds = refreshTokenRepository.findByMemberIdIn(dummyIds).map { it.id }
        refreshTokenRepository.deleteAllByIdInBatch(refreshTokenIds)
        val introNoteIds = introNoteRepository.findByMemberIdIn(dummyIds).map { it.id }
        introNoteRepository.deleteAllByIdInBatch(introNoteIds)
    }
}
