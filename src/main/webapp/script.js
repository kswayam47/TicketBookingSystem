document.addEventListener('DOMContentLoaded', function() {
    loadMovies();
    loadSnacks();
    setupUserProfile();
});

function setupUserProfile() {
    const user = JSON.parse(localStorage.getItem('user'));
    const userProfile = document.querySelector('.user-profile');
    const authButtons = document.querySelector('.auth-buttons');
    const userNameSpan = document.querySelector('.user-name');
    
    if (user) {
        // Show user profile and hide auth buttons
        userProfile.classList.add('show');
        authButtons.classList.remove('show');
        userNameSpan.textContent = user.name;
        
        // Add logout functionality
        document.querySelector('.logout-btn').addEventListener('click', function() {
            localStorage.removeItem('user');
            window.location.reload();
        });

        // Add profile button functionality
        document.querySelector('.profile-btn').addEventListener('click', function() {
            // You can implement profile page navigation here
            alert('Profile feature coming soon!');
        });
    } else {
        // Hide user profile and show auth buttons
        userProfile.classList.remove('show');
        authButtons.classList.add('show');
    }
}

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
            <button class="show-times-btn" onclick="loadShowTimings(${movie.id})">Show Times</button>
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

function showBookingForm(movieId, showId, showTime, showDate, screenNo, movieName) {
    const user = JSON.parse(localStorage.getItem('user'));
    
    if (!user) {
        // Redirect to login page if not logged in
        window.location.href = 'login.html';
        return;
    }
    
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'block';
    
    // Store show timing information in the form's dataset
    const ticketForm = document.getElementById('ticketForm');
    ticketForm.dataset.movieId = movieId;
    ticketForm.dataset.showId = showId;
    ticketForm.dataset.showTime = showTime;
    ticketForm.dataset.showDate = showDate;
    ticketForm.dataset.screenNo = screenNo;
    ticketForm.dataset.movieName = movieName;
    
    // Display show timing information
    const showInfoDiv = document.createElement('div');
    showInfoDiv.className = 'show-info';
    showInfoDiv.innerHTML = `
        <h3>Show Details</h3>
        <p><strong>Movie:</strong> ${movieName}</p>
        <p><strong>Date:</strong> ${new Date(showDate).toLocaleDateString()}</p>
        <p><strong>Time:</strong> ${showTime}</p>
        <p><strong>Screen:</strong> ${screenNo}</p>
    `;
    
    // Insert show info before the form
    const formGroup = ticketForm.querySelector('.form-group');
    ticketForm.insertBefore(showInfoDiv, formGroup);
    
    // Pre-fill user details if logged in
    if (user) {
        document.getElementById('name').value = user.name;
        document.getElementById('age').value = user.age;
        document.getElementById('gender').value = user.gender;
        
        // Disable fields that are pre-filled
        document.getElementById('name').disabled = true;
        document.getElementById('age').disabled = true;
        document.getElementById('gender').disabled = true;
    }
    
    // Hide snack form until booking is complete
    const snackForm = document.getElementById('snackForm');
    if (snackForm) snackForm.style.display = 'none';
    
    bookingForm.scrollIntoView({ behavior: 'smooth' });
}

function hideBookingForm() {
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'none';
    document.getElementById('ticketForm').reset();
    
    // Re-enable fields
    document.getElementById('name').disabled = false;
    document.getElementById('age').disabled = false;
    document.getElementById('gender').disabled = false;
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
    
    // Get show timing information from the form dataset
    const ticketForm = document.getElementById('ticketForm');
    const showTime = ticketForm.dataset.showTime || 'N/A';
    const showDate = ticketForm.dataset.showDate ? new Date(ticketForm.dataset.showDate).toLocaleDateString() : 'N/A';
    const screenNo = ticketForm.dataset.screenNo || 'N/A';
    
    let ticketHtml = `
        <div class="ticket-container">
            <div class="ticket-header">
                <h2>Movie Ticket</h2>
            </div>
            <div class="ticket-body">
                <div class="movie-info">
                    <h3>${escapeHtml(ticketData.movieTitle)}</h3>
                    <div class="show-details">
                        <p><i class="icon-calendar"></i> <strong>Date:</strong> ${showDate}</p>
                        <p><i class="icon-time"></i> <strong>Time:</strong> ${showTime}</p>
                        <p><i class="icon-screen"></i> <strong>Screen:</strong> ${screenNo}</p>
                    </div>
                </div>
                <div class="ticket-divider"></div>
                <div class="seat-details">
                    <h4>Seat Details</h4>
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
                <div class="seat-number">Row ${rowNo}, Seat ${seatNo}</div>
                <div class="seat-price">₹${price.toFixed(2)}</div>
            </div>
        `;
    });
    
    ticketHtml += `
                </div>
                <div class="ticket-total">
                    <p><strong>Total Amount:</strong> ₹${totalAmount.toFixed(2)}</p>
                </div>
            </div>
            <div class="ticket-footer">
                <p class="booking-id">Booking ID: ${ticketData.reservationId}</p>
                <p class="booking-date">Booked on: ${new Date().toLocaleDateString()}</p>
            </div>
        </div>
        <div class="ticket-actions">
            <button onclick="showSnackForm(${ticketData.reservationId})" class="submit-btn">Order Snacks</button>
            <button onclick="cancelTicket(${ticketData.reservationId})" class="cancel-ticket-btn">Cancel Ticket</button>
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
    
    const user = JSON.parse(localStorage.getItem('user'));
    
    if (!user) {
        showNotification('Please login to book tickets', true);
        return;
    }
    
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
    
    // Get show timing information from the form dataset
    const ticketForm = document.getElementById('ticketForm');
    const showTime = ticketForm.dataset.showTime || 'N/A';
    const showDate = ticketForm.dataset.showDate ? new Date(ticketForm.dataset.showDate).toLocaleDateString() : 'N/A';
    const screenNo = ticketForm.dataset.screenNo || 'N/A';
    const movieName = ticketForm.dataset.movieName || ticketData.movieTitle || 'N/A';
    
    let receiptHtml = `
        <div class="ticket-container">
            <div class="ticket-header">
                <h2>Movie Ticket</h2>
            </div>
            <div class="ticket-body">
                <div class="movie-info">
                    <h3>${escapeHtml(movieName)}</h3>
                    <div class="show-details">
                        <p><i class="icon-calendar"></i> <strong>Date:</strong> ${showDate}</p>
                        <p><i class="icon-time"></i> <strong>Time:</strong> ${showTime}</p>
                        <p><i class="icon-screen"></i> <strong>Screen:</strong> ${screenNo}</p>
                    </div>
                </div>
                <div class="ticket-divider"></div>
                <div class="seat-details">
                    <h4>Seat Details</h4>
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
                <div class="seat-number">Row ${rowNo}, Seat ${seatNo}</div>
                <div class="seat-price">₹${price.toFixed(2)}</div>
            </div>
        `;
    });
    
    receiptHtml += `
                    </div>
                    <div class="ticket-total">
                        <p><strong>Total Amount:</strong> ₹${ticketTotal.toFixed(2)}</p>
                    </div>
                </div>
    `;
    
    // Add snack order details if available
    if (snackOrderDetails && snackOrderDetails.orders && snackOrderDetails.orders.length > 0) {
        receiptHtml += `
                <div class="ticket-divider"></div>
                <div class="snack-section">
                    <h4>Snack Order Details</h4>
                    <div class="snack-orders">
        `;
        
        let snackTotal = 0;
        snackOrderDetails.orders.forEach(order => {
            const snack = window.snacksData.find(s => s.id === order.snackId);
            if (snack) {
                const price = snack.price * order.quantity;
                snackTotal += price;
                receiptHtml += `
                    <div class="snack-info">
                        <p><strong>Item:</strong> ${escapeHtml(snack.itemName)}</p>
                        <p><strong>Quantity:</strong> ${order.quantity}</p>
                        <p class="price"><strong>Price:</strong> ₹${price.toFixed(2)}</p>
                    </div>
                `;
            }
        });
        
        receiptHtml += `
                    </div>
                    <div class="snack-total">
                        <p><strong>Total Snack Amount:</strong> ₹${snackTotal.toFixed(2)}</p>
                    </div>
                    <div class="grand-total">
                        <p><strong>Grand Total:</strong> ₹${(ticketTotal + snackTotal).toFixed(2)}</p>
                    </div>
                </div>
        `;
    }
    
    receiptHtml += `
                <div class="ticket-footer">
                    <p class="booking-id">Booking ID: ${ticketData.reservationId}</p>
                    <p class="booking-date">Booked on: ${new Date().toLocaleDateString()}</p>
                </div>
            </div>
            <div class="ticket-actions">
                <button onclick="cancelTicket(${ticketData.reservationId})" class="cancel-ticket-btn">Cancel Ticket</button>
                <button onclick="hideTicketModal()" class="cancel-btn">Close</button>
            </div>
        </div>
    `;
    
    ticketContent.innerHTML = receiptHtml;
    ticketModal.style.display = 'none'; // Reset display
    requestAnimationFrame(() => {
        ticketModal.classList.add('show');
        ticketModal.style.display = 'flex';
    });
}

async function loadShowTimings(movieId) {
    try {
        const response = await fetch(`/api/showtimings?movieId=${movieId}`);
        const data = await response.json();
        
        const showTimingsDiv = document.getElementById('show-timings');
        showTimingsDiv.innerHTML = '<h3>Available Show Timings</h3>';
        
        if (data.showTimings && data.showTimings.length > 0) {
            const timingsGrid = document.createElement('div');
            timingsGrid.className = 'show-timings-grid';
            
            data.showTimings.forEach(show => {
                const showDate = new Date(show.showDate);
                const formattedDate = showDate.toLocaleDateString('en-US', { 
                    weekday: 'short', 
                    month: 'short', 
                    day: 'numeric' 
                });
                
                const showButton = document.createElement('button');
                showButton.className = 'show-time-btn';
                showButton.innerHTML = `
                    <div class="show-date">${formattedDate}</div>
                    <div class="show-time">${show.showTime}</div>
                    <div class="screen-info">Screen ${show.screenNo}</div>
                    <div class="seats-info">${show.availableSeats} seats left</div>
                `;
                showButton.onclick = () => showBookingForm(movieId, show.showId, show.showTime, show.showDate, show.screenNo, show.movieName);
                timingsGrid.appendChild(showButton);
            });
            
            showTimingsDiv.appendChild(timingsGrid);
            showTimingsDiv.scrollIntoView({ behavior: 'smooth' });
        } else {
            showTimingsDiv.innerHTML += '<p>No show timings available for this movie.</p>';
        }
    } catch (error) {
        console.error('Error loading show timings:', error);
        showNotification('Failed to load show timings', 'error');
    }
}

// Add the cancelTicket function
function cancelTicket(reservationId) {
    if (!confirm('Are you sure you want to cancel this ticket? This action cannot be undone.')) {
        return;
    }
    
    fetch('/api/booking/cancel', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ reservationId: reservationId })
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
        showNotification('Ticket cancelled successfully!');
        hideTicketModal();
        // Refresh the page to update the UI
        window.location.reload();
    })
    .catch(error => {
        console.error('Error cancelling ticket:', error);
        showNotification(error.message || 'Error cancelling ticket. Please try again.', true);
    });
}