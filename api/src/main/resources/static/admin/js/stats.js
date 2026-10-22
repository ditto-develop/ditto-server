// 운영 지표를 그린다. 원본은 서버가 넣은 statsPageJson, 계산은 StatsCalc(stats-calc.js) 가 한다.
(() => {
    const pageData = JSON.parse(window.statsPageJson || 'null');
    if (!pageData) return;

    const shortDate = (isoDate) => `${Number(isoDate.slice(5, 7))}/${Number(isoDate.slice(8, 10))}`;

    const byId = (id) => document.getElementById(id);

    const cell = (text, className) => {
        const td = document.createElement('td');
        td.textContent = text;
        if (className) td.className = className;
        return td;
    };

    // 0 은 흐리게, 아직 오지 않은 날(null)은 '-' 로 그린다.
    const countCell = (count) => {
        if (count === null) return cell('-', 'num empty');
        return cell(StatsCalc.formatCount(count), count === 0 ? 'num empty' : 'num');
    };

    const headerCell = (text, className) => {
        const th = document.createElement('th');
        th.textContent = text;
        if (className) th.className = className;
        return th;
    };

    const fillRows = (tbodyId, rows, cellsOf, classNameOf = () => '') => {
        const rowElements = rows.map((row) => {
            const tr = document.createElement('tr');
            tr.className = classNameOf(row);
            tr.append(...cellsOf(row));
            return tr;
        });
        byId(tbodyId).replaceChildren(...rowElements);
    };

    const shareRow = ({ label, count }, whole) => {
        const row = document.createElement('div');
        row.className = 'share-row';
        const name = document.createElement('span');
        name.textContent = label;
        const bar = document.createElement('span');
        bar.className = 'share-bar';
        const fill = document.createElement('span');
        fill.style.width = `${StatsCalc.percent(count, whole)}%`;
        bar.append(fill);
        const value = document.createElement('span');
        value.className = 'share-value';
        const countText = `${StatsCalc.formatCount(count)}명`;
        value.textContent = whole === 0 ? countText : `${countText} · ${StatsCalc.percentText(count, whole)}`;
        row.append(name, bar, value);
        return row;
    };

    const renderMemberTotals = (memberStats) => {
        const totals = StatsCalc.memberTotals(memberStats.countsByStatus);
        byId('memberJoinedCount').textContent = `${StatsCalc.formatCount(totals.joined)}명`;
        byId('memberCurrentCount').textContent = `${StatsCalc.formatCount(totals.current)}명`;
        byId('memberLeftCount').textContent = `${StatsCalc.formatCount(totals.left)}명`;
        byId('memberEmptyNotice').hidden = totals.joined > 0;
    };

    const renderGenderShares = (memberStats, genderLabels) => {
        const genderCounts = StatsCalc.genderCounts(memberStats.genderBirthDateCounts, genderLabels);
        const currentTotal = genderCounts.reduce((sum, { count }) => sum + count, 0);
        byId('genderShares').replaceChildren(...genderCounts.map((gender) => shareRow(gender, currentTotal)));
    };

    const renderAgeGenderTable = (memberStats, genderLabels, ageBandRanges, today) => {
        const headRow = document.createElement('tr');
        headRow.append(
            headerCell('연령대'),
            ...StatsCalc.genderColumnLabels(genderLabels).map((label) => headerCell(label, 'num')),
            headerCell('합계', 'num'),
        );
        byId('ageGenderHead').replaceChildren(headRow);

        const rows = StatsCalc.ageGenderRows(memberStats.genderBirthDateCounts, genderLabels, ageBandRanges, today);
        fillRows('ageGenderRows', rows, (row) => [
            cell(row.label),
            ...row.countsInColumnOrder.map(countCell),
            countCell(row.totalCount),
        ]);
    };

    const renderJoinTables = (memberStats, weekStartedOn, today) => {
        const days = StatsCalc.dailyJoins(weekStartedOn, today, memberStats.recentJoinedAts);
        fillRows(
            'dailyJoinRows',
            days,
            (day) => [cell(`${shortDate(day.date)} (${day.dayLabel})`), countCell(day.count)],
            (day) => (day.isToday ? 'today' : ''),
        );

        byId('weeklyJoinTitle').textContent = `고른 회차까지 ${memberStats.recentWeekCount}주 신규 가입`;
        const weeks = StatsCalc.weeklyJoins(weekStartedOn, memberStats.recentWeekCount, memberStats.recentJoinedAts);
        fillRows('weeklyJoinRows', weeks, (week) => [cell(`${shortDate(week.weekStartedOn)} 주`), countCell(week.count)]);
    };

    const renderMemberSection = ({ weekStartedOn, today, genderLabels, ageBandRanges, memberStats }) => {
        byId('statsToday').textContent = today;
        renderMemberTotals(memberStats);
        renderGenderShares(memberStats, genderLabels);
        renderAgeGenderTable(memberStats, genderLabels, ageBandRanges, today);
        renderJoinTables(memberStats, weekStartedOn, today);
    };

    renderMemberSection(pageData);
})();
