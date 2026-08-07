const config = window.jpaLabConfig;

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
    callLab('POST', '/seed', 'Seed complete'));

document.getElementById('persist-btn').addEventListener('click', () =>
    callLab('POST', '/lifecycle/persist', 'Lifecycle persist'));

document.getElementById('detach-btn').addEventListener('click', () =>
    callLab('POST', '/lifecycle/detach-mutate', 'Lifecycle detach'));

document.getElementById('nplus1-btn').addEventListener('click', () =>
    callLab('POST', '/loading/nplus1', 'N+1 demo'));

document.getElementById('entity-graph-btn').addEventListener('click', () =>
    callLab('POST', '/loading/entity-graph', 'EntityGraph fix'));

document.getElementById('jpql-btn').addEventListener('click', () =>
    callLab('POST', '/query/jpql', 'JPQL'));

document.getElementById('criteria-btn').addEventListener('click', () =>
    callLab('POST', '/query/criteria', 'Criteria API'));

document.getElementById('spec-btn').addEventListener('click', () =>
    callLab('POST', '/query/spec', 'Specifications'));

document.getElementById('booking-success-btn').addEventListener('click', () =>
    callLab('POST', '/tx/booking-success', 'Booking success'));

document.getElementById('booking-rollback-btn').addEventListener('click', () =>
    callLab('POST', '/tx/booking-rollback', 'Booking rollback'));

document.getElementById('propagation-btn').addEventListener('click', () =>
    callLab('POST', '/tx/propagation', 'Propagation map'));

document.getElementById('deadlock-btn').addEventListener('click', () =>
    callLab('POST', '/tx/deadlock-retry', 'Lock contention'));

async function callLab(method, path, label) {
    try {
        const response = await fetch(`${config.baseUrl}${path}`, {
            method,
            headers: authorizedJsonHeaders()
        });
        const payload = await parseBody(response);
        if (!response.ok) {
            throw new Error(payload.error || payload.message || `${label} failed`);
        }
        outputMeta.textContent = label;
        outputPanel.textContent = JSON.stringify(payload, null, 2);
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
