document.addEventListener('DOMContentLoaded', function() {
    loadMovies();
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

function showBookingForm(movieId) {
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'block';
    bookingForm.dataset.movieId = movieId;
    
    bookingForm.scrollIntoView({ behavior: 'smooth' });
}

function hideBookingForm() {
    const bookingForm = document.getElementById('bookingForm');
    bookingForm.style.display = 'none';
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
        movieId: parseInt(this.closest('.booking-form').dataset.movieId),
        name: document.getElementById('name').value,
        age: parseInt(document.getElementById('age').value),
        gender: document.getElementById('gender').value,
        seats: parseInt(document.getElementById('seats').value)
    };
    
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
        hideBookingForm();
    })
    .catch(error => {
        console.error('Error booking ticket:', error);
        showNotification(error.message || 'Error booking ticket. Please try again.', true);
    });
});
