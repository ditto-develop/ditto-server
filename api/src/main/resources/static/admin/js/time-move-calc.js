// 시간 조정 화면에서 고른 시각으로 옮기기 전에 확인받을 경고. 서버의 QaTimeMoveWarning 과 같은 기준이다.
const TimeMoveCalc = (() => {
    // 시각은 모두 분까지 자른 'yyyy-MM-ddTHH:mm' 문자열이라 문자열 순서가 시각 순서와 같다.
    const warningsOf = ({ now, groupDeadline, chatEnd, target }) => {
        if (target < now) return ['backToPast'];
        const crosses = (boundary) => now < boundary && target >= boundary;
        return [
            crosses(groupDeadline) && 'pastGroupDeadline',
            crosses(chatEnd) && 'pastChatEnd',
        ].filter(Boolean);
    };

    // 지난 시점으로 가는 것은 되돌릴 수 있고, 마감을 넘기는 것은 1분 안에 처리돼 되돌릴 수 없다.
    const isIrreversible = (warnings) => warnings.some((warning) => warning !== 'backToPast');

    return { warningsOf, isIrreversible };
})();

if (typeof module !== 'undefined') module.exports = TimeMoveCalc;
