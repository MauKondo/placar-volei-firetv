// ============================================================================
// script.js — controle do placar via HTTP
//
// Mantém UI sincronizada com o GameState via polling /state a cada 1s.
// Mutações disparam POST imediato; response já é o novo estado → render.
// PIN obrigatório em POST via header X-Pin (salvo em localStorage).
// ============================================================================

const POLL_INTERVAL_MS = 1000;
const RENAME_DEBOUNCE_MS = 600;
const PIN_STORAGE_KEY = 'placarvolei_pin';
const FLASH_DURATION_MS = 340;

// ----- Estado local -----
let lastState = null;
let renameTimer = null;
let isUserTypingA = false;
let isUserTypingB = false;

// ----- Helpers -----
const $ = (id) => document.getElementById(id);

function getPin() {
    return localStorage.getItem(PIN_STORAGE_KEY) || '';
}

function setPin(v) {
    if (v) localStorage.setItem(PIN_STORAGE_KEY, v);
    else localStorage.removeItem(PIN_STORAGE_KEY);
}

function promptPin() {
    const v = prompt('Digite o PIN do placar:');
    if (v) {
        setPin(v.trim());
        return true;
    }
    return false;
}

async function api(path, options = {}) {
    try {
        const res = await fetch(path, options);
        if (res.status === 401) {
            setPin(null);
            alert('PIN inválido. Tente novamente.');
            promptPin();
            return null;
        }
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const state = await res.json();
        setConnStatus(true);
        applyState(state);
        return state;
    } catch (e) {
        setConnStatus(false);
        console.error(path, e);
        return null;
    }
}

function post(path, body) {
    if (!getPin()) {
        if (!promptPin()) return Promise.resolve(null);
    }
    const headers = { 'X-Pin': getPin() };
    if (body) headers['Content-Type'] = 'application/json';
    return api(path, {
        method: 'POST',
        headers,
        body: body ? JSON.stringify(body) : undefined,
    });
}

function setConnStatus(online) {
    const el = $('connStatus');
    el.className = 'status-pill ' + (online ? 'online' : 'offline');
    el.querySelector('.status-text').textContent = online ? 'ao vivo' : 'offline';
}

function modeLabel(mode) {
    switch (mode) {
        case 'ALL_25': return 'ALL 25';
        case 'ALL_15': return 'ALL 15';
        default: return 'FIVB';
    }
}

// ----- DOM updates with diffing + flash on score change -----
function setText(id, val) {
    const el = $(id);
    const str = String(val);
    if (el && el.textContent !== str) el.textContent = str;
}

function setScore(id, val) {
    const el = $(id);
    if (!el) return;
    const str = String(val);
    if (el.textContent === str) return;
    el.textContent = str;
    // restart animation
    el.classList.remove('flash');
    void el.offsetWidth;
    el.classList.add('flash');
    setTimeout(() => el.classList.remove('flash'), FLASH_DURATION_MS + 20);
}

// ----- Render -----
function applyState(s) {
    if (!s) return;
    lastState = s;

    // theme
    const theme = s.theme === 'light' ? 'light' : 'dark';
    if (document.documentElement.dataset.theme !== theme) {
        document.documentElement.dataset.theme = theme;
        $('themeToggleIcon').textContent = theme === 'light' ? '☀' : '☾';
    }

    // mode badge
    $('modeBadge').querySelector('.mode-badge-text').textContent = modeLabel(s.scoringMode);

    // team names — don't overwrite while user types
    if (!isUserTypingA && $('teamAName').value !== s.teamAName) {
        $('teamAName').value = s.teamAName;
    }
    if (!isUserTypingB && $('teamBName').value !== s.teamBName) {
        $('teamBName').value = s.teamBName;
    }

    // scores (with flash animation when changed)
    setScore('teamAPoints', s.teamAPoints);
    setScore('teamBPoints', s.teamBPoints);

    // sets
    setText('teamASets', s.teamASets);
    setText('teamBSets', s.teamBSets);

    // serve indicator
    $('serveDotA').classList.toggle('active', !!s.teamAServing);
    $('serveDotB').classList.toggle('active', !s.teamAServing);
    setText('servingTeam', (s.teamAServing ? s.teamAName : s.teamBName) || '—');

    // selects
    const bestOfEl = $('bestOfSelect');
    if (String(s.bestOf) !== bestOfEl.value) bestOfEl.value = String(s.bestOf);

    const modeEl = $('scoringModeSelect');
    if (s.scoringMode !== modeEl.value) modeEl.value = s.scoringMode;

    // history
    renderHistory(s.history);

    // modals
    $('setCloseModal').hidden = !s.pendingSetClose || s.matchFinished;
    if (s.pendingSetClose) {
        $('modalScore').innerHTML =
            `<span class="modal-side">${s.teamAPoints}</span>` +
            `<span class="modal-vs">×</span>` +
            `<span class="modal-side">${s.teamBPoints}</span>`;
    }

    $('matchFinishedModal').hidden = !s.matchFinished;
    if (s.matchFinished) {
        setText('winnerName', s.winner || '—');
    }
}

function renderHistory(history) {
    const list = $('historyList');
    if (!history || history.length === 0) {
        list.innerHTML = '<span class="history-empty">Nenhum set encerrado.</span>';
        return;
    }
    list.innerHTML = '';
    history.forEach((r, i) => {
        const pill = document.createElement('span');
        pill.className = 'history-pill';
        pill.innerHTML =
            `<span class="history-pill-tag">SET ${i + 1}</span>` +
            `<span>${r.teamAPoints}–${r.teamBPoints}</span>`;
        list.appendChild(pill);
    });
}

// ----- Handlers -----
function setupButtons() {
    document.body.addEventListener('click', (ev) => {
        const btn = ev.target.closest('[data-action]');
        if (!btn) return;
        // ignore select labels — they shouldn't fire actions
        if (btn.tagName === 'LABEL') return;
        const action = btn.dataset.action;
        switch (action) {
            case 'A+': post('/score/A/plus'); break;
            case 'A-': post('/score/A/minus'); break;
            case 'B+': post('/score/B/plus'); break;
            case 'B-': post('/score/B/minus'); break;
            case 'serve': post('/serve/toggle'); break;
            case 'theme': {
                const next = (lastState && lastState.theme === 'light') ? 'dark' : 'light';
                post('/theme', { theme: next });
                break;
            }
            case 'confirm-close': post('/set/confirm-close'); break;
            case 'reject-close': post('/set/reject-close'); break;
            case 'new':
                if (confirm('Iniciar nova partida? O placar atual será descartado.')) {
                    post('/match/new');
                }
                break;
            case 'reset':
                if (confirm('Reset total? Volta tudo aos padrões.')) {
                    post('/match/reset');
                }
                break;
        }
    });
}

function setupNameInputs() {
    const a = $('teamAName');
    const b = $('teamBName');

    a.addEventListener('focus', () => { isUserTypingA = true; });
    a.addEventListener('blur', () => { isUserTypingA = false; sendRename(); });
    b.addEventListener('focus', () => { isUserTypingB = true; });
    b.addEventListener('blur', () => { isUserTypingB = false; sendRename(); });

    a.addEventListener('input', scheduleRename);
    b.addEventListener('input', scheduleRename);

    // submit on Enter — blurs to send
    [a, b].forEach((el) => {
        el.addEventListener('keydown', (ev) => {
            if (ev.key === 'Enter') el.blur();
        });
    });
}

function scheduleRename() {
    clearTimeout(renameTimer);
    renameTimer = setTimeout(sendRename, RENAME_DEBOUNCE_MS);
}

function sendRename() {
    clearTimeout(renameTimer);
    const teamA = $('teamAName').value.trim();
    const teamB = $('teamBName').value.trim();
    if (!teamA && !teamB) return;
    post('/teams/rename', { teamA, teamB });
}

function setupSelects() {
    $('bestOfSelect').addEventListener('change', (e) => {
        post('/match/best-of', { bestOf: parseInt(e.target.value, 10) });
    });
    $('scoringModeSelect').addEventListener('change', (e) => {
        post('/match/scoring-mode', { mode: e.target.value });
    });
}

// ----- Polling -----
function startPolling() {
    api('/state');
    setInterval(() => api('/state'), POLL_INTERVAL_MS);
}

// ----- Init -----
document.addEventListener('DOMContentLoaded', () => {
    setupButtons();
    setupNameInputs();
    setupSelects();
    startPolling();
});
