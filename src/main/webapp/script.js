document.addEventListener('DOMContentLoaded', function() {
    loadMovies();
    loadSnacks();
});

function loadMovies() {
    fetch('/api/movies')
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => Promise.reject(err));
            }
            return response.json();
        })
        .then(data => {
            if (!Array.isArray(data)) {
                throw new Error('Invalid response format');
            }
            const movieContainer = document.getElementById('movieContainer');
            movieContainer.innerHTML = '';
            
            if (data.length === 0) {
                movieContainer.innerHTML = '<p class="no-movies">No movies available at the moment.</p>';
                return;
            }
            
            data.forEach(movie => {
                const movieCard = createMovieCard(movie);
                movieContainer.appendChild(movieCard);
            });
        })
        .catch(error => {
            console.error('Error loading movies:', error);
            const movieContainer = document.getElementById('movieContainer');
            movieContainer.innerHTML = '<p class="error-message">Error loading movies. Please try again later.</p>';
            showNotification(error.message || 'Error loading movies. Please try again.', true);
        });
}

function loadSnacks() {
    fetch('/api/snacks')
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => Promise.reject(err));
            }
            return response.json();
        })
        .then(snacks => {
            // Store snacks data globally
            window.snacksData = snacks;
            
            const snackContainer = document.getElementById('snackContainer');
            if (!snackContainer) return; // Only load if container exists
            
            snackContainer.innerHTML = '';
            
            snacks.forEach(snack => {
                const snackItem = createSnackItem(snack);
                snackContainer.appendChild(snackItem);
            });
        })
        .catch(error => {
            console.error('Error loading snacks:', error);
            showNotification('Error loading snacks. Please try again.', true);
        });
}

function createMovieCard(movie) {
    const card = document.createElement('div');
    card.className = 'movie-card';
    
    card.innerHTML = `
        <div class="movie-info">
            <h3>${escapeHtml(movie.title)}</h3>
            <p><strong>Genre:</strong> ${escapeHtml(movie.genre || 'N/A')}</p>
            <p><strong>Duration:</strong> ${movie.duration || 'N/A'} minutes</p>
            <p><strong>Release Date:</strong> ${formatDate(movie.releaseDate)}</p>
            <button onclick="showBookingForm(${movie.id})" class="submit-btn">Book Now</button>
        </div>
    `;
    
    return card;
}

function createSnackItem(snack) {
    const item = document.createElement('div');
    item.className = 'snack-item';
    
    item.innerHTML = `
        <div class="snack-info">
            <h4>${escapeHtml(snack.itemName)}</h4>
            <p class="price">₹${snack.price.toFixed(2)}</p>
            <div class="quantity-selector">
                <button onclick="updateQuantity(${snack.id}, -1)" class="quantity-btn">-</button>
                <input type="number" min="0" value="0" id="snack-${snack.id}" class="quantity-input">
                <button onclick="updateQuantity(${snack.id}, 1)" class="quantity-btn">+</button>
            </div>
        </div>
    `;
    
    return item;
}

function updateQuantity(snackId, delta) {
    const input = document.getElementById(`snack-${snackId}`);
    const newValue = Math.max(0, parseInt(input.value) + delta);
    input.value = newValue;
}

function showBookingForm(movieId) {
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'block';
    document.getElementById('ticketForm').dataset.movieId = movieId;
    
    // Hide snack form until booking is complete
    const snackForm = document.getElementById('snackForm');
    if (snackForm) snackForm.style.display = 'none';
    
    bookingForm.scrollIntoView({ behavior: 'smooth' });
}

function hideBookingForm() {
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'none';
    document.getElementById('ticketForm').reset();
}

function showSnackForm(reservationId) {
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'none';
    
    const snackForm = document.getElementById('snackForm');
    snackForm.style.display = 'block';
    snackForm.dataset.reservationId = reservationId;
    
    loadSnacks();
    snackForm.scrollIntoView({ behavior: 'smooth' });
}

function showTicketDetails(ticketData) {
    // Store ticket data for later use
    window.lastTicketData = ticketData;
    
    const ticketModal = document.getElementById('ticketModal');
    const ticketContent = document.getElementById('ticketContent');
    
    // Debug log to check ticket data
    console.log('Ticket Data:', ticketData);
    console.log('Tickets array:', ticketData.tickets);
    
    // Check if tickets array exists and has items
    if (!ticketData.tickets || !Array.isArray(ticketData.tickets) || ticketData.tickets.length === 0) {
        console.error('No tickets found in the data');
        showNotification('Error: No ticket information available', true);
        return;
    }
    
    let ticketHtml = `
        <h3>Your Tickets</h3>
        <p><strong>Movie:</strong> ${escapeHtml(ticketData.movieTitle)}</p>
        <p><strong>Date & Time:</strong> ${formatDate(ticketData.dateTime)}</p>
        <p><strong>Total Seats:</strong> ${ticketData.tickets.length}</p>
        <h4>Seat Details:</h4>
        <div class="ticket-seats">
    `;
    
    let totalAmount = 0;
    
    ticketData.tickets.forEach((ticket, index) => {
        // Debug log for each ticket
        console.log(`Ticket ${index}:`, ticket);
        console.log(`Ticket ${index} properties:`, Object.keys(ticket));
        
        // Ensure all ticket properties are properly accessed
        const rowNo = ticket.rowNo !== undefined ? ticket.rowNo : 'N/A';
        const seatNo = ticket.seatNo !== undefined ? ticket.seatNo : 'N/A';
        const screenNo = ticket.screenNo !== undefined ? ticket.screenNo : 'N/A';
        const price = ticket.price !== undefined ? ticket.price : 0;
        
        console.log(`Ticket ${index} values:`, { rowNo, seatNo, screenNo, price });
        
        totalAmount += price;
        
        ticketHtml += `
            <div class="seat-info">
                <p><strong>Screen:</strong> ${screenNo}</p>
                <p><strong>Row:</strong> ${rowNo}</p>
                <p><strong>Seat:</strong> ${seatNo}</p>
                <p class="price"><strong>Price:</strong> ₹${price.toFixed(2)}</p>
            </div>
        `;
    });
    
    ticketHtml += `
        </div>
        <div class="ticket-total">
            <p><strong>Total Amount:</strong> ₹${totalAmount.toFixed(2)}</p>
        </div>
        <div class="ticket-actions">
            <button onclick="showSnackForm(${ticketData.reservationId})" class="submit-btn">Order Snacks</button>
            <button onclick="hideTicketModal()" class="cancel-btn">Close</button>
        </div>
    `;
    
    ticketContent.innerHTML = ticketHtml;
    ticketModal.style.display = 'none'; // Reset display
    requestAnimationFrame(() => {
        ticketModal.classList.add('show');
        ticketModal.style.display = 'flex';
    });
}

function hideTicketModal() {
    const ticketModal = document.getElementById('ticketModal');
    ticketModal.classList.remove('show');
    ticketModal.style.display = 'none';
    // Reset booking form
    document.getElementById('ticketForm').reset();
    document.getElementById('bookingForm').style.display = 'none';
}

// Close modal when clicking outside
document.getElementById('ticketModal').addEventListener('click', function(e) {
    if (e.target === this) {
        hideTicketModal();
    }
});

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    
    try {
        // Try to parse the date string
        const date = new Date(dateString);
        
        // Check if the date is valid
        if (isNaN(date.getTime())) {
            // If not a valid date, try to format the string directly
            return dateString;
        }
        
        // Format the date with time
        const options = { 
            year: 'numeric', 
            month: 'long', 
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        };
        
        return date.toLocaleString(undefined, options);
    } catch (e) {
        console.error('Error formatting date:', e);
        return dateString;
    }
}

function showNotification(message, isError = false) {
    const notification = document.getElementById('notification');
    notification.textContent = message;
    notification.classList.add('show');
    if (isError) {
        notification.classList.add('error');
    } else {
        notification.classList.remove('error');
    }
    
    setTimeout(() => {
        notification.classList.remove('show');
    }, 3000);
}

document.getElementById('ticketForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const formData = {
        movieId: parseInt(this.dataset.movieId),
        name: document.getElementById('name').value.trim(),
        age: parseInt(document.getElementById('age').value),
        gender: document.getElementById('gender').value,
        seats: parseInt(document.getElementById('seats').value)
    };
    
    // Validate form data
    if (!formData.movieId) {
        showNotification('Invalid movie selection. Please try again.', true);
        return;
    }
    if (!formData.name) {
        showNotification('Please enter your name.', true);
        return;
    }
    if (isNaN(formData.age) || formData.age < 1 || formData.age > 120) {
        showNotification('Please enter a valid age.', true);
        return;
    }
    if (!formData.gender) {
        showNotification('Please select your gender.', true);
        return;
    }
    if (isNaN(formData.seats) || formData.seats < 1 || formData.seats > 10) {
        showNotification('Please select a valid number of seats (1-10).', true);
        return;
    }
    
    fetch('/api/book', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(formData)
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => Promise.reject(err));
        }
        return response.json();
    })
    .then(data => {
        if (data.error) {
            throw new Error(data.error);
        }
        console.log('Booking response:', data);
        console.log('Ticket data:', data.ticket);
        
        if (!data.ticket || !data.ticket.tickets || !Array.isArray(data.ticket.tickets)) {
            console.error('Invalid ticket data format:', data.ticket);
            throw new Error('Invalid ticket data received from server');
        }
        
        showNotification('Booking completed successfully!');
        this.reset();
        showTicketDetails(data.ticket);
    })
    .catch(error => {
        console.error('Error booking ticket:', error);
        showNotification(error.message || 'Error booking ticket. Please try again.', true);
    });
});

document.getElementById('snackForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const snackOrders = [];
    document.querySelectorAll('.quantity-input').forEach(input => {
        const quantity = parseInt(input.value);
        if (quantity > 0) {
            snackOrders.push({
                snackId: parseInt(input.id.replace('snack-', '')),
                quantity: quantity
            });
        }
    });
    
    if (snackOrders.length === 0) {
        showNotification('Please select at least one snack item', true);
        return;
    }
    
    const formData = {
        reservationId: parseInt(this.dataset.reservationId),
        orders: snackOrders
    };
    
    fetch('/api/snacks/order', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(formData)
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => Promise.reject(err));
        }
        return response.json();
    })
    .then(data => {
        if (data.error) {
            throw new Error(data.error);
        }
        // Store snack order details for the final receipt
        const snackOrderDetails = {
            orders: formData.orders,
            reservationId: formData.reservationId
        };
        // Hide snack form
        document.getElementById('snackForm').style.display = 'none';
        // Show final receipt
        showFinalReceipt(window.lastTicketData, snackOrderDetails);
    })
    .catch(error => {
        console.error('Error ordering snacks:', error);
        showNotification(error.message || 'Error ordering snacks. Please try again.', true);
    });
});

function showFinalReceipt(ticketData, snackOrderDetails) {
    const ticketModal = document.getElementById('ticketModal');
    const ticketContent = document.getElementById('ticketContent');
    
    // Debug log to check ticket data
    console.log('Final Receipt Ticket Data:', ticketData);
    
    let receiptHtml = `
        <h2>Final Receipt</h2>
        <div class="ticket-section">
            <h3>Movie Ticket Details</h3>
            <p><strong>Movie:</strong> ${escapeHtml(ticketData.movieTitle)}</p>
            <p><strong>Date & Time:</strong> ${formatDate(ticketData.dateTime)}</p>
            <p><strong>Total Seats:</strong> ${ticketData.tickets.length}</p>
            <h4>Seat Details:</h4>
            <div class="ticket-seats">
    `;
    
    let ticketTotal = 0;
    ticketData.tickets.forEach((ticket, index) => {
        // Debug log for each ticket
        console.log('Final Receipt Ticket:', ticket);
        
        // Ensure all ticket properties are properly accessed
        const rowNo = ticket.rowNo !== undefined ? ticket.rowNo : 'N/A';
        const seatNo = ticket.seatNo !== undefined ? ticket.seatNo : 'N/A';
        const screenNo = ticket.screenNo !== undefined ? ticket.screenNo : 'N/A';
        const price = ticket.price !== undefined ? ticket.price : 0;
        
        ticketTotal += price;
        receiptHtml += `
            <div class="seat-info">
                <p><strong>Screen:</strong> ${screenNo}</p>
                <p><strong>Row:</strong> ${rowNo}</p>
                <p><strong>Seat:</strong> ${seatNo}</p>
                <p class="price"><strong>Price:</strong> ₹${price.toFixed(2)}</p>
            </div>
        `;
    });
    
    receiptHtml += `
        </div>
        <div class="ticket-total">
            <p><strong>Ticket Total:</strong> ₹${ticketTotal.toFixed(2)}</p>
        </div>
    `;
    
    // Add snack order details
    receiptHtml += `
        <div class="snack-section">
            <h3>Snack Order Details</h3>
            <div class="snack-items">
    `;
    
    let snackTotal = 0;
    snackOrderDetails.orders.forEach(order => {
        const snackItem = window.snacksData.find(s => s.id === order.snackId);
        if (snackItem) {
            const itemTotal = snackItem.price * order.quantity;
            snackTotal += itemTotal;
            receiptHtml += `
                <div class="snack-item">
                    <p>${escapeHtml(snackItem.itemName)} x ${order.quantity}</p>
                    <p class="price">₹${itemTotal.toFixed(2)}</p>
                </div>
            `;
        }
    });
    
    receiptHtml += `
            </div>
            <div class="snack-total">
                <p><strong>Snack Total:</strong> ₹${snackTotal.toFixed(2)}</p>
            </div>
        </div>
        <div class="grand-total">
            <h3>Grand Total: ₹${(ticketTotal + snackTotal).toFixed(2)}</h3>
        </div>
        <div class="receipt-actions">
            <button onclick="confirmBooking(${ticketData.reservationId})" class="submit-btn">Confirm Booking</button>
            <button onclick="cancelBooking(${ticketData.reservationId})" class="cancel-btn">Cancel Booking</button>
        </div>
    `;
    
    ticketContent.innerHTML = receiptHtml;
    ticketModal.style.display = 'flex';
}

function confirmBooking(reservationId) {
    fetch('/api/booking/confirm', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ reservationId })
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => Promise.reject(err));
        }
        return response.json();
    })
    .then(data => {
        if (data.error) {
            throw new Error(data.error);
        }
        showNotification('Booking confirmed successfully!');
        hideTicketModal();
        // Reset all quantity inputs
        document.querySelectorAll('.quantity-input').forEach(input => {
            input.value = 0;
        });
    })
    .catch(error => {
        console.error('Error confirming booking:', error);
        showNotification(error.message || 'Error confirming booking. Please try again.', true);
    });
}

function cancelBooking(reservationId) {
    fetch('/api/booking/cancel', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ reservationId })
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => Promise.reject(err));
        }
        return response.json();
    })
    .then(data => {
        if (data.error) {
            throw new Error(data.error);
        }
        showNotification('Booking cancelled successfully.');
        hideTicketModal();
        // Reset all quantity inputs
        document.querySelectorAll('.quantity-input').forEach(input => {
            input.value = 0;
        });
    })
    .catch(error => {
        console.error('Error cancelling booking:', error);
        showNotification(error.message || 'Error cancelling booking. Please try again.', true);
    });
}
