// QA 콘솔 방 화면. 폼은 페이지를 다시 그리지 않고 보내고, [data-qa-live] 영역만 주기적으로 갈아 끼운다.
// 서버는 일반 폼 POST → 리다이렉트로 답하므로 fetch 가 따라간 방 화면 HTML 에서 같은 id 영역을 골라 바꾼다.
(() => {
    const POLL_INTERVAL_MS = 3000;
    const NEAR_BOTTOM_PX = 40;
    const LOGIN_PATH = '/admin/login';

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

    // 고르던 더미(select)는 갈아 끼운 뒤에도 그대로 둔다. 남은 선택지에 없으면 첫 항목으로 돌아간다.
    function keepSelectedValues(current, next) {
        current.querySelectorAll('select[name]').forEach((select) => {
            const replacement = next.querySelector(`select[name="${select.name}"]`);
            if (replacement && [...replacement.options].some((option) => option.value === select.value)) {
                replacement.value = select.value;
            }
        });
    }

    // 체크를 고르던 영역(투표)은 건너뛴다. 방금 제출한 폼이 속한 영역만은 결과를 보여야 해서 바꾼다.
    // 스크롤을 올려 지난 대화를 보는 중이면 갱신이 바닥으로 끌어내리지 않는다.
    function swapLiveRegions(doc, submittedRegion = null) {
        const current = timeline();
        const stickToBottom = !current || isNearBottom(current);
        document.querySelectorAll('[data-qa-live]').forEach((region) => {
            if (region.dataset.editing === 'true' && region !== submittedRegion) return;
            const next = doc.getElementById(region.id);
            if (!next) return;
            keepSelectedValues(region, next);
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
            showLiveState(editing ? '3초마다 자동 갱신 (고르던 투표 영역은 제출 전까지 멈춤)' : '3초마다 자동 갱신', true);
        } catch (ignored) {
            showLiveState('갱신 실패(네트워크), 다시 시도 중', false);
        } finally {
            polling = false;
        }
    }

    function setButtonsDisabled(form, disabled) {
        form.querySelectorAll('button').forEach((button) => { button.disabled = disabled; });
    }

    // 결과 화면에 오류 알림이 없을 때만 성공으로 본다. 실패하면 친 메시지를 지우지 않는다.
    function clearComposerIfSent(form, submitter, doc) {
        if (form.id !== 'qa-composer' || submitter?.name === 'preset') return;
        if (doc.querySelector('#qa-alerts .alert.error')) return;
        form.elements.content.value = '';
        form.elements.content.focus();
    }

    document.addEventListener('change', (event) => {
        if (!event.target.matches('input[type="checkbox"], input[type="radio"]')) return;
        const region = event.target.closest('[data-qa-live]');
        if (region) region.dataset.editing = 'true';
    });

    // 버튼마다 다른 엔드포인트(formaction)와 확인 문구(data-confirm)를 둘 수 있다.
    document.addEventListener('submit', async (event) => {
        const form = event.target;
        if (!form.matches('form[data-qa-async]')) return;
        event.preventDefault();
        const submitter = event.submitter;
        const confirmMessage = submitter?.dataset.confirm || form.dataset.confirm;
        if (confirmMessage && !window.confirm(confirmMessage)) return;

        const body = new FormData(form, submitter);
        const action = submitter?.hasAttribute('formaction') ? submitter.formAction : form.action;
        const submittedRegion = form.closest('[data-qa-live]');
        submitting = true;
        setButtonsDisabled(form, true);
        try {
            const response = await fetch(action, { method: 'POST', body, credentials: 'same-origin' });
            if (isLoggedOut(response)) {
                showError('세션이 끝나 요청이 처리되지 않았습니다. 다시 로그인하세요.');
                return;
            }
            const doc = parse(await response.text());
            swapAlerts(doc);
            swapLiveRegions(doc, submittedRegion);
            clearComposerIfSent(form, submitter, doc);
            if (form.id === 'qa-composer') scrollTimelineToBottom();
        } catch (ignored) {
            showError('네트워크 오류로 요청 결과를 확인하지 못했습니다. 새로고침해서 확인하세요.');
        } finally {
            submitGeneration += 1;
            submitting = false;
            setButtonsDisabled(form, false);
        }
    });

    scrollTimelineToBottom();
    window.setInterval(refresh, POLL_INTERVAL_MS);
})();
