const config = window.kafkaDashboardConfig;
const storageKey = "voyage.kafka.accessToken";

const tokenInput = document.querySelector("#token-input");
const authFeedback = document.querySelector("#auth-feedback");
const actionFeedback = document.querySelector("#action-feedback");
const eventList = document.querySelector("#event-list");
const deadLetterList = document.querySelector("#dead-letter-list");
const hotelList = document.querySelector("#hotel-list");

function setFeedback(element, message, isError = false) {
    element.textContent = message;
    element.style.color = isError ? "#9f3f1f" : "#27414d";
}

function loadStoredToken() {
    const storedToken = sessionStorage.getItem(storageKey);
    if (storedToken) {
        tokenInput.value = storedToken;
    }
}

function persistToken() {
    sessionStorage.setItem(storageKey, tokenInput.value.trim());
}

function currentToken() {
    return tokenInput.value.trim();
}

function authHeaders() {
    const token = currentToken();
    if (!token) {
        throw new Error("An admin access token is required for hotel writes.");
    }

    return {
        "Content-Type": "application/json",
        Authorization: token.startsWith("Bearer ") ? token : `Bearer ${token}`
    };
}

async function jsonRequest(url, options = {}) {
    const response = await fetch(url, options);
    const rawBody = await response.text();
    const jsonBody = rawBody ? JSON.parse(rawBody) : null;

    if (!response.ok) {
        const message = jsonBody?.message || `Request failed with HTTP ${response.status}`;
        throw new Error(message);
    }

    return jsonBody;
}

function renderHotels(hotels) {
    if (!hotels.length) {
        hotelList.innerHTML = '<p class="feedback">No hotels yet. Create one from the console above.</p>';
        return;
    }

    hotelList.innerHTML = hotels.map(hotel => `
        <article class="hotel-card">
            <div>
                <p class="hotel-name">${hotel.name}</p>
                <p class="hotel-meta">${hotel.city} · ${hotel.pricePerNight}</p>
            </div>
            <span class="pill">#${hotel.id}</span>
        </article>
    `).join("");
}

function renderEvents(events) {
    if (!events.length) {
        eventList.innerHTML = '<p class="feedback">No events consumed yet. Create or update a hotel to populate this feed.</p>';
        return;
    }

    eventList.innerHTML = events.map(event => `
        <article class="event-card">
            <div class="event-topline">
                <span class="pill">${event.eventType}</span>
                <span>hotel #${event.hotelId}</span>
                <span>offset ${event.kafkaOffset}</span>
            </div>
            <p class="event-name">${event.hotelName}</p>
            <p class="event-meta">partition ${event.partitionId} · key ${event.messageKey} · processed ${event.processedAt}</p>
        </article>
    `).join("");
}

function renderDeadLetters(events) {
    if (!deadLetterList) {
        return;
    }

    if (!events.length) {
        deadLetterList.innerHTML = '<p class="feedback">No dead letters yet. That is the healthy state for the happy path.</p>';
        return;
    }

    deadLetterList.innerHTML = events.map(event => `
        <article class="event-card">
            <div class="event-topline">
                <span class="pill danger-pill">DLT</span>
                <span>${event.originalTopic}</span>
                <span>offset ${event.kafkaOffset}</span>
            </div>
            <p class="event-name">${event.errorClassName || "Unknown failure"}</p>
            <p class="event-meta">${event.deadLetterTopic} · key ${event.messageKey || "n/a"} · dead-lettered ${event.deadLetteredAt}</p>
        </article>
    `).join("");
}

async function refreshHotels() {
    const hotels = await jsonRequest(config.hotelsUrl);
    renderHotels(hotels);
}

async function refreshEvents() {
    const events = await jsonRequest(config.statusUrl);
    renderEvents(events);
}

async function refreshDeadLetters() {
    const events = await jsonRequest(config.deadLettersUrl);
    renderDeadLetters(events);
}

async function refreshDashboard() {
    await Promise.all([refreshHotels(), refreshEvents(), refreshDeadLetters()]);
}

document.querySelector("#login-form").addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload = Object.fromEntries(formData.entries());

    try {
        const loginResponse = await jsonRequest(config.loginUrl, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        tokenInput.value = `Bearer ${loginResponse.accessToken}`;
        persistToken();
        setFeedback(authFeedback, "Access token loaded into the dashboard. If you still get 403, promote the user to ADMIN and log in again.");
    } catch (error) {
        setFeedback(authFeedback, error.message, true);
    }
});

tokenInput.addEventListener("input", persistToken);

document.querySelector("#create-form").addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload = {
        name: formData.get("name"),
        city: formData.get("city"),
        pricePerNight: Number(formData.get("pricePerNight"))
    };

    try {
        const hotel = await jsonRequest(config.createUrl, {
            method: "POST",
            headers: authHeaders(),
            body: JSON.stringify(payload)
        });

        event.currentTarget.reset();
        setFeedback(actionFeedback, `Created hotel #${hotel.id}. Waiting for the consumer feed to refresh.`);
        await refreshDashboard();
    } catch (error) {
        setFeedback(actionFeedback, error.message, true);
    }
});

document.querySelector("#update-form").addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const hotelId = formData.get("id");
    const payload = {
        name: formData.get("name"),
        city: formData.get("city"),
        pricePerNight: Number(formData.get("pricePerNight"))
    };

    try {
        await jsonRequest(`${config.updateBaseUrl}${hotelId}`, {
            method: "PUT",
            headers: authHeaders(),
            body: JSON.stringify(payload)
        });

        setFeedback(actionFeedback, `Updated hotel #${hotelId}. The consumer feed will show a new offset entry.`);
        await refreshDashboard();
    } catch (error) {
        setFeedback(actionFeedback, error.message, true);
    }
});

document.querySelector("#delete-form").addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const hotelId = formData.get("id");

    try {
        await jsonRequest(`${config.deleteBaseUrl}${hotelId}`, {
            method: "DELETE",
            headers: authHeaders()
        });

        setFeedback(actionFeedback, `Deleted hotel #${hotelId}. The delete event should appear in the consumer feed.`);
        await refreshDashboard();
    } catch (error) {
        setFeedback(actionFeedback, error.message, true);
    }
});

loadStoredToken();
refreshDashboard().catch(error => {
    setFeedback(actionFeedback, error.message, true);
});
window.setInterval(() => {
    Promise.all([refreshEvents(), refreshDeadLetters()]).catch(() => {
        // The dashboard already shows the last successful state; polling failures can stay silent.
    });
}, 4000);