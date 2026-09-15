/**
 * 퀴즈셋 폼의 주차 선택. 달력에서 월요일만 고를 수 있게 하고, 고른 주의 응답 기간(월 00:00 ~ 수 23:59:59)을 옆에 보여준다.
 * flatpickr 가 CDN 장애 등으로 없으면 브라우저 date 입력으로 내려가되, 어느 날을 골라도 그 주 월요일로 맞춘다.
 * 서버(QuizSetForm.requiredWeek)가 월요일인지 다시 검사하므로 여기서 놓쳐도 잘못 저장되지는 않는다.
 */
(function () {
    var input = document.getElementById('weekStartedOn');
    var periodLabel = document.getElementById('weekPeriodLabel');
    if (!input) return;

    var MONDAY = 1;

    function pad(n) { return n < 10 ? '0' + n : '' + n; }
    function toIsoDate(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
    function fromIsoDate(s) {
        var parts = s.split('-');
        return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    }
    function monthDay(d) { return pad(d.getMonth() + 1) + '.' + pad(d.getDate()); }

    function showPeriod(mondayIso) {
        if (!periodLabel) return;
        if (!mondayIso) { periodLabel.value = '주차를 고르면 표시됩니다'; return; }
        var monday = fromIsoDate(mondayIso);
        var wednesday = new Date(monday); wednesday.setDate(monday.getDate() + 2);
        var sunday = new Date(monday); sunday.setDate(monday.getDate() + 6);
        periodLabel.value = monthDay(monday) + '(월) 00:00 ~ ' + monthDay(wednesday) + '(수) 23:59:59'
            + '  ·  주차 ' + monthDay(monday) + ' ~ ' + monthDay(sunday);
    }

    function snapToMonday(date) {
        var d = new Date(date);
        var diff = (d.getDay() + 6) % 7; // 월=0 … 일=6
        d.setDate(d.getDate() - diff);
        return d;
    }

    if (typeof flatpickr === 'function') {
        var korean = (flatpickr.l10ns && flatpickr.l10ns.ko) || {};
        flatpickr(input, {
            locale: Object.assign({}, korean, { firstDayOfWeek: MONDAY }),
            dateFormat: 'Y-m-d',
            enable: [function (date) { return date.getDay() === MONDAY; }],
            defaultDate: input.value || null,
            onChange: function (selectedDates, dateStr) { showPeriod(dateStr); },
        });
        showPeriod(input.value);
        return;
    }

    input.type = 'date';
    input.step = 7;
    input.addEventListener('change', function () {
        if (!input.value) { showPeriod(''); return; }
        input.value = toIsoDate(snapToMonday(fromIsoDate(input.value)));
        showPeriod(input.value);
    });
    showPeriod(input.value);
})();
