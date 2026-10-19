package com.ditto.api.launchnotification.service

import com.ditto.api.launchnotification.dto.CreateLaunchNotificationRequest
import com.ditto.domain.launchnotification.entity.LaunchNotificationRequest
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.repository.LaunchNotificationRequestRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class LaunchNotificationService(
    private val launchNotificationRequestRepository: LaunchNotificationRequestRepository,
    private val transactionTemplate: TransactionTemplate,
    private val launchNotificationMetrics: LaunchNotificationMetrics,
) {

    /**
     * 출시 알림을 신청한다. 이미 신청한 (이메일, 플랫폼)이면 새 행 없이 동의 시각·버전만 갱신한다.
     * 어느 쪽이든 결과가 같아 응답으로 등록 여부가 드러나지 않는다.
     *
     * 동의 시각은 증빙이라 실제 시각을 쓴다(어드민 시각 오버라이드를 따르지 않는다).
     */
    fun request(request: CreateLaunchNotificationRequest) {
        LaunchNotificationRequest.requireConsent(request.privacyConsent)
        val platform = LaunchPlatform.from(request.platform)
        // 기존 신청을 찾으려면 저장할 때와 같은 형태여야 해서 조회 전에 맞춘다.
        val email = LaunchNotificationRequest.normalizeEmail(request.email)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

        // 같은 주소가 동시에 들어오면(더블클릭) 한쪽이 유일 제약에, 셋 이상이면 MySQL 데드락에 걸릴 수 있다.
        // 그때는 이미 생긴 행을 갱신한다. 트랜잭션을 나눠 다시 도는 이유는, 실패한 트랜잭션은 롤백 전용이 되어 이어 쓸 수 없어서다.
        val created = runCatching { upsert(email, platform, request.consentVersion, now) }
            .recoverCatching { e ->
                if (e !is DataIntegrityViolationException && e !is PessimisticLockingFailureException) throw e
                upsert(email, platform, request.consentVersion, now)
            }
            .getOrThrow()
        launchNotificationMetrics.countRequested(platform, created)
    }

    /** @return 새로 만들었으면 true, 기존 신청을 갱신했으면 false */
    private fun upsert(email: String, platform: LaunchPlatform, consentVersion: String, now: LocalDateTime): Boolean =
        transactionTemplate.execute {
            val existing = launchNotificationRequestRepository.findByEmailAndPlatform(email, platform)
            if (existing != null) {
                existing.reconsent(consentVersion, now)
                false
            } else {
                launchNotificationRequestRepository.saveAndFlush(
                    LaunchNotificationRequest.request(email, platform, consentVersion, now),
                )
                true
            }
        }!!
}
