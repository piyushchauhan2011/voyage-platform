const config = window.rabbitMqLabConfig;

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

document.getElementById('setup-btn').addEventListener('click', () =>
    callLab('POST', '/setup', null, 'Topology setup'));

document.getElementById('topology-btn').addEventListener('click', () =>
    callLab('GET', '/topology', null, 'Topology'));

document.getElementById('purge-btn').addEventListener('click', () =>
    callLab('POST', '/purge', null, 'Purge'));

document.querySelectorAll('.publish-btn').forEach(button => {
    button.addEventListener('click', () =>
        callLab('POST', '/publish', {
            routingKey: button.dataset.routingKey,
            payload: `ui-${button.dataset.routingKey}`
        }, `Publish ${button.dataset.routingKey}`));
});

document.getElementById('routing-demo-btn').addEventListener('click', () =>
    callLab('POST', '/routing-demo', null, 'Routing demo'));

document.getElementById('consumed-btn').addEventListener('click', () =>
    callLab('GET', '/consumed', null, 'Consumed jobs'));

document.getElementById('compare-btn').addEventListener('click', () =>
    callLab('GET', '/compare', null, 'Kafka vs RabbitMQ'));

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
