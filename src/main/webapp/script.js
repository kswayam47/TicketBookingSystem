document.addEventListener('DOMContentLoaded', function() {
    loadMovies();
    loadSnacks();
    setupUserProfile();
    initializeSlideshow();
});

function initializeSlideshow() {
    // Initialize the slideshow with auto-rotation
    new Swiper('.hero-slider', {
        loop: true,
        effect: 'fade',
        speed: 1000,
        autoplay: {
            delay: 4000,
            disableOnInteraction: false
        },
        pagination: {
            el: '.swiper-pagination',
            clickable: true
        }
    });
    
    // Add click event to all Book Now buttons in the slideshow
    document.querySelectorAll('.book-now-btn').forEach(button => {
        button.addEventListener('click', function() {
            // Scroll to Now Showing section
            document.querySelector('.movie-list').scrollIntoView({
                behavior: 'smooth'
            });
        });
    });
}

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
            
            // Create a map to ensure uniqueness by movie ID
            const processedMovieIds = new Set();
            
            // First display trending movies
            data.filter(movie => movie.trending)
                .forEach(movie => {
                    const movieCard = createMovieCard(movie);
                    movieContainer.appendChild(movieCard);
                    processedMovieIds.add(movie.id);
                });
            
            // Then display non-trending movies
            data.filter(movie => !movie.trending && !processedMovieIds.has(movie.id))
                .forEach(movie => {
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
            
            // Create a map to ensure uniqueness by snack ID
            const processedSnackIds = new Set();
            
            // First display trending snacks
            snacks.filter(snack => snack.trending)
                .forEach(snack => {
                    const snackItem = createSnackItem(snack);
                    snackContainer.appendChild(snackItem);
                    processedSnackIds.add(snack.id);
                });
            
            // Then display non-trending snacks
            snacks.filter(snack => !snack.trending && !processedSnackIds.has(snack.id))
                .forEach(snack => {
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
    
    // Add trending badge if this movie is trending
    if (movie.trending) {
        card.classList.add('trending');
    }
    
    card.innerHTML = `
        <div class="movie-info">
            ${movie.trending ? '<div class="trending-badge">TRENDING</div>' : ''}
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
    
    // Add low stock indicator if needed
    if (snack.lowStock) {
        item.classList.add('low-stock');
    }
    
    // Add trending indicator if this is a popular snack
    if (snack.trending) {
        item.classList.add('trending');
    }
    
    item.innerHTML = `
        <div class="snack-info">
            ${snack.trending ? '<div class="trending-badge">MOST ORDERED</div>' : ''}
            <h4>${escapeHtml(snack.itemName)}</h4>
            <p class="price">₹${snack.price.toFixed(2)}</p>
            ${snack.lowStock ? `<p class="stock-warning">Low Stock: Only ${snack.quantity} left</p>` : ''}
            <div class="quantity-selector">
                <button onclick="updateQuantity(${snack.id}, -1)" class="quantity-btn">-</button>
                <input type="number" min="0" max="${snack.quantity}" value="0" id="snack-${snack.id}" 
                       class="quantity-input" data-max-stock="${snack.quantity}">
                <button onclick="updateQuantity(${snack.id}, 1)" class="quantity-btn">+</button>
            </div>
        </div>
    `;
    
    return item;
}

function updateQuantity(snackId, delta) {
    const input = document.getElementById(`snack-${snackId}`);
    const maxStock = parseInt(input.dataset.maxStock);
    const currentValue = parseInt(input.value) || 0;
    
    // Calculate new value but enforce minimum and maximum limits
    let newValue = currentValue + delta;
    newValue = Math.max(0, newValue); // Ensure not below 0
    newValue = Math.min(maxStock, newValue); // Ensure not above available stock
    
    // Set the new value and provide feedback if hitting limits
    input.value = newValue;
    
    // Give visual feedback when trying to exceed available stock
    if (newValue === maxStock && delta > 0) {
        showNotification(`Maximum available quantity (${maxStock}) reached for this item`, true);
    }
}

function showBookingForm(movieId, showId, showTime, showDate, screenNo, movieName, availableSeats) {
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
    ticketForm.dataset.availableSeats = availableSeats; // Store available seats info
    
    // Display show timing information
    const showInfoDiv = document.createElement('div');
    showInfoDiv.className = 'show-info';
    showInfoDiv.innerHTML = `
        <h3>Show Details</h3>
        <p><strong>Movie:</strong> ${movieName}</p>
        <p><strong>Date:</strong> ${new Date(showDate).toLocaleDateString()}</p>
        <p><strong>Time:</strong> ${showTime}</p>
        <p><strong>Screen:</strong> ${screenNo}</p>
        <p><strong>Available Seats:</strong> <span class="available-seats" data-show-id="${showId}">${availableSeats}</span></p>
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
    
    // Set maximum selectable seats based on available seats
    const seatsInput = document.getElementById('seats');
    seatsInput.max = availableSeats;
    
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
    // Hide the ticket modal first
    const ticketModal = document.getElementById('ticketModal');
    ticketModal.classList.remove('show');
    ticketModal.style.display = 'none';
    
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
            <button onclick="confirmTicket(${ticketData.reservationId})" class="confirm-ticket-btn">Confirm Ticket</button>
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
        showId: parseInt(this.dataset.showId),
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
    if (!formData.showId) {
        showNotification('Invalid show timing selection. Please try again.', true);
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
    
    // Check if requested seats exceed available seats
    const availableSeats = parseInt(this.dataset.availableSeats || 0);
    if (formData.seats > availableSeats) {
        showNotification(`Only ${availableSeats} seats available for this show.`, true);
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
        
        // Update the displayed available seats count in the UI if applicable
        updateAvailableSeatsDisplay(formData.showId, formData.seats);
    })
    .catch(error => {
        console.error('Error booking ticket:', error);
        showNotification(error.message || 'Error booking ticket. Please try again.', true);
    });
});

document.getElementById('snackForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const snackOrders = [];
    let hasLowStockWarning = false;
    let totalItems = 0;
    
    document.querySelectorAll('.quantity-input').forEach(input => {
        const quantity = parseInt(input.value);
        if (quantity > 0) {
            const maxStock = parseInt(input.dataset.maxStock);
            // Double-check we're not exceeding stock
            if (quantity > maxStock) {
                showNotification(`Cannot order more than ${maxStock} of this item due to stock limitations`, true);
                hasLowStockWarning = true;
                return;
            }
            
            snackOrders.push({
                snackId: parseInt(input.id.replace('snack-', '')),
                quantity: quantity
            });
            totalItems += quantity;
        }
    });
    
    if (hasLowStockWarning) {
        return; // Stop submission if we have stock issues
    }
    
    if (snackOrders.length === 0) {
        showNotification('Please select at least one snack item', true);
        return;
    }
    
    const formData = {
        reservationId: parseInt(this.dataset.reservationId),
        orders: snackOrders
    };
    
    // Disable the submit button to prevent double submission
    const submitButton = this.querySelector('.submit-btn');
    submitButton.disabled = true;
    submitButton.textContent = 'Processing...';
    
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
            orders: data.orders || formData.orders, // Use orders from response if available
            reservationId: formData.reservationId
        };
        
        showNotification(`Successfully ordered ${totalItems} snack items!`);
        
        // Check if any items are now low in stock
        const lowStockItems = data.orders ? data.orders.filter(item => item.lowStock) : [];
        if (lowStockItems.length > 0) {
            setTimeout(() => {
                showNotification(`Note: Some items are running low on stock after your order`, false);
            }, 3000);
        }
        
        // Hide snack form
        document.getElementById('snackForm').style.display = 'none';
        
        // Show final receipt
        showFinalReceipt(window.lastTicketData, snackOrderDetails);
    })
    .catch(error => {
        console.error('Error ordering snacks:', error);
        showNotification(error.message || 'Error ordering snacks. Please try again.', true);
    })
    .finally(() => {
        // Re-enable the submit button
        submitButton.disabled = false;
        submitButton.textContent = 'Place Snack Order';
    });
});

function showFinalReceipt(ticketData, snackOrderDetails) {
    const ticketModal = document.getElementById('ticketModal');
    const ticketContent = document.getElementById('ticketContent');
    
    let receiptHtml = `
        <div class="ticket-container">
            ${ticketData.status === 'Confirmed' ? '<div class="status-badge confirmed">CONFIRMED</div>' : ''}
            <div class="ticket-header">
                <h2>Movie Ticket</h2>
            </div>
            <div class="ticket-body">
                <div class="movie-info">
                    <h3>${escapeHtml(ticketData.movieTitle)}</h3>
                    <div class="show-details">
                        <p><i class="icon-calendar"></i> <strong>Date:</strong> ${ticketData.showDate || 'N/A'}</p>
                        <p><i class="icon-time"></i> <strong>Time:</strong> ${ticketData.showTime || 'N/A'}</p>
                        <p><i class="icon-screen"></i> <strong>Screen:</strong> ${ticketData.screenNo || 'N/A'}</p>
                    </div>
                </div>
                <div class="ticket-divider"></div>
                <div class="seat-details">
                    <h4>Seat Details</h4>
                    <div class="ticket-seats">
    `;
    
    // Create a Set to track unique seats and calculate total
    let ticketTotal = 0;
    const processedSeats = new Set();
    
    ticketData.tickets.forEach(ticket => {
        // Create a unique key for each seat
        const seatKey = `${ticket.rowNo}-${ticket.seatNo}`;
        
        // Only process if we haven't seen this seat before
        if (!processedSeats.has(seatKey)) {
            processedSeats.add(seatKey);
            ticketTotal += ticket.price || 0;
            
            receiptHtml += `
                <div class="seat-info">
                    <div class="seat-number">Row ${ticket.rowNo || 'N/A'}, Seat ${ticket.seatNo || 'N/A'}</div>
                    <div class="seat-price">₹${(ticket.price || 0).toFixed(2)}</div>
                </div>
            `;
        }
    });
    
    receiptHtml += `
                    </div>
                    <div class="ticket-total">
                        <p><strong>Total Amount:</strong> ₹${ticketTotal.toFixed(2)}</p>
                    </div>
                </div>
    `;
    
    // Use snack orders from either source
    const snacks = ticketData.snacks || (snackOrderDetails ? snackOrderDetails.orders : null);
    if (snacks && snacks.length > 0) {
        receiptHtml += `
                <div class="ticket-divider"></div>
                <div class="snack-section">
                    <h4>Snack Order Details</h4>
                    ${ticketData.employeeName ? 
                        `<div class="employee-info">
                            <p><i class="fas fa-user-tie"></i> You will be served by: <strong>${escapeHtml(ticketData.employeeName)}</strong></p>
                        </div>` : ''}
                    <div class="snack-orders">
        `;
        
        let snackTotal = 0;
        snacks.forEach(order => {
            const totalPrice = order.total || (order.price * order.quantity);
            snackTotal += totalPrice;
            
            receiptHtml += `
                <div class="snack-info">
                    <p><strong>Item:</strong> ${escapeHtml(order.itemName)}</p>
                    <p><strong>Quantity:</strong> ${order.quantity}</p>
                    <p class="price"><strong>Price:</strong> ₹${order.price.toFixed(2)} × ${order.quantity} = ₹${totalPrice.toFixed(2)}</p>
                </div>
            `;
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
                ${ticketData.status === 'Confirmed' ? 
                    `<button onclick="downloadTicket()" class="download-ticket-btn"><i class="fas fa-download"></i> Download Ticket</button>` : 
                    `<button onclick="confirmTicket(${ticketData.reservationId})" class="confirm-ticket-btn">Confirm Ticket</button>
                     <button onclick="cancelTicket(${ticketData.reservationId})" class="cancel-ticket-btn">Cancel Ticket</button>`}
                <button onclick="hideTicketModal()" class="cancel-btn">Close</button>
            </div>
        </div>
    `;
    
    ticketContent.innerHTML = receiptHtml;
    ticketModal.style.display = 'none';
    requestAnimationFrame(() => {
        ticketModal.classList.add('show');
        ticketModal.style.display = 'flex';
    });
}

function downloadTicket() {
    const ticketContent = document.querySelector('.ticket-container').cloneNode(true);
    
    // Remove action buttons from the download version
    const actionButtons = ticketContent.querySelector('.ticket-actions');
    if (actionButtons) {
        actionButtons.remove();
    }
    
    const html = `
        <!DOCTYPE html>
        <html>
        <head>
            <title>Movie Ticket</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    max-width: 800px;
                    margin: 20px auto;
                    padding: 20px;
                }
                .ticket-container {
                    border: 2px solid #333;
                    padding: 20px;
                    border-radius: 10px;
                }
                .status-badge {
                    background-color: #4CAF50;
                    color: white;
                    padding: 5px 10px;
                    border-radius: 5px;
                    display: inline-block;
                    margin-bottom: 10px;
                }
                .ticket-divider {
                    border-top: 1px solid #ddd;
                    margin: 15px 0;
                }
                .employee-info {
                    background-color: #f8f9fa;
                    padding: 10px;
                    border-radius: 5px;
                    margin: 10px 0;
                    border-left: 4px solid #3498db;
                }
                .ticket-total, .snack-total, .grand-total {
                    margin-top: 15px;
                    padding: 10px;
                    background-color: #f8f9fa;
                }
                .grand-total {
                    font-weight: bold;
                    font-size: 1.1em;
                }
                .ticket-footer {
                    margin-top: 20px;
                    padding-top: 10px;
                    border-top: 1px solid #ddd;
                    font-size: 0.9em;
                }
            </style>
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
        </head>
        <body>
            ${ticketContent.outerHTML}
        </body>
        </html>
    `;
    
    const blob = new Blob([html], { type: 'text/html' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `movie-ticket-${Date.now()}.html`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
}

// Add this function to update available seats display after booking
function updateAvailableSeatsDisplay(showId, bookedSeats) {
    // Update all display elements with this show ID (buttons, booking form, etc.)
    document.querySelectorAll(`[data-show-id="${showId}"]`).forEach(element => {
        if (element.classList.contains('available-seats')) {
            // This is a display counter
            const currentCount = parseInt(element.textContent);
            const newCount = currentCount - bookedSeats;
            element.textContent = newCount;
        } else if (element.classList.contains('seats-info')) {
            // This is a button display
            const currentText = element.textContent;
            const currentCount = parseInt(currentText.match(/\d+/)[0]);
            const newCount = currentCount - bookedSeats;
            element.textContent = `${newCount} seats left`;
        }
    });
    
    // If we have a show-time-btn with this show ID, update it too
    const showButton = document.querySelector(`.show-time-btn[data-show-id="${showId}"]`);
    if (showButton) {
        const seatsInfo = showButton.querySelector('.seats-info');
        if (seatsInfo) {
            const currentCount = parseInt(seatsInfo.textContent.match(/\d+/)[0]);
            const newCount = currentCount - bookedSeats;
            seatsInfo.textContent = `${newCount} seats left`;
            seatsInfo.dataset.availableSeats = newCount;
        }
    }
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
                showButton.dataset.showId = show.showId; // Store show ID as data attribute
                
                showButton.innerHTML = `
                    <div class="show-date">${formattedDate}</div>
                    <div class="show-time">${show.showTime}</div>
                    <div class="screen-info">Screen ${show.screenNo}</div>
                    <div class="seats-info" data-show-id="${show.showId}">${show.availableSeats} seats left</div>
                `;
                
                showButton.onclick = () => showBookingForm(
                    movieId, 
                    show.showId, 
                    show.showTime, 
                    show.showDate, 
                    show.screenNo, 
                    show.movieName,
                    show.availableSeats
                );
                
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

// Add the confirmTicket function
function confirmTicket(reservationId) {
    if (!confirm('Are you sure you want to confirm this ticket? This action cannot be undone.')) {
        return;
    }
    
    fetch('/api/booking/confirm', {
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
        showNotification('Ticket confirmed successfully!');
        
        // Update the ticket display with confirmed status and download button
        if (data.ticket) {
            showFinalReceipt(data.ticket);
            // Automatically trigger download
            downloadTicket();
        }
    })
    .catch(error => {
        console.error('Error confirming ticket:', error);
        showNotification(error.message || 'Error confirming ticket. Please try again.', true);
    });
}