const config = window.postgresLabConfig;

const tokenInput = document.getElementById('token-input');
const authFeedback = document.getElementById('auth-feedback');
const outputPanel = document.getElementById('output-panel');
const outputMeta = document.getElementById('output-meta');

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

document.getElementById('seed-btn').addEventListener('click', () =>
    callLab('POST', '/seed', null, 'Seed complete'));

document.getElementById('list-indexes-btn').addEventListener('click', () =>
    callLab('GET', '/indexes', null, 'Index status'));

document.querySelectorAll('.index-drop').forEach(button => {
    button.addEventListener('click', () =>
        callLab('POST', `/indexes/${button.dataset.index}/drop`, null, `Dropped ${button.dataset.index}`));
});

document.querySelectorAll('.index-create').forEach(button => {
    button.addEventListener('click', () =>
        callLab('POST', `/indexes/${button.dataset.index}/create`, null, `Created ${button.dataset.index}`));
});

document.querySelectorAll('.explain-btn').forEach(button => {
    button.addEventListener('click', () =>
        callLab('POST', '/explain', {scenario: button.dataset.scenario}, `Explain ${button.dataset.scenario}`));
});

document.getElementById('partition-setup-btn').addEventListener('click', () =>
    callLab('POST', '/partitioning/setup', null, 'Partition setup'));

document.getElementById('partition-explain-btn').addEventListener('click', () =>
    callLab('GET', '/partitioning/explain', null, 'Partition explain'));

document.getElementById('lock-hold-btn').addEventListener('click', () =>
    callLab('POST', '/locks/hold', {seconds: 3}, 'Lock hold'));

document.querySelectorAll('.isolation-btn').forEach(button => {
    button.addEventListener('click', () =>
        callLab('POST', '/isolation/demo', {scenario: button.dataset.scenario}, `Isolation: ${button.dataset.scenario}`));
});

async function callLab(method, path, body, label) {
    try {
        const headers = authorizedJsonHeaders();
        const options = {method, headers};
        if (body !== null && method !== 'GET') {
            options.body = JSON.stringify(body);
        }
        const response = await fetch(`${config.baseUrl}${path}`, options);
        const payload = await parseBody(response);
        if (!response.ok) {
            throw new Error(payload.error || payload.message || `${label} failed`);
        }
        outputMeta.textContent = label;
        if (payload.plan) {
            outputPanel.textContent =
                `SQL:\n${payload.sql}\n\nscanType: ${payload.scanType}  elapsedMs: ${payload.elapsedMs}\n\n${payload.plan}`;
        } else {
            outputPanel.textContent = JSON.stringify(payload, null, 2);
        }
    } catch (error) {
        outputMeta.textContent = 'Error';
        outputPanel.textContent = error.message;
    }
}

function authorizedJsonHeaders() {
    const token = tokenInput.value.trim();
    if (!token) {
        throw new Error('Paste an admin Bearer token first (or login).');
    }
    localStorage.setItem(config.storageKey, token);
    return {
        'Content-Type': 'application/json',
        Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}`
    };
}

async function parseBody(response) {
    const text = await response.text();
    if (!text) {
        return {};
    }
    try {
        return JSON.parse(text);
    } catch {
        return {message: text};
    }
}

function setFeedback(element, message, isError) {
    element.textContent = message;
    element.classList.toggle('error', Boolean(isError));
}
