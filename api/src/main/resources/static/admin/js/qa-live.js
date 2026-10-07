// QA 콘솔·방 화면 공용. [data-qa-live] 영역을 같은 주소의 HTML 로 주기적으로 갈아 끼우고, 비동기 폼([data-qa-async])을 보낸다.
// 서버는 일반 폼 POST → 리다이렉트로 답하므로 fetch 가 따라간 화면 HTML 에서 같은 id 영역을 골라 바꾼다.
(() => {
    const POLL_INTERVAL_MS = 3000;
    const NEAR_BOTTOM_PX = 40;
    const LOGIN_PATH = '/admin/login';
    const LIVE_TEXT = `${POLL_INTERVAL_MS / 1000}초마다 자동 갱신`;
    // 알림 슬롯이 속한 영역. 자동 갱신 영역이거나, 갈아 끼우지 않아 입력이 남는 신고 카드다.
    const ALERT_SCOPE = '[data-qa-live], [data-qa-alert-scope]';

    // 제출이 끝날 때마다 올린다. 그 전에 출발한 폴링 응답은 낡은 화면이라 버린다.
    let submitGeneration = 0;
    let submitting = false;
    let polling = false;

    const timeline = () => document.getElementById('qa-timeline');

    function isNearBottom(element) {
        return element.scrollHeight - element.scrollTop - element.clientHeight < NEAR_BOTTOM_PX;
    }

    function scrollTimelineToBottom() {
        const element = timeline();
        if (element) element.scrollTop = element.scrollHeight;
    }

    function showLiveState(text, healthy) {
        const state = document.getElementById('qa-live-state');
        if (!state) return;
        state.textContent = text;
        state.classList.toggle('stopped', !healthy);
    }

    function showError(message) {
        const alerts = document.getElementById('qa-alerts');
        if (!alerts) return;
        alerts.innerHTML = '';
        const alert = document.createElement('div');
        alert.className = 'alert error';
        alert.textContent = message;
        alerts.appendChild(alert);
    }

    function showSubmitError(message, submittedAlertScope) {
        showError(message);
        showInlineAlerts(submittedAlertScope?.id);
    }

    // 같은 이름의 칸이 폼마다 반복되는 영역(평가)은 폼 id 로 짝을 찾는다. id 가 없는 폼은 select 만 이름으로 찾는다.
    function replacementOf(field, next) {
        const formId = field.form?.id;
        if (formId) return next.querySelector(`#${CSS.escape(formId)} [name="${field.name}"]`);
        return field.tagName === 'SELECT' ? next.querySelector(`select[name="${field.name}"]`) : null;
    }

    // 고르던 더미(select)와 쓰던 코멘트는 갈아 끼운 뒤에도 그대로 둔다. 남은 선택지에 없으면 첫 항목으로 돌아간다.
    function keepFieldValues(current, next) {
        current.querySelectorAll('select[name], input[type="text"][name]').forEach((field) => {
            const replacement = replacementOf(field, next);
            if (!replacement) return;
            if (field.tagName === 'SELECT' && ![...replacement.options].some((option) => option.value === field.value)) return;
            replacement.value = field.value;
        });
    }

    // 영역 안에 띄운 결과 알림은 다음 제출 전까지 남긴다.
    function keepInlineAlerts(current, next) {
        const slot = current.querySelector('[data-qa-inline-alerts]');
        const nextSlot = next.querySelector('[data-qa-inline-alerts]');
        if (slot && nextSlot) nextSlot.replaceChildren(...slot.childNodes);
    }

    // 결과 알림은 맨 위에 뜨는데, 화면 아래쪽에서 낸 것이면 보이지 않으니 그 영역 안에도 띄운다.
    // 다른 영역에서 낸 뒤에는 지난 결과가 남지 않게 비운다. 영역은 이미 갈아 끼워져 id 로 다시 찾는다.
    function showInlineAlerts(submittedScopeId) {
        const alerts = [...(document.getElementById('qa-alerts')?.children ?? [])];
        document.querySelectorAll('[data-qa-inline-alerts]').forEach((slot) => {
            if (slot.closest(ALERT_SCOPE)?.id !== submittedScopeId) {
                slot.replaceChildren();
                return;
            }
            slot.replaceChildren(...alerts.map((alert) => alert.cloneNode(true)));
            slot.scrollIntoView({ block: 'nearest' });
        });
    }

    // 손대는 중인 영역(평가)은 갈아 끼우면 열린 선택지가 닫히고 커서가 사라지니 건너뛴다.
    // 제출 뒤 포커스가 돌아간 버튼은 손대는 중이 아니다. 그것까지 막으면 실패한 제출 뒤 영역이 계속 옛 화면으로 남는다.
    function isBeingEdited(region) {
        if (region.dataset.editing === 'true') return true;
        const active = document.activeElement;
        return region.hasAttribute('data-qa-hold-while-editing')
            && region.contains(active)
            && active.matches('input, select, textarea');
    }

    // 체크를 고르던 영역(투표)과 손대는 중인 영역(평가)은 건너뛴다. 방금 제출한 폼이 속한 영역만은 결과를 보여야 해서 바꾼다.
    // 스크롤을 올려 지난 대화를 보는 중이면 갱신이 바닥으로 끌어내리지 않는다.
    function swapLiveRegions(doc, submittedRegion = null) {
        const current = timeline();
        const stickToBottom = !current || isNearBottom(current);
        document.querySelectorAll('[data-qa-live]').forEach((region) => {
            if (isBeingEdited(region) && region !== submittedRegion) return;
            const next = doc.getElementById(region.id);
            if (!next) return;
            keepFieldValues(region, next);
            keepInlineAlerts(region, next);
            region.replaceWith(next);
        });
        if (stickToBottom) scrollTimelineToBottom();
    }

    function swapAlerts(doc) {
        const next = doc.getElementById('qa-alerts');
        const current = document.getElementById('qa-alerts');
        if (next && current) current.replaceWith(next);
    }

    function parse(html) {
        return new DOMParser().parseFromString(html, 'text/html');
    }

    function isLoggedOut(response) {
        return response.redirected && new URL(response.url).pathname.startsWith(LOGIN_PATH);
    }

    async function refresh() {
        if (polling || submitting || document.hidden) return;
        polling = true;
        const startedGeneration = submitGeneration;
        try {
            const response = await fetch(window.location.pathname, { credentials: 'same-origin' });
            if (isLoggedOut(response)) {
                showLiveState('갱신 멈춤: 세션이 끝났습니다. 다시 로그인하세요.', false);
                return;
            }
            if (!response.ok) {
                showLiveState(`갱신 실패(${response.status}), 다시 시도 중`, false);
                return;
            }
            const html = await response.text();
            if (startedGeneration !== submitGeneration || submitting) return;
            swapLiveRegions(parse(html));
            const editing = document.querySelector('[data-qa-live][data-editing="true"]');
            showLiveState(editing ? `${LIVE_TEXT} (고르던 투표 영역은 제출 전까지 멈춤)` : LIVE_TEXT, true);
        } catch (ignored) {
            showLiveState('갱신 실패(네트워크), 다시 시도 중', false);
        } finally {
            polling = false;
        }
    }

    function fieldNamesIn(value) {
        return (value ?? '').split(' ').filter(Boolean);
    }

    // 빈 값 보기('회원 ID를 직접 넣으세요' 같은 안내)는 고른 값으로 치지 않는다.
    function chosenTextOf(form, name) {
        const field = form.elements[name];
        if (!field?.value) return null;
        if (field.tagName === 'SELECT') return field.selectedOptions?.[0]?.text ?? null;
        return `${field.dataset.confirmPrefix ?? ''}${field.value}`;
    }

    // 'typedMemberId|listedMemberId' 처럼 앞 칸이 비었으면 다음 칸을 쓴다.
    function firstChosenTextOf(form, alternatives) {
        return alternatives.split('|').map((name) => chosenTextOf(form, name)).find(Boolean) ?? null;
    }

    // 직접 넣은 값이 함께 있는 목록에 없으면(다른 회원 ID 를 잘못 친 경우 등) 한 번 더 알린다.
    function unlistedWarningsOf(form) {
        return [...form.elements]
            .filter((field) => field.dataset?.confirmUnlistedIn && field.value)
            .filter((field) => {
                const list = form.elements[field.dataset.confirmUnlistedIn];
                return list && ![...list.options].some((option) => option.value === field.value);
            })
            .map((field) => field.dataset.confirmUnlisted);
    }

    function checkedFlagLabelsOf(form) {
        return fieldNamesIn(form.dataset.confirmFlags)
            .map((name) => form.elements[name])
            .filter((field) => field?.checked)
            .map((field) => field.dataset.confirmLabel);
    }

    // 확인 창에 고른 값(일괄 평가의 재매칭 의사, 신고자와 대상), 대상 경고, 체크한 선택을 함께 보여
    // 실수로 누른 것을 알아차리게 한다.
    function withConfirmDetails(form, message) {
        if (!message) return message;
        const chosen = fieldNamesIn(form.dataset.confirmChoice).map((names) => firstChosenTextOf(form, names));
        const details = [chosen.filter(Boolean).join(' → '), ...unlistedWarningsOf(form), ...checkedFlagLabelsOf(form)];
        return [message, ...details].filter(Boolean).join('\n');
    }

    // 성공한 뒤 다음 신고에 그대로 실리면 안 되는 칸(차단·상세)을 비운다. 거부되면 고쳐 다시 내게 남긴다.
    function resetFieldsAfterSuccess(form, failed) {
        if (failed) return;
        fieldNamesIn(form.dataset.resetOnSuccess).forEach((name) => {
            const field = form.elements[name];
            if (!field) return;
            if (field.type === 'checkbox') field.checked = false;
            else field.value = '';
        });
    }

    function setButtonsDisabled(form, disabled) {
        form.querySelectorAll('button').forEach((button) => { button.disabled = disabled; });
    }

    // 실패하면 친 메시지를 지우지 않는다. 빠른 입력은 입력창을 쓰지 않았으니 건드리지 않는다.
    function clearComposerIfSent(form, submitter, failed) {
        if (form.id !== 'qa-composer' || submitter?.name === 'preset' || failed) return;
        form.elements.content.value = '';
        form.elements.content.focus();
    }

    // 체크를 모두 풀면 고르던 것이 없으니 다시 갱신한다.
    document.addEventListener('change', (event) => {
        if (!event.target.matches('input[type="checkbox"], input[type="radio"]')) return;
        const region = event.target.closest('[data-qa-live]');
        if (!region) return;
        const anyChecked = region.querySelector('input[type="checkbox"]:checked, input[type="radio"]:checked') !== null;
        if (anyChecked) region.dataset.editing = 'true';
        else delete region.dataset.editing;
    });

    // 버튼마다 다른 엔드포인트(formaction)와 확인 문구(data-confirm)를 둘 수 있다.
    document.addEventListener('submit', async (event) => {
        const form = event.target;
        // 일반 폼은 곧 페이지가 바뀐다. 그 사이 폴링이 영역을 갈아 끼우면 잠근 버튼이 되살아나고 결과 알림을 가로챌 수 있다.
        // 확인 창에서 취소한 제출(qa-forms.js 가 먼저 막는다)은 그대로 둔다.
        if (!form.matches('form[data-qa-async]')) {
            if (!event.defaultPrevented) submitting = true;
            return;
        }
        event.preventDefault();
        const submitter = event.submitter;
        const confirmMessage = withConfirmDetails(form, submitter?.dataset.confirm || form.dataset.confirm);
        if (confirmMessage && !window.confirm(confirmMessage)) return;

        const body = new FormData(form, submitter);
        const action = submitter?.hasAttribute('formaction') ? submitter.formAction : form.action;
        const submittedLiveRegion = form.closest('[data-qa-live]');
        const submittedAlertScope = form.closest(ALERT_SCOPE);
        submitting = true;
        setButtonsDisabled(form, true);
        try {
            const response = await fetch(action, { method: 'POST', body, credentials: 'same-origin' });
            if (isLoggedOut(response)) {
                showSubmitError('세션이 끝나 요청이 처리되지 않았습니다. 다시 로그인하세요.', submittedAlertScope);
                return;
            }
            const doc = parse(await response.text());
            // CSRF 거부(403)나 서버 오류 JSON 처럼 화면이 아닌 응답은 결과를 알 수 없다.
            if (!response.ok || doc.getElementById('qa-alerts') === null) {
                showSubmitError(`요청이 처리됐는지 확인하지 못했습니다(${response.status}). 새로고침해서 확인하세요.`, submittedAlertScope);
                return;
            }
            // 갈아 끼우면 노드가 응답 문서에서 빠져나오므로 오류 여부는 그 전에 본다.
            const failed = doc.querySelector('#qa-alerts .alert.error') !== null;
            swapAlerts(doc);
            swapLiveRegions(doc, submittedLiveRegion);
            showInlineAlerts(submittedAlertScope?.id);
            clearComposerIfSent(form, submitter, failed);
            resetFieldsAfterSuccess(form, failed);
            if (form.id === 'qa-composer') scrollTimelineToBottom();
        } catch (ignored) {
            showSubmitError('네트워크 오류로 요청 결과를 확인하지 못했습니다. 새로고침해서 확인하세요.', submittedAlertScope);
        } finally {
            submitGeneration += 1;
            submitting = false;
            setButtonsDisabled(form, false);
            // 잠근 버튼에서 포커스가 빠져 키보드로 쓰던 사람이 처음부터 다시 탭하지 않게 되돌린다.
            if (document.activeElement === document.body && submitter?.isConnected) submitter.focus();
        }
    });

    showLiveState(LIVE_TEXT, true);
    scrollTimelineToBottom();
    window.setInterval(refresh, POLL_INTERVAL_MS);
})();
