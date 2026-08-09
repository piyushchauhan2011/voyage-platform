const config = window.searchLabConfig;

const tokenInput = document.getElementById('token-input');
const authFeedback = document.getElementById('auth-feedback');
const labOutput = document.getElementById('lab-output');

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
    // Refresh HTMX results after the index is rebuilt
    const results = document.getElementById('search-results');
    if (results && window.htmx) {
        window.htmx.trigger(results, 'load');
    }
});

document.getElementById('explain-btn').addEventListener('click', () => {
    const q = document.getElementById('search-q').value || 'ชายหาด';
    callLab('GET', `/explain?q=${encodeURIComponent(q)}&lang=th`, 'Explain');
});

tokenInput.addEventListener('change', () => {
    localStorage.setItem(config.storageKey, tokenInput.value.trim());
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
