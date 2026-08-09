const config = window.jobsLabConfig;

const tokenInput = document.getElementById('token-input');
const authFeedback = document.getElementById('auth-feedback');

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
        setFeedback(authFeedback, 'Admin token stored. HTMX actions will send Authorization.', false);
    } catch (error) {
        setFeedback(authFeedback, error.message, true);
    }
});

tokenInput.addEventListener('change', () => {
    localStorage.setItem(config.storageKey, tokenInput.value.trim());
});

document.body.addEventListener('htmx:configRequest', event => {
    const token = (tokenInput.value || localStorage.getItem(config.storageKey) || '').trim();
    if (!token) {
        return;
    }
    event.detail.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
});

document.body.addEventListener('htmx:responseError', event => {
    const status = event.detail.xhr?.status;
    if (status === 401 || status === 403) {
        setFeedback(authFeedback, 'Admin JWT required for job actions. Login above.', true);
    }
});

function setFeedback(el, message, isError) {
    el.textContent = message;
    el.classList.toggle('is-error', Boolean(isError));
}
