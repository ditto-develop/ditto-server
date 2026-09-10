/**
 * 퀴즈셋 폼의 문항 편집.
 * 순서는 DOM 순서가 곧 값이라, 제출 직전에 name 을 quizzes[i].* 로 다시 매긴다.
 */
(function () {
    var list = document.getElementById('quizList');
    var form = document.getElementById('quizSetForm');
    if (!list || !form) return;

    var template = document.getElementById('quizRowTemplate');
    var errorBox = document.getElementById('formError');
    var countLabel = document.getElementById('quizCount');
    var dragging = null;
    var MIN_CHOICE_COUNT = 2;

    function cards() {
        return Array.prototype.slice.call(list.querySelectorAll('.qcard'));
    }

    function renumber() {
        var all = cards();
        all.forEach(function (card, index) {
            card.querySelector('.n').textContent = index + 1;
            card.querySelector('.quiz-id').name = 'quizzes[' + index + '].id';
            card.querySelector('.qtext').name = 'quizzes[' + index + '].question';

            Array.prototype.forEach.call(card.querySelectorAll('.cbox'), function (box, choiceIndex) {
                box.querySelector('.cmark').textContent = choiceIndex + 1;
                box.querySelector('.choice-id').name =
                    'quizzes[' + index + '].choices[' + choiceIndex + '].id';
                box.querySelector('.choice-content').name =
                    'quizzes[' + index + '].choices[' + choiceIndex + '].content';
            });

            card.querySelector('.move-up').disabled = index === 0;
            card.querySelector('.move-down').disabled = index === all.length - 1;
        });
        countLabel.textContent = all.length;
    }

    function isFilled(card) {
        var question = card.querySelector('.qtext').value.trim();
        var contents = Array.prototype.map.call(
            card.querySelectorAll('.choice-content'),
            function (input) { return input.value.trim(); }
        );
        return question !== '' || contents.some(function (text) { return text !== ''; });
    }

    /** 기존 행(id 있음)이 아니면서 아무것도 안 채운 행. 서버는 빈 행을 거부하므로 제출 전에 뺀다. */
    function isDiscardableNewRow(card) {
        return card.querySelector('.quiz-id').value.trim() === '' && !isFilled(card);
    }

    function incompleteRows() {
        return cards()
            .map(function (card, index) { return { card: card, no: index + 1 }; })
            .filter(function (row) {
                var question = row.card.querySelector('.qtext').value.trim();
                var contents = Array.prototype.map.call(
                    row.card.querySelectorAll('.choice-content'),
                    function (input) { return input.value.trim(); }
                );
                var tooFewChoices = contents.length < MIN_CHOICE_COUNT;
                var blank = contents.some(function (text) { return text === ''; });
                return question === '' || tooFewChoices || blank;
            })
            .map(function (row) { return row.no; });
    }

    function move(card, offset) {
        var all = cards();
        var from = all.indexOf(card);
        var to = from + offset;
        if (to < 0 || to >= all.length) return;

        if (offset < 0) {
            list.insertBefore(card, all[to]);
        } else {
            list.insertBefore(all[to], card);
        }
        renumber();
    }

    function clearDropMarks() {
        cards().forEach(function (card) {
            card.classList.remove('drop-above', 'drop-below', 'dragging');
        });
    }

    list.addEventListener('click', function (event) {
        var target = event.target;
        var card = target.closest('.qcard');
        if (!card) return;

        if (target.classList.contains('move-up')) {
            move(card, -1);
            return;
        }
        if (target.classList.contains('move-down')) {
            move(card, 1);
            return;
        }
        if (target.classList.contains('remove-quiz')) {
            if (card.dataset.answered === 'true') return;
            card.remove();
            renumber();
            return;
        }
        if (target.classList.contains('swap')) {
            var boxes = card.querySelectorAll('.cbox');
            if (boxes.length < 2) return;
            card.querySelector('.choices2').insertBefore(boxes[1], boxes[0]);
            renumber();
        }
    });

    list.addEventListener('dragstart', function (event) {
        var handle = event.target.closest('.qnum');
        if (!handle) return;
        dragging = handle.closest('.qcard');
        dragging.classList.add('dragging');
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', '');
    });

    list.addEventListener('dragover', function (event) {
        if (!dragging) return;
        var over = event.target.closest('.qcard');
        if (!over || over === dragging) return;

        event.preventDefault();
        clearDropMarks();
        dragging.classList.add('dragging');
        var all = cards();
        over.classList.add(all.indexOf(dragging) < all.indexOf(over) ? 'drop-below' : 'drop-above');
    });

    list.addEventListener('drop', function (event) {
        if (!dragging) return;
        var over = event.target.closest('.qcard');
        if (!over || over === dragging) return;

        event.preventDefault();
        var all = cards();
        if (all.indexOf(dragging) < all.indexOf(over)) {
            list.insertBefore(dragging, over.nextSibling);
        } else {
            list.insertBefore(dragging, over);
        }
        dragging = null;
        clearDropMarks();
        renumber();
    });

    list.addEventListener('dragend', function () {
        dragging = null;
        clearDropMarks();
    });

    document.getElementById('addQuiz').addEventListener('click', function () {
        list.appendChild(template.content.cloneNode(true));
        renumber();
        var texts = list.querySelectorAll('.qtext');
        texts[texts.length - 1].focus();
    });

    form.addEventListener('submit', function (event) {
        cards().filter(isDiscardableNewRow).forEach(function (card) { card.remove(); });
        renumber();

        var incomplete = incompleteRows();
        if (incomplete.length === 0) {
            errorBox.hidden = true;
            return;
        }

        event.preventDefault();
        errorBox.textContent = incomplete.join('번, ') + '번 문항의 질문과 선택지 2개를 모두 채워야 합니다.' +
            ' 지우려면 문항 삭제를 누르세요.';
        errorBox.hidden = false;
        errorBox.scrollIntoView({ block: 'center' });
    });

    renumber();
})();
