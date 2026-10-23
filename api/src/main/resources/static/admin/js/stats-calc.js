// 운영 지표 화면의 계산. DOM 을 건드리지 않아 node --test 로 검증한다(api/src/test/js).
const StatsCalc = (() => {
    const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];
    const GENDER_UNKNOWN_LABEL = '미입력';
    const LOCATION_UNKNOWN_LABEL = '미입력';
    const LEAVE_REASON_UNKNOWN_LABEL = '사유 없음';
    // 탈퇴 사유는 enum 이 없는 문자열이다. 앞 다섯은 FE 탈퇴 화면(withdrawReasons.ts) 문구 그대로고,
    // APPLE_ 둘은 애플 서버 알림으로 서버가 탈퇴시킬 때 넣는다(AppleServerNotificationService).
    const LEAVE_REASON_LABELS = {
        no_desired_match: '원하는 매칭이 이루어지지 않아요',
        low_usage: '사용 빈도가 낮아졌어요',
        privacy_concern: '개인정보가 걱정돼요',
        bad_experience: '불편한 경험이 있었어요',
        other: '기타',
        APPLE_ACCOUNT_DELETED: '애플 계정 삭제',
        APPLE_CONSENT_REVOKED: '애플 연결 해제',
    };
    const AGE_OUTSIDE_BANDS_LABEL = '그 외';
    const BIRTH_DATE_UNKNOWN_LABEL = '생년월일 없음';
    const REMATCH_LABEL = '재매칭';
    const INVITATION_ACCEPTED = 'ACCEPTED';
    const GROUP_ROOM_TYPE = 'GROUP';
    const REMATCH_ROOM_TYPE = 'REMATCH';
    // 매칭 배치 크론(목 00:01)과 맞춘다. 크론을 바꾸면 같이 바꾼다.
    const MATCHING_BATCH_DAY_OFFSET = 3;
    // 퀴즈 응답 기간(월~수, QuizResponsePeriod)과 맞춘다. 응답 기간을 바꾸면 같이 바꾼다.
    const RESPONSE_DAY_COUNT = 3;
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
    const average = (values) =>
        (values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length);
    const median = (values) => {
        if (values.length === 0) return null;
        const sorted = [...values].sort((one, other) => one - other);
        const middle = Math.floor(sorted.length / 2);
        if (sorted.length % 2 === 1) return sorted[middle];
        return (sorted[middle - 1] + sorted[middle]) / 2;
    };
    const completedOnly = (progressCounts) => progressCounts.filter((countRow) => countRow.status === 'COMPLETED');
    // labels 순서대로 0이어도 한 행씩 낸다.
    const labeledCounts = (countsByKey, labels) =>
        Object.entries(labels).map(([key, label]) => ({ label, count: countsByKey.get(key) || 0 }));

    const formatCount = (count) => count.toLocaleString('ko-KR');
    const percent = (part, whole) => (whole === 0 ? 0 : (part * 100) / whole);
    const percentText = (part, whole) => (whole === 0 ? '-' : `${percent(part, whole).toFixed(1)}%`);

    // 가입을 마치지 않은 PENDING 은 누적 가입자에 넣지 않는다.
    const memberTotals = (countsByStatus) => {
        const pending = countsByStatus.PENDING || 0;
        const joined = Object.values(countsByStatus).reduce((sum, count) => sum + count, 0) - pending;
        const left = countsByStatus.LEFT || 0;
        return { joined, current: joined - left, left, pending };
    };

    // 활성은 ACTIVE 중 최근 회차 완주자, 휴면은 나머지 ACTIVE 다. 정지 회원은 완주했어도 정지로 센다.
    const accountStatusCounts = (countsByStatus, recentlyCompletedActiveStatusCount) => {
        const activeStatusMemberCount = countsByStatus.ACTIVE || 0;
        return [
            { label: '활성', count: recentlyCompletedActiveStatusCount },
            { label: '휴면', count: activeStatusMemberCount - recentlyCompletedActiveStatusCount },
            { label: '정지', count: countsByStatus.SUSPENDED || 0 },
            { label: '영구 차단', count: countsByStatus.BANNED || 0 },
        ];
    };

    // 지역은 많아서 회원이 있는 곳만 많은 순으로 보인다.
    const locationCounts = (countRows, locationLabels) => {
        const unknownRow = countRows.find((countRow) => countRow.location === null);
        const knownRows = countRows
            .filter((countRow) => countRow.location !== null)
            .map((countRow) => ({
                label: locationLabels[countRow.location] || countRow.location,
                count: countRow.count,
            }))
            .sort((one, other) => other.count - one.count || one.label.localeCompare(other.label, 'ko'));
        return unknownRow ? [...knownRows, { label: LOCATION_UNKNOWN_LABEL, count: unknownRow.count }] : knownRows;
    };

    // 사유는 탈퇴 화면 순서대로 0이어도 나온다. 모르는 코드는 코드 그대로 뒤에 붙는다.
    const leaveReasonCounts = (countRows) => {
        const countsByReason = sumCountsBy(countRows, (countRow) => countRow.leaveReason);
        const unlabeledReasonRows = [...countsByReason.entries()]
            .filter(([reason]) => reason === null || !Object.hasOwn(LEAVE_REASON_LABELS, reason))
            .map(([reason, count]) => ({ label: reason === null ? LEAVE_REASON_UNKNOWN_LABEL : reason, count }));
        return {
            leftCount: sumCounts(countRows),
            reasonCounts: [...labeledCounts(countsByReason, LEAVE_REASON_LABELS), ...unlabeledReasonRows],
        };
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

    const HOUR_MILLIS = 60 * 60 * 1000;
    const toUtcDateTime = (dateTime) => new Date(`${dateTime.replace(' ', 'T')}Z`);
    const hoursBetween = (from, to) => (toUtcDateTime(to) - toUtcDateTime(from)) / HOUR_MILLIS;
    const MESSAGE_COUNT_BINS = [
        { label: '0개', max: 0 },
        { label: '1~9개', max: 9 },
        { label: '10~49개', max: 49 },
        { label: '50~99개', max: 99 },
        { label: '100개 이상', max: Infinity },
    ];
    const FIRST_MESSAGE_HOUR_BINS = [
        { label: '1시간 안', max: 1 },
        { label: '1~6시간', max: 6 },
        { label: '6~24시간', max: 24 },
        { label: '24~48시간', max: 48 },
        { label: '48시간 넘게', max: Infinity },
    ];
    // 방은 72시간 열린다.
    const LAST_MESSAGE_HOUR_BINS = [
        { label: '첫째 날', max: 24 },
        { label: '둘째 날', max: 48 },
        { label: '셋째 날', max: 72 },
        { label: '72시간 뒤', max: Infinity },
    ];
    // 메시지 시각은 실제 시각이고 방 열린 시각은 시각 조정을 따라서, 시각을 옮긴 dev 에서는 음수가 나올 수 있다.
    const BEFORE_OPEN_LABEL = '열린 시각 전(시각 조정)';

    // 구간마다 max 이하이고 앞 구간 max 보다 큰 값을 센다. 음수는 맨 앞 칸에 따로 모은다.
    const binCounts = (values, bins) => {
        const beforeOpenCount = values.filter((value) => value < 0).length;
        const counted = bins.map((bin, index) => {
            const lowerExclusive = index === 0 ? -Infinity : bins[index - 1].max;
            const inBin = (value) => value >= 0 && value > lowerExclusive && value <= bin.max;
            return { label: bin.label, count: values.filter(inBin).length };
        });
        return beforeOpenCount > 0 ? [{ label: BEFORE_OPEN_LABEL, count: beforeOpenCount }, ...counted] : counted;
    };

    // 발신자 성별로 나뉜 행을 방마다 합치고, 가장 이른 행의 성별을 첫 발신자로 본다.
    const roomMessageSummaries = (messageSummaries) =>
        [...groupBy(messageSummaries, (summary) => summary.roomId).values()].map((genderRows) => {
            const firstRow = genderRows
                .reduce((earliest, row) => (row.firstMessageAt < earliest.firstMessageAt ? row : earliest));
            const lastMessageAt = genderRows.map((row) => row.lastMessageAt).sort().at(-1);
            return {
                roomId: firstRow.roomId,
                messageCount: genderRows.reduce((sum, row) => sum + row.messageCount, 0),
                firstSenderGender: firstRow.senderGender,
                firstMessageHours: hoursBetween(firstRow.opensAt, firstRow.firstMessageAt),
                lastMessageHours: hoursBetween(firstRow.opensAt, lastMessageAt),
            };
        });

    // 열린 방 기준이고 메시지가 없는 방은 0개로 센다. 재매칭 방은 대화 활동 합계처럼 넣지 않는다.
    const chatMessageStats = (chatStats, roomTypeLabels, genderLabels) => {
        const shownRoomIds = new Set(chatStats.chatRoomMemberCounts
            .filter((row) => Object.hasOwn(roomTypeLabels, row.roomType))
            .map((row) => row.roomId));
        const messagedRooms = roomMessageSummaries(chatStats.messageSummaries)
            .filter((room) => shownRoomIds.has(room.roomId));
        const messageCounts = [
            ...messagedRooms.map((room) => room.messageCount),
            ...Array(shownRoomIds.size - messagedRooms.length).fill(0),
        ];
        const firstMessageHours = messagedRooms.map((room) => room.firstMessageHours);
        const firstSenderCounts = countBy(messagedRooms, (room) => room.firstSenderGender);
        return {
            roomCount: shownRoomIds.size,
            messagedRoomCount: messagedRooms.length,
            averageMessageCount: average(messageCounts),
            medianMessageCount: median(messageCounts),
            messageCountBins: binCounts(messageCounts, MESSAGE_COUNT_BINS),
            medianFirstMessageHours: median(firstMessageHours),
            firstMessageBins: binCounts(firstMessageHours, FIRST_MESSAGE_HOUR_BINS),
            beforeOpenRoomCount: firstMessageHours.filter((hours) => hours < 0).length,
            firstSenderGenderCounts: [
                ...labeledCounts(firstSenderCounts, genderLabels),
                { label: GENDER_UNKNOWN_LABEL, count: firstSenderCounts.get(null) || 0 },
            ],
            lastMessageBins: binCounts(messagedRooms.map((room) => room.lastMessageHours), LAST_MESSAGE_HOUR_BINS),
        };
    };

    const choiceShare = (answerRows, choiceId) => {
        const choiceCount = sumCounts(answerRows.filter((countRow) => countRow.choiceId === choiceId));
        return percent(choiceCount, sumCounts(answerRows));
    };

    // 답한 성별만 나온다.
    const choiceSharesByGender = (questionRows, choiceId, genderLabels) =>
        Object.entries(genderLabels)
            .map(([gender, label]) => {
                const genderRows = questionRows.filter((countRow) => countRow.gender === gender);
                return { label, answerCount: sumCounts(genderRows), share: choiceShare(genderRows, choiceId) };
            })
            .filter((genderShare) => genderShare.answerCount > 0);

    // 문항은 선택지가 둘이라 첫 선택지 비율 하나로 쏠림과 성별 차이를 본다. 행은 선택지 순서로 온다.
    // 쏠림은 첫 선택지 비율이 50%에서 벗어난 %p 다. 0 이면 반반, 50 이면 한쪽으로 다 몰렸다.
    const questionSummary = (questionRows, genderLabels) => {
        const answerCount = sumCounts(questionRows);
        const firstChoiceId = questionRows[0].choiceId;
        return {
            displayOrder: questionRows[0].quizDisplayOrder,
            question: questionRows[0].question,
            answerCount,
            choices: [...groupBy(questionRows, (countRow) => countRow.choiceId).values()]
                .map((choiceRows) => ({ label: choiceRows[0].choiceContent, count: sumCounts(choiceRows) })),
            skew: answerCount === 0 ? null : Math.abs(choiceShare(questionRows, firstChoiceId) - 50),
            genderShares: choiceSharesByGender(questionRows, firstChoiceId, genderLabels),
        };
    };

    // 퀴즈셋의 쏠림 점수는 답이 있는 문항의 쏠림 평균이다.
    const averageSkew = (questions) => {
        const skews = questions.map((question) => question.skew).filter((skew) => skew !== null);
        return skews.length === 0 ? null : skews.reduce((sum, skew) => sum + skew, 0) / skews.length;
    };

    // 서버가 퀴즈셋·문항·선택지 순서로 준 행을 그 순서 그대로 묶는다.
    const questionDistributions = (choiceAnswerCounts, genderLabels) =>
        [...groupBy(choiceAnswerCounts, (countRow) => countRow.quizSetId).values()].map((quizSetRows) => {
            const questions = [...groupBy(quizSetRows, (countRow) => countRow.quizId).values()]
                .map((questionRows) => questionSummary(questionRows, genderLabels));
            return { matchingType: quizSetRows[0].matchingType, questions, skewScore: averageSkew(questions) };
        });

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

    // 응답 기간인 월~수는 0이어도 칸을 채우고, 그 밖의 요일은 완주가 있을 때만 붙는다(QA 시각 조정 등).
    const completionDayCounts = (completedAts) => {
        const mondayFirstDayIndexOf = (dateTime) => (toUtcDate(dateOf(dateTime)).getUTCDay() + 6) % 7;
        const countsByDay = countBy(completedAts, mondayFirstDayIndexOf);
        return DAY_LABELS
            .map((label, index) => ({ label, count: countsByDay.get(index) || 0 }))
            .filter((day, index) => index < RESPONSE_DAY_COUNT || day.count > 0);
    };

    // 지난 회차 유형은 조건으로 거른 라벨, 고른 회차 유형은 늘 두 유형 다 나온다.
    const trackSwitchCounts = (completedAgainCounts, previousTypeLabels, allQuizMatchingTypeLabels) =>
        Object.entries(previousTypeLabels).flatMap(([previousType, previousLabel]) =>
            Object.entries(allQuizMatchingTypeLabels).map(([againType, againLabel]) => ({
                label: `${previousLabel} → ${againLabel}`,
                count: sumCounts(completedAgainCounts.filter((countRow) =>
                    countRow.previousMatchingType === previousType && countRow.againMatchingType === againType)),
            })));

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

    const SCORE_BIN_SIZE = 10;
    const LAST_SCORE_BIN = 9;

    // 점수는 0~100 이다. 10점 구간으로 묶고 100점은 90점 이상 칸에 넣는다. 값이 있는 구간 사이만 보인다.
    const scoreSummary = (scores) => {
        if (scores.length === 0) return { count: 0, average: null, median: null, bins: [] };
        const binIndexOf = (score) => Math.min(Math.floor(score / SCORE_BIN_SIZE), LAST_SCORE_BIN);
        const countsByBin = countBy(scores, binIndexOf);
        const firstBin = Math.min(...countsByBin.keys());
        const lastBin = Math.max(...countsByBin.keys());
        const bins = Array.from({ length: lastBin - firstBin + 1 }, (_, offset) => {
            const binIndex = firstBin + offset;
            const label = binIndex === LAST_SCORE_BIN ? '90점 이상' : `${binIndex * SCORE_BIN_SIZE}점대`;
            return { label, count: countsByBin.get(binIndex) || 0 };
        });
        return {
            count: scores.length,
            average: average(scores),
            median: median(scores),
            bins,
        };
    };

    // 후보를 다시 만들면 성사 쌍의 후보 행이 지워져 점수가 null 로 온다.
    const matchScores = ({ pairScores, acceptedPairScores, groupScores }) => ({
        pair: scoreSummary(pairScores),
        acceptedPair: scoreSummary(acceptedPairScores.filter((score) => score !== null)),
        unscoredAcceptedPairCount: acceptedPairScores.filter((score) => score === null).length,
        group: scoreSummary(groupScores.map((groupScore) => groupScore.score)),
        activeGroup: scoreSummary(
            groupScores.filter((groupScore) => groupScore.isGroupActive).map((groupScore) => groupScore.score),
        ),
    });

    // 성사 그룹을 인원수로 묶고 인원수마다 성별 구성을 낸다.
    const activeGroupSizeRows = (activeGroupMemberCounts, genderLabels) => {
        const rowsByGroup = groupBy(activeGroupMemberCounts, (countRow) => countRow.groupMatchId);
        const groupRowsBySize = groupBy([...rowsByGroup.values()], (groupRows) => sumCounts(groupRows));
        return [...groupRowsBySize.entries()]
            .sort(([oneSize], [otherSize]) => oneSize - otherSize)
            .map(([size, groups]) => genderRow(`${size}명 · ${groups.length}개`, groups.flat(), genderLabels));
    };

    // 지난 회차 완주자를 성사 여부로 나눈다. 성사 안 됨은 전체에서 성사를 뺀 값이다.
    const matchResultRetention = (retentionTotals, matchedPreviousCounts, matchedCompletedAgainCounts) => {
        const matchedPreviousCount = sumCounts(matchedPreviousCounts);
        const matchedAgainCount = sumCounts(matchedCompletedAgainCounts);
        return [
            { label: '성사됨', previousCount: matchedPreviousCount, completedAgainCount: matchedAgainCount },
            {
                label: '성사 안 됨',
                previousCount: retentionTotals.previousCount - matchedPreviousCount,
                completedAgainCount: retentionTotals.completedAgainCount - matchedAgainCount,
            },
        ];
    };

    // 가입한 주마다 가입 뒤 N번째 회차에 완주한 수. 가입한 주가 회차면 그 회차는 "가입한 주" 칸이다.
    // 아직 오지 않은 회차 칸은 null 이다.
    const cohortRetention = ({ recentJoinedAts, recentWeekCount }, retentionStats, weekStartedOn) => {
        const roundWeeks = [...retentionStats.cohortRoundWeeks].sort();
        const cohortWeeks = Array.from({ length: recentWeekCount }, (_, index) =>
            addDays(weekStartedOn, -7 * (recentWeekCount - 1 - index)));
        const joinedCountsByWeek = countBy(recentJoinedAts, (joinedAt) => mondayOf(dateOf(joinedAt)));
        const completionKey = (cohortWeek, roundWeek) => `${cohortWeek}|${roundWeek}`;
        const completionCounts = countBy(
            retentionStats.joinedAtCompletions,
            (completion) => completionKey(mondayOf(dateOf(completion.joinedAt)), completion.weekStartedOn),
        );
        const columnCount = roundWeeks.filter((roundWeek) => roundWeek > cohortWeeks[0]).length;
        const cellOf = (cohortWeek, roundWeek) => (roundWeek === undefined
            ? null
            : { completedCount: completionCounts.get(completionKey(cohortWeek, roundWeek)) || 0 });
        const rows = cohortWeeks
            .map((cohortWeek) => {
                const roundsAfter = roundWeeks.filter((roundWeek) => roundWeek > cohortWeek);
                return {
                    cohortWeek,
                    joinedCount: joinedCountsByWeek.get(cohortWeek) || 0,
                    joinWeekCell: roundWeeks.includes(cohortWeek) ? cellOf(cohortWeek, cohortWeek) : null,
                    afterCells: Array.from(
                        { length: columnCount },
                        (_, index) => cellOf(cohortWeek, roundsAfter[index]),
                    ),
                };
            })
            .filter((row) => row.joinedCount > 0);
        return { columnCount, rows };
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
        accountStatusCounts,
        locationCounts,
        leaveReasonCounts,
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
        completionDayCounts,
        trackSwitchCounts,
        matchResultRetention,
        cohortRetention,
        chatMessageStats,
        scoreSummary,
        matchScores,
        activeGroupSizeRows,
        memberReportCounts,
        pushSendCounts,
    };
})();

if (typeof module !== 'undefined') module.exports = StatsCalc;
