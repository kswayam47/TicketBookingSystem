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
        .then(data => {
            if (!Array.isArray(data)) {
                throw new Error('Invalid response format');
            }
            const snackContainer = document.getElementById('snackContainer');
            if (!snackContainer) return; // Only load if container exists
            
            snackContainer.innerHTML = '';
            data.forEach(snack => {
                const snackItem = createSnackItem(snack);
                snackContainer.appendChild(snackItem);
            });
        })
        .catch(error => {
            console.error('Error loading snacks:', error);
            showNotification('Error loading snacks menu. Please try again.', true);
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
    const ticketModal = document.getElementById('ticketModal');
    const ticketContent = document.getElementById('ticketContent');
    
    // Get the first ticket from the array for display
    const firstTicket = ticketData.tickets[0];
    
    let ticketHtml = `
        <h3>Your Tickets</h3>
        <p><strong>Movie:</strong> ${escapeHtml(ticketData.movieTitle)}</p>
        <p><strong>Date & Time:</strong> ${formatDate(ticketData.dateTime)}</p>
        <p><strong>Total Seats:</strong> ${ticketData.tickets.length}</p>
        <h4>Seat Details:</h4>
        <div class="ticket-seats">
    `;
    
    ticketData.tickets.forEach(ticket => {
        ticketHtml += `
            <div class="seat-info">
                <p>Screen ${ticket.screenNo}, Row ${ticket.rowNo}, Seat ${ticket.seatNo}</p>
                <p class="price">₹${ticket.price.toFixed(2)}</p>
            </div>
        `;
    });
    
    ticketHtml += `
        </div>
        <div class="ticket-total">
            <p><strong>Total Amount:</strong> ₹${(ticketData.tickets.reduce((sum, ticket) => sum + ticket.price, 0)).toFixed(2)}</p>
        </div>
        <div class="ticket-actions">
            <button onclick="showSnackForm(${ticketData.reservationId})" class="submit-btn">Order Snacks</button>
            <button onclick="hideTicketModal()" class="cancel-btn">Close</button>
        </div>
    `;
    
    ticketContent.innerHTML = ticketHtml;
    ticketModal.style.display = 'block';
}

function hideTicketModal() {
    document.getElementById('ticketModal').style.display = 'none';
    // Reset booking form
    document.getElementById('ticketForm').reset();
    document.getElementById('bookingForm').style.display = 'none';
}

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
    const options = { year: 'numeric', month: 'long', day: 'numeric' };
    try {
        return new Date(dateString).toLocaleDateString(undefined, options);
    } catch (e) {
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
        showNotification('Snacks ordered successfully!');
        this.reset();
        document.getElementById('snackForm').style.display = 'none';
    })
    .catch(error => {
        console.error('Error ordering snacks:', error);
        showNotification(error.message || 'Error ordering snacks. Please try again.', true);
    });
});
