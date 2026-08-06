const historyConfig = window.kafkaHistoryConfig;
const historyTokenInput = document.querySelector("#history-token-input");
const historyFeedback = document.querySelector("#history-feedback");

function setHistoryFeedback(message, isError = false) {
    historyFeedback.textContent = message;
    historyFeedback.style.color = isError ? "#9f3f1f" : "#27414d";
}

function loadHistoryToken() {
    const storedToken = sessionStorage.getItem(historyConfig.storageKey);
    if (storedToken) {
        historyTokenInput.value = storedToken;
    }
}

function currentHistoryToken() {
    const token = historyTokenInput.value.trim();
    if (!token) {
        throw new Error("Paste an admin Bearer token before retrying a dead-letter record.");
    }
    sessionStorage.setItem(historyConfig.storageKey, token);
    return token.startsWith("Bearer ") ? token : `Bearer ${token}`;
}

async function retryDeadLetter(deadLetterId, payloadOverride) {
    const response = await fetch(`${historyConfig.retryBaseUrl}${deadLetterId}/retry`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: currentHistoryToken()
        },
        body: JSON.stringify({ payloadOverride })
    });

    const rawBody = await response.text();
    const body = rawBody ? JSON.parse(rawBody) : null;

    if (!response.ok) {
        throw new Error(body?.message || `Retry failed with HTTP ${response.status}`);
    }

    return body;
}

document.querySelectorAll(".retry-dead-letter-button").forEach(button => {
    button.addEventListener("click", async () => {
        const deadLetterId = button.dataset.deadLetterId;
        const payloadInput = document.querySelector(`.retry-payload-input[data-dead-letter-id='${deadLetterId}']`);

        try {
            const result = await retryDeadLetter(deadLetterId, payloadInput.value);
            setHistoryFeedback(`Replayed dead-letter #${result.id} to ${result.originalTopic}. Current replay count: ${result.retryCount}.`);
            window.location.reload();
        } catch (error) {
            setHistoryFeedback(error.message, true);
        }
    });
});

loadHistoryToken();