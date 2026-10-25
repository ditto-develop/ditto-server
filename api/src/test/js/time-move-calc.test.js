const { describe, test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const TimeMoveCalc = require(path.join(__dirname, '../../main/resources/static/admin/js/time-move-calc.js'));

// 2026-10-05(월) 주. 그룹 응답 마감은 금 00:00, 채팅 마감은 다음 주 월 00:00 이다.
const week = { groupDeadline: '2026-10-09T00:00', chatEnd: '2026-10-12T00:00' };
const warningsOf = (now, target) => TimeMoveCalc.warningsOf({ ...week, now, target });

describe('warningsOf', () => {
    test('마감을 넘기지 않으면 경고가 없다', () => {
        assert.deepEqual(warningsOf('2026-10-06T12:00', '2026-10-08T23:50'), []);
    });

    test('같은 시각으로 옮기면 경고가 없다', () => {
        assert.deepEqual(warningsOf('2026-10-06T12:00', '2026-10-06T12:00'), []);
    });

    test('그룹 응답 마감 시각에 딱 닿으면 넘긴 것으로 본다', () => {
        assert.deepEqual(warningsOf('2026-10-08T23:50', '2026-10-09T00:00'), ['pastGroupDeadline']);
    });

    test('두 마감을 한 번에 넘기면 둘 다 알린다', () => {
        assert.deepEqual(warningsOf('2026-10-06T12:00', '2026-10-20T00:00'), ['pastGroupDeadline', 'pastChatEnd']);
    });

    test('이미 지난 마감은 다시 알리지 않는다', () => {
        assert.deepEqual(warningsOf('2026-10-09T10:00', '2026-10-12T01:00'), ['pastChatEnd']);
    });

    test('지난 시점으로 가면 마감과 상관없이 되돌아간다고만 알린다', () => {
        assert.deepEqual(warningsOf('2026-10-09T10:00', '2026-10-05T00:00'), ['backToPast']);
    });
});

describe('isIrreversible', () => {
    test('마감을 넘기면 되돌릴 수 없고, 지난 시점으로 가는 것은 되돌릴 수 있다', () => {
        assert.equal(TimeMoveCalc.isIrreversible(['pastChatEnd']), true);
        assert.equal(TimeMoveCalc.isIrreversible(['backToPast']), false);
    });
});
