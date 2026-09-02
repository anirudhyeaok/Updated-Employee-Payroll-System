'use strict';

let currentUser = null;

function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(screen => {
        screen.classList.add('hidden');
        screen.classList.remove('active');
    });

    const target = document.getElementById(screenId);
    if (target) {
        target.classList.remove('hidden');
        target.classList.add('active');
    }
}

function switchSection(clickedBtn, screenId) {
    const screen = document.getElementById(screenId);
    if (!screen) return;

    screen.querySelectorAll('.nav-btn').forEach(button => button.classList.remove('active'));
    screen.querySelectorAll('.content-section').forEach(section => {
        section.classList.add('hidden');
        section.classList.remove('active');
    });

    clickedBtn.classList.add('active');
    const targetId = clickedBtn.getAttribute('data-target');
    const targetSection = document.getElementById(targetId);
    if (targetSection) {
        targetSection.classList.remove('hidden');
        targetSection.classList.add('active');
    }
}

function jumpToSection(sectionId, screenId) {
    const screen = document.getElementById(screenId);
    if (!screen) return;
    const button = screen.querySelector(`.nav-btn[data-target="${sectionId}"]`);
    if (button) {
        switchSection(button, screenId);
    }
}

function resetSections(screenId, defaultSectionId) {
    const screen = document.getElementById(screenId);
    if (!screen) return;
    const button = screen.querySelector(`.nav-btn[data-target="${defaultSectionId}"]`);
    if (button) {
        switchSection(button, screenId);
    }
}

function togglePassword() {
    const input = document.getElementById('password');
    const button = document.getElementById('toggle-password-btn');
    if (!input) return;
    const isHidden = input.type === 'password';
    input.type = isHidden ? 'text' : 'password';
    if (button) {
        button.textContent = isHidden ? 'Hide' : 'Show';
    }
}

function cap(value) {
    if (!value) return '';
    return value.charAt(0).toUpperCase() + value.slice(1);
}

function formatCurrency(value) {
    const amount = Number(value || 0);
    return `Rs. ${amount.toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })}`;
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, char => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[char]);
}

function showError(message) {
    const errorBox = document.getElementById('error-box');
    const errorText = document.getElementById('error-text');
    if (!errorBox || !errorText) return;
    errorText.textContent = message;
    errorBox.classList.remove('hidden');
    errorBox.classList.remove('shake');
    void errorBox.offsetWidth;
    errorBox.classList.add('shake');
}

function clearError() {
    const errorBox = document.getElementById('error-box');
    if (errorBox) {
        errorBox.classList.add('hidden');
    }
}

function setLoginLoading(isLoading) {
    const loginBtn = document.getElementById('login-btn');
    const btnLabel = document.getElementById('btn-label');
    const spinner = document.getElementById('btn-spinner');
    if (!loginBtn || !btnLabel || !spinner) return;
    loginBtn.disabled = isLoading;
    btnLabel.classList.toggle('hidden', isLoading);
    spinner.classList.toggle('hidden', !isLoading);
}

async function apiFetch(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });

    if (response.status === 401) {
        await forceLogoutToLogin();
        throw new Error('Session expired');
    }

    return response;
}

async function handleLogin() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();

    if (!username || !password) {
        showError('Please enter both username and password.');
        return;
    }

    clearError();
    setLoginLoading(true);

    try {
        const response = await apiFetch('/api/login', {
            method: 'POST',
            body: JSON.stringify({ username, password })
        });
        const data = await response.json();

        if (!data.success) {
            showError(data.message || 'Invalid credentials.');
            return;
        }

        currentUser = { id: data.userId, username: data.username, role: data.role };
        document.getElementById('password').value = '';
        await activateUserSession();
    } catch (error) {
        console.error(error);
        showError('Cannot reach the server. Check that Main.java is running on port 8000.');
    } finally {
        setLoginLoading(false);
    }
}

async function activateUserSession() {
    if (!currentUser) return;

    if (currentUser.role === 'admin') {
        if (typeof setupAdmin === 'function') {
            await setupAdmin(currentUser);
        }
        showScreen('admin-screen');
    } else {
        if (typeof setupEmployee === 'function') {
            await setupEmployee(currentUser);
        }
        showScreen('employee-screen');
    }
}

async function restoreSession() {
    try {
        const response = await fetch('/api/session');
        if (!response.ok) {
            showScreen('login-screen');
            return;
        }

        const data = await response.json();
        if (!data.success) {
            showScreen('login-screen');
            return;
        }

        currentUser = { id: data.userId, username: data.username, role: data.role };
        await activateUserSession();
    } catch (error) {
        console.error('Session restore failed:', error);
        showScreen('login-screen');
    }
}

async function handleLogout() {
    try {
        await fetch('/api/logout', { method: 'POST' });
    } catch (error) {
        console.error('Logout failed:', error);
    }
    await forceLogoutToLogin();
}

async function forceLogoutToLogin() {
    currentUser = null;
    document.getElementById('username').value = '';
    document.getElementById('password').value = '';
    clearError();
    showScreen('login-screen');
}

document.addEventListener('DOMContentLoaded', () => {
    ['username', 'password'].forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            input.addEventListener('keydown', event => {
                if (event.key === 'Enter') {
                    handleLogin();
                }
            });
        }
    });

    restoreSession();
});
