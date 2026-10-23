// 운영 지표 화면의 계산. DOM 을 건드리지 않아 node --test 로 검증한다(api/src/test/js).
const StatsCalc = (() => {
    const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];
    const GENDER_UNKNOWN_LABEL = '미입력';
    const AGE_OUTSIDE_BANDS_LABEL = '그 외';
    const BIRTH_DATE_UNKNOWN_LABEL = '생년월일 없음';
    const REMATCH_LABEL = '재매칭';
    const INVITATION_ACCEPTED = 'ACCEPTED';
    const GROUP_ROOM_TYPE = 'GROUP';
    const REMATCH_ROOM_TYPE = 'REMATCH';
    // 매칭 배치 크론(목 00:01)과 맞춘다. 크론을 바꾸면 같이 바꾼다.
    const MATCHING_BATCH_DAY_OFFSET = 3;
    // 알림 유형 라벨은 어드민 화면에서만 써서 서버 enum 이 아니라 여기 둔다. 없는 유형은 enum 이름 그대로 보인다.
    const NOTIFICATION_TYPE_LABELS = {
        QUIZ_OPENED: '퀴즈 열림',
        QUIZ_CLOSING_SOON: '퀴즈 마감 임박',
        MATCH_RESULT: '매칭 결과',
        NO_MATCH: '매칭 없음',
        GROUP_FORMED: '그룹 결성',
        GROUP_NOT_FORMED: '그룹 취소(인원 미달)',
        REMATCH_REQUESTED: '재매칭 신청',
        REMATCH_REJECTED: '재매칭 불발',
        REMATCH_MATCHED: '재매칭 성사',
        MATCH_REQUESTED: '1:1 대화 신청',
        MATCH_ACCEPTED: '1:1 대화 수락',
        MATCH_REJECTED: '1:1 대화 거절',
        REVIEW_REQUEST: '평가 요청',
        REVIEW_REMINDER: '평가 재요청',
        CHAT_ROOM_OPENED: '채팅방 열림',
        CHAT_MESSAGE: '새 메시지',
        CHAT_NO_MESSAGE: '대화 시작 권유',
        CHAT_ENDING_SOON: '채팅 종료 임박',
        VOTE_CREATED: '투표 시작',
        VOTE_CLOSED: '투표 마감',
        SYSTEM_NOTICE: '시스템 공지',
        REPORT_ACTIONED: '신고 조치 완료',
        SANCTION_IMPOSED: '제재 안내',
    };

    // 'yyyy-MM-dd' 를 브라우저 시간대와 상관없이 다루려고 UTC 로 계산한다.
    const toUtcDate = (isoDate) => new Date(`${isoDate}T00:00:00Z`);
    const addDays = (isoDate, days) => {
        const date = toUtcDate(isoDate);
        date.setUTCDate(date.getUTCDate() + days);
        return date.toISOString().slice(0, 10);
    };
    const mondayOf = (isoDate) => addDays(isoDate, -((toUtcDate(isoDate).getUTCDay() + 6) % 7));
    // 서버는 일시를 'yyyy-MM-dd HH:mm:ss' 로 준다.
    const dateOf = (dateTime) => dateTime.slice(0, 10);

    const countBy = (items, keyOf) => items.reduce((counts, item) => {
        const key = keyOf(item);
        return counts.set(key, (counts.get(key) || 0) + 1);
    }, new Map());
    const sumCountsBy = (countRows, keyOf) => countRows.reduce((counts, countRow) => {
        const key = keyOf(countRow);
        return counts.set(key, (counts.get(key) || 0) + countRow.count);
    }, new Map());
    const groupBy = (items, keyOf) => items.reduce((groups, item) => {
        const key = keyOf(item);
        return groups.set(key, [...(groups.get(key) || []), item]);
    }, new Map());

    const sumCounts = (countRows) => countRows.reduce((sum, countRow) => sum + countRow.count, 0);
    const completedOnly = (progressCounts) => progressCounts.filter((countRow) => countRow.status === 'COMPLETED');
    // labels 순서대로 0이어도 한 행씩 낸다.
    const labeledCounts = (countsByKey, labels) =>
        Object.entries(labels).map(([key, label]) => ({ label, count: countsByKey.get(key) || 0 }));

    const formatCount = (count) => count.toLocaleString('ko-KR');
    const percent = (part, whole) => (whole === 0 ? 0 : (part * 100) / whole);
    const percentText = (part, whole) => (whole === 0 ? '-' : `${percent(part, whole).toFixed(1)}%`);

    const memberTotals = (countsByStatus) => {
        const joined = Object.values(countsByStatus).reduce((sum, count) => sum + count, 0);
        const left = countsByStatus.LEFT || 0;
        return { joined, current: joined - left, left };
    };

    // 오늘 이후 날짜는 아직 오지 않아 count 가 null 이다.
    const dailyJoins = (weekStartedOn, today, joinedAts) => {
        const countsByDate = countBy(joinedAts, dateOf);
        return DAY_LABELS.map((dayLabel, index) => {
            const date = addDays(weekStartedOn, index);
            const count = date > today ? null : countsByDate.get(date) || 0;
            return { date, dayLabel, isToday: date === today, count };
        });
    };

    const weekStartsUntil = (lastWeekStartedOn, weekCount) =>
        Array.from({ length: weekCount }, (_, index) => addDays(lastWeekStartedOn, -7 * (weekCount - 1 - index)));

    const weeklyJoins = (lastWeekStartedOn, weekCount, joinedAts) => {
        const countsByWeek = countBy(joinedAts, (joinedAt) => mondayOf(dateOf(joinedAt)));
        return weekStartsUntil(lastWeekStartedOn, weekCount)
            .map((weekStartedOn) => ({ weekStartedOn, count: countsByWeek.get(weekStartedOn) || 0 }));
    };

    // 만 나이. 서버 AgeBand.birthDateRange 와 경계가 같다.
    const ageOn = (birthDate, today) => {
        const [birthYear, birthMonth, birthDay] = birthDate.split('-').map(Number);
        const [year, month, day] = today.split('-').map(Number);
        const hadBirthday = month > birthMonth || (month === birthMonth && day >= birthDay);
        return year - birthYear - (hadBirthday ? 0 : 1);
    };

    const genderCounts = (genderBirthDateCounts, genderLabels) => {
        const countsByGender = sumCountsBy(genderBirthDateCounts, (countRow) => countRow.gender);
        return [
            ...labeledCounts(countsByGender, genderLabels),
            { label: GENDER_UNKNOWN_LABEL, count: countsByGender.get(null) || 0 },
        ];
    };

    // 성별 칸은 genderLabels 순서이고 마지막 칸이 미입력이다.
    const genderRow = (label, countRows, genderLabels) => {
        const countsByGender = sumCountsBy(countRows, (countRow) => countRow.gender);
        const countsInColumnOrder = [...Object.keys(genderLabels), null]
            .map((gender) => countsByGender.get(gender) || 0);
        const totalCount = countsInColumnOrder.reduce((sum, count) => sum + count, 0);
        return { label, countsInColumnOrder, totalCount };
    };

    const genderColumnLabels = (genderLabels) => [...Object.values(genderLabels), GENDER_UNKNOWN_LABEL];

    const ageGenderRows = (genderBirthDateCounts, genderLabels, ageBandRanges, today) => {
        const ageLabelOf = (birthDate) => {
            if (!birthDate) return BIRTH_DATE_UNKNOWN_LABEL;
            const age = ageOn(birthDate, today);
            const band = ageBandRanges.find(({ minAge, maxAge }) => age >= minAge && age <= maxAge);
            return band ? band.label : AGE_OUTSIDE_BANDS_LABEL;
        };
        const countRowsByAgeLabel = groupBy(genderBirthDateCounts, (countRow) => ageLabelOf(countRow.birthDate));
        const labels = [...ageBandRanges.map((band) => band.label), AGE_OUTSIDE_BANDS_LABEL, BIRTH_DATE_UNKNOWN_LABEL];
        return labels.map((label) => genderRow(label, countRowsByAgeLabel.get(label) || [], genderLabels));
    };

    const quizParticipation = (progressCounts, quizMatchingTypeLabels) => {
        const completedCounts = completedOnly(progressCounts);
        const participantCountsByType = sumCountsBy(completedCounts, (countRow) => countRow.matchingType);
        return {
            participantCount: sumCounts(completedCounts),
            startedCount: sumCounts(progressCounts),
            participantCountsByType: labeledCounts(participantCountsByType, quizMatchingTypeLabels),
        };
    };

    const participantGenderRows = (progressCounts, quizMatchingTypeLabels, genderLabels) => {
        const completedCounts = completedOnly(progressCounts);
        return Object.entries(quizMatchingTypeLabels).map(([matchingType, label]) => genderRow(
            label,
            completedCounts.filter((countRow) => countRow.matchingType === matchingType),
            genderLabels,
        ));
    };

    // 1:1 은 후보·성사 테이블이 따로 있고, 그룹은 초대 테이블 하나에서 성사된 그룹을 수락한 사람을 성사로 본다.
    const stageCountRowsByType = (matchStats) => ({
        ONE_TO_ONE: {
            candidate: matchStats.matchCandidateCounts,
            matched: matchStats.personalMatchMemberCounts,
        },
        GROUP: {
            candidate: matchStats.groupMatchMemberCounts,
            matched: matchStats.groupMatchMemberCounts
                .filter((countRow) => countRow.isGroupActive && countRow.status === INVITATION_ACCEPTED),
        },
    });

    const completedCountsOf = (progressCounts, matchingType) =>
        completedOnly(progressCounts).filter((countRow) => countRow.matchingType === matchingType);

    // 성사 뒤 진행을 초기화하면 참여에서 빠져 음수가 될 수 있어 성별 칸마다 0 에서 자른다.
    const unmatchedGenderRow = (label, completedCounts, matchedCounts, genderLabels) => {
        const completedByGender = sumCountsBy(completedCounts, (countRow) => countRow.gender);
        const matchedByGender = sumCountsBy(matchedCounts, (countRow) => countRow.gender);
        const unmatchedCounts = [...Object.keys(genderLabels), null].map((gender) => ({
            gender,
            count: Math.max(0, (completedByGender.get(gender) || 0) - (matchedByGender.get(gender) || 0)),
        }));
        return genderRow(label, unmatchedCounts, genderLabels);
    };

    const sumField = (rows, key) => rows.reduce((sum, row) => sum + row[key], 0);

    // 미매칭 카드는 성별 표 합계와 같은 값이다.
    const matchFunnel = (progressCounts, matchStats, quizMatchingTypeLabels, genderLabels) => {
        const countRowsByType = stageCountRowsByType(matchStats);
        const typeEntries = Object.entries(quizMatchingTypeLabels);
        const countsByType = typeEntries.map(([matchingType, label]) => ({
            label,
            completedCount: sumCounts(completedCountsOf(progressCounts, matchingType)),
            candidateCount: sumCounts(countRowsByType[matchingType].candidate),
            matchedCount: sumCounts(countRowsByType[matchingType].matched),
        }));
        const unmatchedGenderRows = typeEntries.map(([matchingType, label]) => unmatchedGenderRow(
            label,
            completedCountsOf(progressCounts, matchingType),
            countRowsByType[matchingType].matched,
            genderLabels,
        ));
        return {
            completedCount: sumField(countsByType, 'completedCount'),
            candidateCount: sumField(countsByType, 'candidateCount'),
            matchedCount: sumField(countsByType, 'matchedCount'),
            unmatchedCount: sumField(unmatchedGenderRows, 'totalCount'),
            countsByType,
            unmatchedGenderRows,
        };
    };

    const isBeforeMatchingBatch = (weekStartedOn, today) => today < addDays(weekStartedOn, MATCHING_BATCH_DAY_OFFSET);

    // 분모는 모두 열린 방이다. 전원은 나간 사람까지 참여자로 친다.
    const roomActivityCounts = (rooms) => ({
        openedCount: rooms.length,
        startedCount: rooms.filter((room) => room.senderCount >= 1).length,
        twoWayCount: rooms.filter((room) => room.senderCount >= 2).length,
        allSentCount: rooms.filter((room) => room.senderCount >= room.memberCount).length,
    });

    // 재매칭은 그룹에서 이어져서 그룹이 보일 때만 마지막 줄로 둔다. 합계에는 넣지 않는다.
    const chatActivity = (chatStats, roomTypeLabels) => {
        const senderCountsByRoom = new Map(chatStats.chatRoomSenderCounts.map((row) => [row.roomId, row.count]));
        const rooms = chatStats.chatRoomMemberCounts.map((row) => ({
            roomType: row.roomType,
            memberCount: row.count,
            senderCount: senderCountsByRoom.get(row.roomId) || 0,
        }));
        const roomsOf = (roomType) => rooms.filter((room) => room.roomType === roomType);
        const shownRoomTypes = Object.keys(roomTypeLabels);
        const typeRows = Object.entries(roomTypeLabels).map(([roomType, label]) => ({
            label,
            isGroup: roomType === GROUP_ROOM_TYPE,
            isRematch: false,
            ...roomActivityCounts(roomsOf(roomType)),
        }));
        const rematchRows = shownRoomTypes.includes(GROUP_ROOM_TYPE)
            ? [{
                label: REMATCH_LABEL,
                isGroup: false,
                isRematch: true,
                ...roomActivityCounts(roomsOf(REMATCH_ROOM_TYPE)),
            }]
            : [];
        return {
            ...roomActivityCounts(rooms.filter((room) => shownRoomTypes.includes(room.roomType))),
            rows: [...typeRows, ...rematchRows],
        };
    };

    // 서버가 퀴즈셋·문항·선택지 순서로 준 행을 그 순서 그대로 묶는다.
    const questionDistributions = (choiceAnswerCounts) =>
        [...groupBy(choiceAnswerCounts, (countRow) => countRow.quizSetId).values()].map((quizSetRows) => ({
            matchingType: quizSetRows[0].matchingType,
            questions: [...groupBy(quizSetRows, (countRow) => countRow.quizId).values()].map((choiceRows) => ({
                displayOrder: choiceRows[0].quizDisplayOrder,
                question: choiceRows[0].question,
                answerCount: sumCounts(choiceRows),
                choices: choiceRows
                    .map((choiceRow) => ({ label: choiceRow.choiceContent, count: choiceRow.count })),
            })),
        }));

    const memberReviewSubmission = (memberReviewCounts) => {
        const countsByStatus = sumCountsBy(memberReviewCounts, (countRow) => countRow.status);
        return {
            memberReviewCount: sumCounts(memberReviewCounts),
            completedCount: countsByStatus.get('COMPLETED') || 0,
            inProgressCount: countsByStatus.get('IN_PROGRESS') || 0,
            notStartedCount: countsByStatus.get('NOT_STARTED') || 0,
        };
    };

    const memberReviewSubmissionByType = (memberReviewCounts, memberReviewMatchTypeLabels) =>
        Object.entries(memberReviewMatchTypeLabels).map(([matchType, label]) => {
            const typeCounts = memberReviewCounts.filter((countRow) => countRow.matchType === matchType);
            const { memberReviewCount, completedCount } = memberReviewSubmission(typeCounts);
            return { label, memberReviewCount, completedCount };
        });

    const meetingStatusCounts = (reviewAnswerCounts, meetingStatusLabels) =>
        labeledCounts(sumCountsBy(reviewAnswerCounts, (countRow) => countRow.meetingStatus), meetingStatusLabels);

    const ratingCounts = (reviewAnswerCounts, ratingScores) => {
        const countsByRating = sumCountsBy(reviewAnswerCounts, (countRow) => countRow.rating);
        return ratingScores.map((rating) => ({ label: `${rating}점`, count: countsByRating.get(rating) || 0 }));
    };

    const retentionCounts = (previousProgressCounts, completedAgainCounts, quizMatchingTypeLabels) => {
        const previousCountsByType =
            sumCountsBy(completedOnly(previousProgressCounts), (countRow) => countRow.matchingType);
        const completedAgainCountsByType =
            sumCountsBy(completedAgainCounts, (countRow) => countRow.previousMatchingType);
        const countsByType = Object.entries(quizMatchingTypeLabels).map(([matchingType, label]) => ({
            label,
            previousCount: previousCountsByType.get(matchingType) || 0,
            completedAgainCount: completedAgainCountsByType.get(matchingType) || 0,
        }));
        return {
            previousCount: countsByType.reduce((sum, row) => sum + row.previousCount, 0),
            completedAgainCount: countsByType.reduce((sum, row) => sum + row.completedAgainCount, 0),
            countsByType,
        };
    };

    const isSkipped = (countRow) => countRow.skipReason !== null;

    // 실패는 보낸 기기 중 하나라도 실패한 알림이다.
    const pushSendTotals = (historyCounts) => {
        const sentRows = historyCounts.filter((countRow) => !isSkipped(countRow));
        return {
            notificationCount: sumCounts(historyCounts),
            sentCount: sumCounts(sentRows),
            failedCount: sumCounts(sentRows.filter((countRow) => countRow.failedDeviceCount > 0)),
            skippedCount: sumCounts(historyCounts.filter(isSkipped)),
        };
    };

    const pushSendCounts = (historyCounts, skipReasonLabels) => {
        const typeRows = [...groupBy(historyCounts, (countRow) => countRow.notificationType).entries()]
            .map(([notificationType, rows]) => ({
                label: NOTIFICATION_TYPE_LABELS[notificationType] || notificationType,
                ...pushSendTotals(rows),
            }))
            .sort((one, other) => other.sentCount - one.sentCount);
        const skippedRows = historyCounts.filter(isSkipped);
        return {
            ...pushSendTotals(historyCounts),
            typeRows,
            skipReasonCounts:
                labeledCounts(sumCountsBy(skippedRows, (countRow) => countRow.skipReason), skipReasonLabels),
        };
    };

    const memberReportCounts = (memberReportCountRows, reasonLabels, statusLabels) => {
        const countsByStatus = sumCountsBy(memberReportCountRows, (countRow) => countRow.status);
        const countsByReason = new Map(Object.keys(reasonLabels).map((reason) => [
            reason,
            sumCounts(memberReportCountRows.filter((countRow) => countRow.reasons.includes(reason))),
        ]));
        const memberReportCount = sumCounts(memberReportCountRows);
        const receivedCount = countsByStatus.get('RECEIVED') || 0;
        return {
            memberReportCount,
            receivedCount,
            processedCount: memberReportCount - receivedCount,
            reasonCounts: labeledCounts(countsByReason, reasonLabels),
            statusCounts: labeledCounts(countsByStatus, statusLabels),
        };
    };

    return {
        sumCounts,
        formatCount,
        percent,
        percentText,
        memberTotals,
        dailyJoins,
        weeklyJoins,
        ageOn,
        genderCounts,
        genderColumnLabels,
        ageGenderRows,
        quizParticipation,
        participantGenderRows,
        matchFunnel,
        isBeforeMatchingBatch,
        chatActivity,
        questionDistributions,
        memberReviewSubmission,
        memberReviewSubmissionByType,
        meetingStatusCounts,
        ratingCounts,
        retentionCounts,
        memberReportCounts,
        pushSendCounts,
    };
})();

if (typeof module !== 'undefined') module.exports = StatsCalc;
