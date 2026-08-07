const config = window.aiLabConfig;

const tokenInput = document.getElementById('token-input');
const authFeedback = document.getElementById('auth-feedback');
const outputPanel = document.getElementById('output-panel');
const outputMeta = document.getElementById('output-meta');
const answerText = document.getElementById('answer-text');
const answerMeta = document.getElementById('answer-meta');
const answerTrace = document.getElementById('answer-trace');

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
    callLab('GET', '/status', null, 'Status'));

document.getElementById('seed-btn').addEventListener('click', () =>
    callLab('POST', '/seed-catalog', null, 'Seed catalog'));

document.getElementById('prompt-template-btn').addEventListener('click', () =>
    callLab('POST', '/prompt-template', {city: 'Lisbon', budget: 100, vibe: 'close to the beach'},
        'Prompt template'));

document.getElementById('ingest-btn').addEventListener('click', () =>
    callLab('POST', '/ingest', null, 'Ingest hotels'));

document.getElementById('tools-btn').addEventListener('click', () =>
    callLab('GET', '/tools', null, 'Tool catalog'));

document.getElementById('chat-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    callLab('POST', '/chat', {message: form.get('message')}, 'Chat');
});

document.getElementById('embed-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    callLab('POST', '/embed', {first: form.get('first'), second: form.get('second')}, 'Embedding');
});

document.getElementById('search-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    callLab('POST', '/search', {
        query: form.get('query'),
        filterExpression: form.get('filterExpression')
    }, 'Similarity search');
});

document.getElementById('rag-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    callLab('POST', '/rag', {
        question: form.get('question'),
        filterExpression: form.get('filterExpression')
    }, 'RAG');
});

document.getElementById('assistant-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    callLab('POST', '/assistant', {
        question: form.get('question'),
        conversationId: form.get('conversationId')
    }, 'Assistant');
});

document.getElementById('clear-conversation-btn').addEventListener('click', () => {
    const conversationId = document.querySelector('#assistant-form input[name="conversationId"]').value;
    callLab('POST', '/assistant/clear', {conversationId}, 'Clear conversation');
});

async function callLab(method, path, body, label) {
    setBusy(label);
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
        renderAnswer(payload, label);
    } catch (error) {
        outputMeta.textContent = 'Error';
        outputPanel.textContent = error.message;
        answerMeta.textContent = `${label} failed`;
        answerText.textContent = error.message;
        answerTrace.innerHTML = '';
    }
}

/**
 * Any rung that produces prose gets surfaced in the answer panel, with its retrieved
 * documents and tool calls beside it — the point of the lab is seeing why an answer
 * came out the way it did, not just reading the answer.
 */
function renderAnswer(payload, label) {
    if (typeof payload.answer !== 'string') {
        return;
    }
    answerMeta.textContent = `${label}${payload.tookMs ? ` · ${payload.tookMs} ms` : ''}`;
    answerText.textContent = payload.answer;
    answerTrace.innerHTML = '';

    const documents = payload.retrievedDocuments || [];
    if (documents.length > 0) {
        answerTrace.appendChild(buildTraceBlock(
            `Retrieved ${documents.length} document${documents.length === 1 ? '' : 's'} from pgvector`,
            documents.map(doc => {
                const score = typeof doc.score === 'number' ? doc.score.toFixed(3) : 'n/a';
                return `${doc.name} · ${doc.city} · $${doc.pricePerNight} (score ${score})`;
            })));
    }

    const toolCalls = payload.toolCalls || [];
    if (toolCalls.length > 0) {
        answerTrace.appendChild(buildTraceBlock(
            `Model invoked ${toolCalls.length} tool call${toolCalls.length === 1 ? '' : 's'}`,
            toolCalls.map(call =>
                `${call.tool}(${JSON.stringify(call.arguments)}) → ${call.resultCount} row(s)`)));
    }

    if (payload.usage && payload.usage.totalTokens) {
        answerTrace.appendChild(buildTraceBlock('Tokens', [
            `prompt ${payload.usage.promptTokens ?? '?'} · completion ${payload.usage.completionTokens ?? '?'} · total ${payload.usage.totalTokens}`
        ]));
    }
}

function buildTraceBlock(title, lines) {
    const block = document.createElement('div');
    block.className = 'trace-block';

    const heading = document.createElement('h3');
    heading.textContent = title;
    block.appendChild(heading);

    const list = document.createElement('ul');
    lines.forEach(line => {
        const item = document.createElement('li');
        item.textContent = line;
        list.appendChild(item);
    });
    block.appendChild(list);
    return block;
}

function setBusy(label) {
    outputMeta.textContent = `${label}…`;
    outputPanel.textContent = 'Waiting for the model…';
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
