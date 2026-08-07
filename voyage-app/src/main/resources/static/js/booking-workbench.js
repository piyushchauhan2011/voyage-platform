const config = window.bookingWorkbenchConfig;

const tokenInput = document.getElementById('token-input');
const authFeedback = document.getElementById('auth-feedback');
const inventoryFeedback = document.getElementById('inventory-feedback');
const bookingFeedback = document.getElementById('booking-feedback');
const inventoryResults = document.getElementById('inventory-results');
const bookingResults = document.getElementById('booking-results');
const notificationResults = document.getElementById('notification-results');

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
            throw new Error(body.message || 'Login failed');
        }
        const token = `Bearer ${body.accessToken}`;
        tokenInput.value = token;
        localStorage.setItem(config.storageKey, token);
        setFeedback(authFeedback, 'Token stored. You can now create bookings or inventory.', false);
        await Promise.all([loadBookings(), loadNotifications()]);
    } catch (error) {
        setFeedback(authFeedback, error.message, true);
    }
});

document.getElementById('inventory-form').addEventListener('submit', async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    try {
        const response = await fetch(config.inventoryUrl, {
            method: 'POST',
            headers: authorizedJsonHeaders(),
            body: JSON.stringify({
                hotelId: Number(formData.get('hotelId')),
                roomType: formData.get('roomType'),
                date: formData.get('date'),
                availableRooms: Number(formData.get('availableRooms'))
            })
        });
        const body = await parseBody(response);
        if (!response.ok) {
            throw new Error(body.message || 'Inventory creation failed');
        }
        setFeedback(inventoryFeedback, `Created inventory row #${body.id}.`, false);
        await loadInventory();
    } catch (error) {
        setFeedback(inventoryFeedback, error.message, true);
    }
});

document.getElementById('inventory-query-form').addEventListener('submit', async event => {
    event.preventDefault();
    await loadInventory();
});

document.getElementById('booking-form').addEventListener('submit', async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    try {
        const response = await fetch(config.bookingsUrl, {
            method: 'POST',
            headers: authorizedJsonHeaders(),
            body: JSON.stringify({
                hotelId: Number(formData.get('hotelId')),
                roomType: formData.get('roomType'),
                checkIn: formData.get('checkIn'),
                checkOut: formData.get('checkOut'),
                paymentToken: formData.get('paymentToken')
            })
        });
        const body = await parseBody(response);
        if (!response.ok) {
            throw new Error(body.message || 'Booking creation failed');
        }
        setFeedback(bookingFeedback, `Created booking #${body.id}.`, false);
        await Promise.all([loadBookings(), loadNotifications(), loadInventoryFromBookingForm()]);
    } catch (error) {
        setFeedback(bookingFeedback, error.message, true);
    }
});

document.getElementById('refresh-bookings').addEventListener('click', loadBookings);
document.getElementById('refresh-notifications').addEventListener('click', loadNotifications);

async function loadInventory() {
    const queryForm = document.getElementById('inventory-query-form');
    const formData = new FormData(queryForm);
    const params = new URLSearchParams({
        hotelId: formData.get('hotelId'),
        from: formData.get('from'),
        to: formData.get('to')
    });
    if (formData.get('roomType')) {
        params.set('roomType', formData.get('roomType'));
    }
    const response = await fetch(`${config.inventoryUrl}?${params.toString()}`);
    const body = await parseBody(response);
    if (!response.ok) {
        throw new Error(body.message || 'Failed to load inventory');
    }
    inventoryResults.innerHTML = body.map(item => `
        <article class="result-card">
            <div class="pill">${item.roomType}</div>
            <h3>#${item.hotelId} ${item.hotelName}</h3>
            <p class="result-meta">${item.date} · ${item.availableRooms} rooms available</p>
        </article>
    `).join('') || '<article class="result-card"><p>No inventory rows found for that window.</p></article>';
}

async function loadInventoryFromBookingForm() {
    const bookingForm = document.getElementById('booking-form');
    const queryForm = document.getElementById('inventory-query-form');
    const bookingData = new FormData(bookingForm);
    queryForm.elements.hotelId.value = bookingData.get('hotelId');
    queryForm.elements.from.value = bookingData.get('checkIn');
    const checkOut = bookingData.get('checkOut');
    queryForm.elements.to.value = subtractOneDay(checkOut);
    queryForm.elements.roomType.value = bookingData.get('roomType');
    await loadInventory();
}

async function loadBookings() {
    const response = await fetch(config.bookingsUrl, {
        headers: authorizedHeaders()
    });
    const body = await parseBody(response);
    if (!response.ok) {
        throw new Error(body.message || 'Failed to load bookings');
    }
    bookingResults.innerHTML = body.content.map(item => `
        <article class="result-card">
            <div class="pill">${item.status}</div>
            <h3>Booking #${item.id} · ${item.hotelName}</h3>
            <p class="result-meta">${item.roomType} · ${item.checkIn} to ${item.checkOut} · ${item.totalPrice}</p>
            <div class="result-actions">
                ${item.status !== 'CANCELLED' ? `<button type="button" class="danger-button" data-cancel-id="${item.id}">Cancel booking</button>` : ''}
            </div>
        </article>
    `).join('') || '<article class="result-card"><p>No bookings yet.</p></article>';

    bookingResults.querySelectorAll('[data-cancel-id]').forEach(button => {
        button.addEventListener('click', async () => {
            try {
                const response = await fetch(`${config.bookingsUrl}/${button.dataset.cancelId}`, {
                    method: 'DELETE',
                    headers: authorizedHeaders()
                });
                const body = await parseBody(response);
                if (!response.ok) {
                    throw new Error(body.message || 'Cancellation failed');
                }
                setFeedback(bookingFeedback, `Cancelled booking #${body.id}.`, false);
                await Promise.all([loadBookings(), loadNotifications(), loadInventoryFromCancelledBooking(body)]);
            } catch (error) {
                setFeedback(bookingFeedback, error.message, true);
            }
        });
    });
}

async function loadInventoryFromCancelledBooking(booking) {
    const queryForm = document.getElementById('inventory-query-form');
    queryForm.elements.hotelId.value = booking.hotelId;
    queryForm.elements.from.value = booking.checkIn;
    queryForm.elements.to.value = subtractOneDay(booking.checkOut);
    queryForm.elements.roomType.value = booking.roomType;
    await loadInventory();
}

async function loadNotifications() {
    const response = await fetch(config.notificationsUrl, {
        headers: authorizedHeaders()
    });
    const body = await parseBody(response);
    if (!response.ok) {
        throw new Error(body.message || 'Failed to load notifications');
    }
    notificationResults.innerHTML = body.content.map(item => `
        <article class="result-card">
            <div class="pill">${item.type}</div>
            <h3>Notification #${item.id}</h3>
            <p>${item.message}</p>
            <p class="result-meta">${item.createdAt}</p>
            ${!item.read ? `<div class="result-actions"><button type="button" data-read-id="${item.id}">Mark read</button></div>` : ''}
        </article>
    `).join('') || '<article class="result-card"><p>No notifications yet.</p></article>';

    notificationResults.querySelectorAll('[data-read-id]').forEach(button => {
        button.addEventListener('click', async () => {
            try {
                const response = await fetch(`${config.markReadBaseUrl}${button.dataset.readId}/read`, {
                    method: 'PATCH',
                    headers: authorizedHeaders()
                });
                const body = await parseBody(response);
                if (!response.ok) {
                    throw new Error(body.message || 'Failed to mark notification as read');
                }
                setFeedback(bookingFeedback, `Marked notification #${body.id} as read.`, false);
                await loadNotifications();
            } catch (error) {
                setFeedback(bookingFeedback, error.message, true);
            }
        });
    });
}

function authorizedHeaders() {
    const token = tokenInput.value.trim();
    if (!token) {
        throw new Error('Add a bearer token first.');
    }
    localStorage.setItem(config.storageKey, token);
    return {Authorization: token};
}

function authorizedJsonHeaders() {
    return {
        ...authorizedHeaders(),
        'Content-Type': 'application/json'
    };
}

async function parseBody(response) {
    const text = await response.text();
    return text ? JSON.parse(text) : {};
}

function setFeedback(node, message, isError) {
    node.textContent = message;
    node.className = `feedback ${isError ? 'error' : 'success'}`;
}

function subtractOneDay(dateString) {
    const date = new Date(`${dateString}T00:00:00`);
    date.setDate(date.getDate() - 1);
    return date.toISOString().slice(0, 10);
}

if (tokenInput.value.trim()) {
    Promise.allSettled([loadBookings(), loadNotifications()]);
}