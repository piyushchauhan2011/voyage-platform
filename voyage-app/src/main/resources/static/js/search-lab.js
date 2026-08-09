const config = window.searchLabConfig;

const tokenInput = document.getElementById('token-input');
const authFeedback = document.getElementById('auth-feedback');
const labOutput = document.getElementById('lab-output');
const searchInput = document.getElementById('search-q');
const suggestList = document.getElementById('suggest-list');
const searchForm = document.getElementById('hotel-search-form');
const detailDialog = document.getElementById('hotel-detail-dialog');
const detailBody = document.getElementById('hotel-detail-body');
const detailClose = document.getElementById('hotel-dialog-close');

tokenInput.value = localStorage.getItem(config.storageKey) || '';

document.getElementById('login-form').addEventListener('submit', async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    try {
        const response = await fetch(config.loginUrl, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                username: formData.get('username'),
                password: formData.get('password')
            })
        });
        const body = await response.json();
        if (!response.ok) {
            throw new Error(body.message || body.error || 'Login failed');
        }
        const token = `Bearer ${body.accessToken}`;
        tokenInput.value = token;
        localStorage.setItem(config.storageKey, token);
        setFeedback(authFeedback, 'Admin token stored.', false);
    } catch (error) {
        setFeedback(authFeedback, error.message, true);
    }
});

document.getElementById('status-btn').addEventListener('click', () =>
    callLab('GET', '/status', 'Status'));

document.getElementById('seed-btn').addEventListener('click', () =>
    callLab('POST', '/seed?count=100', 'Seed hotels'));

document.getElementById('reindex-btn').addEventListener('click', async () => {
    await callLab('POST', '/reindex', 'Reindex');
    const results = document.getElementById('search-results');
    if (results && window.htmx) {
        window.htmx.trigger(results, 'load');
    }
});

document.getElementById('explain-btn').addEventListener('click', () => {
    const q = searchInput.value || 'ชายหาด';
    callLab('GET', `/explain?q=${encodeURIComponent(q)}&lang=th`, 'Explain');
});

tokenInput.addEventListener('change', () => {
    localStorage.setItem(config.storageKey, tokenInput.value.trim());
});

// --- Autocomplete: debounce (wait for pause) + throttle (cap request rate) ---
// Debounce: only run after the user stops typing for DEBOUNCE_MS.
// Throttle: even after debounce fires, never hit the network more than once per THROTTLE_MS.
const DEBOUNCE_MS = 280;
const THROTTLE_MS = 300;
let debounceTimer = null;
let lastSuggestAt = 0;
let suggestAbort = null;
let activeSuggestIndex = -1;

function debounce(fn, waitMs) {
    return (...args) => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => fn(...args), waitMs);
    };
}

function throttleAsync(fn, waitMs) {
    let pendingArgs = null;
    let trailingTimer = null;
    return async (...args) => {
        const now = Date.now();
        const remaining = waitMs - (now - lastSuggestAt);
        if (remaining <= 0) {
            lastSuggestAt = Date.now();
            return fn(...args);
        }
        pendingArgs = args;
        if (!trailingTimer) {
            trailingTimer = setTimeout(async () => {
                trailingTimer = null;
                if (pendingArgs) {
                    const queued = pendingArgs;
                    pendingArgs = null;
                    lastSuggestAt = Date.now();
                    await fn(...queued);
                }
            }, remaining);
        }
    };
}

const runSuggestThrottled = throttleAsync(fetchSuggestions, THROTTLE_MS);
const scheduleSuggest = debounce(() => {
    runSuggestThrottled(searchInput.value.trim());
}, DEBOUNCE_MS);

searchInput.addEventListener('input', () => {
    activeSuggestIndex = -1;
    scheduleSuggest();
});

searchInput.addEventListener('keydown', event => {
    const items = [...suggestList.querySelectorAll('[role="option"]')];
    if (event.key === 'Escape') {
        hideSuggestions();
        return;
    }
    if (!items.length || suggestList.hidden) {
        return;
    }
    if (event.key === 'ArrowDown') {
        event.preventDefault();
        activeSuggestIndex = (activeSuggestIndex + 1) % items.length;
        highlightSuggest(items);
    } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        activeSuggestIndex = (activeSuggestIndex - 1 + items.length) % items.length;
        highlightSuggest(items);
    } else if (event.key === 'Enter' && activeSuggestIndex >= 0) {
        event.preventDefault();
        items[activeSuggestIndex].click();
    }
});

document.addEventListener('click', event => {
    if (!event.target.closest('.suggest-wrap')) {
        hideSuggestions();
    }
});

async function fetchSuggestions(q) {
    if (!q) {
        hideSuggestions();
        return;
    }
    if (suggestAbort) {
        suggestAbort.abort();
    }
    suggestAbort = new AbortController();
    try {
        const url = `${config.suggestUrl}?q=${encodeURIComponent(q)}&lang=${encodeURIComponent(config.lang || 'en')}&size=8`;
        const response = await fetch(url, {signal: suggestAbort.signal});
        if (!response.ok) {
            throw new Error(`Suggest failed (${response.status})`);
        }
        const suggestions = await response.json();
        renderSuggestions(suggestions);
    } catch (error) {
        if (error.name !== 'AbortError') {
            hideSuggestions();
        }
    }
}

function renderSuggestions(suggestions) {
    suggestList.innerHTML = '';
    activeSuggestIndex = -1;
    if (!suggestions.length) {
        const empty = document.createElement('li');
        empty.className = 'suggest-empty';
        empty.textContent = suggestList.dataset.empty || 'No suggestions';
        suggestList.appendChild(empty);
        showSuggestions();
        return;
    }
    suggestions.forEach(item => {
        const li = document.createElement('li');
        li.setAttribute('role', 'option');
        li.className = 'suggest-item';
        const useThai = (config.lang || 'en') === 'th' && item.labelTh;
        const label = useThai ? item.labelTh : item.label;
        const city = useThai && item.cityTh ? item.cityTh : item.city;
        li.innerHTML = `
            ${item.imageUrl ? `<img src="${item.imageUrl}" alt="" width="48" height="48" loading="lazy">` : ''}
            <span class="suggest-text">
                <strong>${escapeHtml(label || '')}</strong>
                <small>${escapeHtml(city || '')}</small>
            </span>`;
        li.addEventListener('click', () => applySuggestion(label || item.label || ''));
        suggestList.appendChild(li);
    });
    showSuggestions();
}

function applySuggestion(label) {
    searchInput.value = label;
    hideSuggestions();
    if (window.htmx) {
        window.htmx.trigger(searchForm, 'submit');
    } else {
        searchForm.requestSubmit();
    }
}

function highlightSuggest(items) {
    items.forEach((item, index) => {
        item.classList.toggle('is-active', index === activeSuggestIndex);
    });
}

function showSuggestions() {
    suggestList.hidden = false;
    searchInput.setAttribute('aria-expanded', 'true');
}

function hideSuggestions() {
    suggestList.hidden = true;
    searchInput.setAttribute('aria-expanded', 'false');
    activeSuggestIndex = -1;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}

// --- Hotel detail dialog ---
document.getElementById('search-results').addEventListener('click', async event => {
    const button = event.target.closest('.hotel-details-btn');
    if (!button) {
        return;
    }
    const hotelId = button.getAttribute('data-hotel-id');
    if (!hotelId) {
        return;
    }
    detailBody.innerHTML = '<p class="muted">…</p>';
    detailDialog.showModal();
    try {
        const response = await fetch(`${config.detailFragmentBase}/${hotelId}`);
        if (!response.ok) {
            throw new Error(`Detail failed (${response.status})`);
        }
        detailBody.innerHTML = await response.text();
    } catch (error) {
        detailBody.innerHTML = `<p class="feedback error">${escapeHtml(error.message)}</p>`;
    }
});

detailClose.addEventListener('click', () => detailDialog.close());
detailDialog.addEventListener('click', event => {
    if (event.target === detailDialog) {
        detailDialog.close();
    }
});

async function callLab(method, path, label) {
    labOutput.textContent = `${label}…`;
    try {
        const headers = {};
        const token = tokenInput.value.trim();
        if (token) {
            headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
        }
        const response = await fetch(`${config.playgroundBase}${path}`, {method, headers});
        const text = await response.text();
        let body;
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
        if (!response.ok) {
            const message = body && body.message ? body.message : text;
            throw new Error(message || `${label} failed (${response.status})`);
        }
        labOutput.textContent = JSON.stringify(body, null, 2);
        setFeedback(authFeedback, `${label} ok.`, false);
    } catch (error) {
        labOutput.textContent = error.message;
        setFeedback(authFeedback, error.message, true);
    }
}

function setFeedback(el, message, isError) {
    el.textContent = message;
    el.classList.toggle('error', Boolean(isError));
}
