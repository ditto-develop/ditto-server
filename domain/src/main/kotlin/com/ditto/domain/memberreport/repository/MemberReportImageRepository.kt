package com.ditto.domain.memberreport.repository

import com.ditto.domain.memberreport.entity.MemberReportImage
import org.springframework.data.jpa.repository.JpaRepository

interface MemberReportImageRepository : JpaRepository<MemberReportImage, Long> {

    fun findByMemberReportIdIn(memberReportIds: Collection<Long>): List<MemberReportImage>

    fun findAllByMemberReportIdOrderByDisplayOrder(memberReportId: Long): List<MemberReportImage>
}
