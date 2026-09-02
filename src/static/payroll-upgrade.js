'use strict';

let cachedEmployees = [];
let filteredEmployees = [];
let cachedAdmins = [];
let cachedAdjustments = [];
let cachedPendingAdjustments = [];
let cachedPayrollHistory = [];
let cachedComplaints = [];
let cachedPayslips = [];
let cachedEmployeeComplaints = [];
let cachedEmployeeRevisions = [];
let cachedAnalytics = [];
let selectedEmployeeId = null;

function setStatusText(id, message, isError = false) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = message;
    element.style.color = isError ? '#e48a8a' : 'var(--text-2)';
}

function buildEmployeePayload(prefix) {
    return {
        fullName: document.getElementById(`${prefix}name`).value.trim(),
        department: document.getElementById(`${prefix}dept`).value.trim(),
        designation: document.getElementById(`${prefix}desig`).value.trim(),
        baseSalary: Number(document.getElementById(`${prefix}salary`).value),
        username: document.getElementById(`${prefix}user`).value.trim(),
        password: document.getElementById(`${prefix}pass`).value.trim()
    };
}

function clearEmployeeCreateForm() {
    ['name', 'dept', 'desig', 'salary', 'user', 'pass'].forEach(field => {
        const input = document.getElementById(`page-new-${field}`);
        if (input) input.value = '';
    });
}

function populateEmployeeSelects() {
    const options = cachedEmployees.map(employee => `
        <option value="${employee.emp_id}">${escapeHtml(employee.name)} - ${escapeHtml(employee.role)}</option>
    `).join('');

    ['adjustment-employee-select', 'admin-profile-select'].forEach(id => {
        const select = document.getElementById(id);
        if (select) {
            select.innerHTML = options || '<option value="">No employees found</option>';
        }
    });
}

function renderEmployeeTable(employees) {
    const container = document.getElementById('employees-table-wrap');
    if (!container) return;

    if (!employees.length) {
        container.className = 'placeholder-state';
        container.innerHTML = `
            <p>No matching employees found.</p>
            <small>Try another search term or onboard a new employee.</small>`;
        return;
    }

    container.className = '';
    const departmentCount = new Set(employees.map(employee => employee.dept)).size;
    const averageSalary = employees.reduce((sum, employee) => sum + Number(employee.salary || 0), 0) / employees.length;

    container.innerHTML = `
        <div class="inline-grid stats-grid">
            <div class="mini-card"><small>Employees</small><strong>${employees.length}</strong></div>
            <div class="mini-card"><small>Departments</small><strong>${departmentCount}</strong></div>
            <div class="mini-card"><small>Average Salary</small><strong>${formatCurrency(averageSalary)}</strong></div>
        </div>
        <div class="table-shell">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Department</th>
                        <th>Designation</th>
                        <th>Payroll Band</th>
                        <th>Status</th>
                        <th>Base Salary</th>
                        <th style="text-align:right;">Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${employees.map(employee => {
                        const subtext = (employee.email && employee.email !== 'Pending') ? `<span>${escapeHtml(employee.email)}</span>` : '';
                        
                        return `
                        <tr>
                            <td>
                                <strong>${escapeHtml(employee.name)}</strong>
                                ${subtext}
                            </td>
                            <td>${escapeHtml(employee.dept)}</td>
                            <td>${escapeHtml(employee.role)}</td>
                            <td>${escapeHtml(employee.policy || 'Standard Band')}</td>
                            <td>
                                <span class="tag" style="${employee.status === 'Active' ? 'color:#50fa7b; background:rgba(80,250,123,0.15); border:1px solid rgba(80,250,123,0.3); font-weight:700;' : ''}">
                                    ${escapeHtml(employee.status || 'Active')}
                                </span>
                            </td>
                            <td>${formatCurrency(employee.salary)}</td>
                            <td style="text-align:right; white-space:nowrap;">
                                <button class="btn-ghost btn-sm" onclick="openAdminEmployeeProfile(${employee.emp_id})">Profile</button>
                                <button class="btn-ghost btn-sm" style="color:#ff5555; margin-left:8px;" onclick="deleteEmployeeAction(${employee.emp_id})">Delete</button>
                            </td>
                        </tr>`;
                    }).join('')}
                </tbody>
            </table>
        </div>`;
}

async function deleteEmployeeAction(empId) {
    if (!confirm("Are you sure you want to permanently delete this employee? This will also remove their user account and cannot be undone.")) {
        return;
    }

    try {
        const response = await apiFetch('/api/admin/delete-employee', {
            method: 'POST',
            body: JSON.stringify({ empId: Number(empId) })
        });
        const data = await response.json();
        
        if (data.success) {
            alert("Employee successfully removed from the system.");
            await fetchEmployees();
        } else {
            alert(data.message || "Failed to delete employee.");
        }
    } catch (error) {
        console.error("Delete failed:", error);
        alert("Server error while trying to delete employee.");
    }
}

function filterEmployees() {
    const term = document.getElementById('employee-search-input')?.value.trim().toLowerCase() || '';
    filteredEmployees = !term
        ? [...cachedEmployees]
        : cachedEmployees.filter(employee =>
            [employee.name, employee.dept, employee.role, employee.policy, employee.status, employee.email]
                .filter(Boolean)
                .some(value => String(value).toLowerCase().includes(term))
        );
    renderEmployeeTable(filteredEmployees);
}

async function fetchEmployees() {
    const response = await apiFetch('/api/admin/employees');
    cachedEmployees = await response.json();
    const countEl = document.getElementById('kpi-emp-count');
    if (countEl) countEl.textContent = cachedEmployees.length;
    populateEmployeeSelects();
    filterEmployees();
    renderOverviewSnapshot();
    return cachedEmployees;
}

async function saveEmployeeFromPage() {
    const payload = buildEmployeePayload('page-new-');
    if (!payload.fullName || !payload.department || !payload.designation || !payload.username || !payload.password || payload.baseSalary <= 0) {
        setStatusText('employee-form-status', 'Fill every employee field with a valid salary.', true);
        return;
    }

    const response = await apiFetch('/api/admin/add-employee', {
        method: 'POST',
        body: JSON.stringify(payload)
    });
    const data = await response.json();
    setStatusText('employee-form-status', data.message || (data.success ? 'Employee created.' : 'Could not create employee.'), !data.success);

    if (data.success) {
        clearEmployeeCreateForm();
        await fetchEmployees();
        jumpToSection('sec-employees', 'admin-screen');
    }
}

async function fetchPayrollHistory() {
    const response = await apiFetch('/api/admin/payroll-history');
    cachedPayrollHistory = await response.json();
    renderPayrollHistory();
    renderOverviewSnapshot();
}

function renderPayrollHistory() {
    const container = document.getElementById('payroll-history-wrap');
    if (!container) return;

    if (!cachedPayrollHistory.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No payroll cycles recorded yet.</p><small>Processed months will appear here with totals and run timestamps.</small>';
        return;
    }

    container.className = '';
    container.innerHTML = `
        <div class="table-shell">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Month</th>
                        <th>Employees</th>
                        <th>Gross</th>
                        <th>Deductions</th>
                        <th>Net</th>
                        <th>Last Run</th>
                    </tr>
                </thead>
                <tbody>
                    ${cachedPayrollHistory.map(item => `
                        <tr>
                            <td>${escapeHtml(item.monthYear)}</td>
                            <td>${item.employeeCount}</td>
                            <td>${formatCurrency(item.totalGross)}</td>
                            <td>${formatCurrency(item.totalDeductions)}</td>
                            <td>${formatCurrency(item.totalNet)}</td>
                            <td>${escapeHtml(String(item.lastRun || '').replace('T', ' ').slice(0, 19))}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>`;
}

async function runPayroll() {
    const monthYear = document.getElementById('payroll-month-input').value.trim();
    if (!monthYear) {
        setStatusText('payroll-run-message', 'Enter a payroll period like April 2026 or 2026-04.', true);
        return;
    }

    const response = await apiFetch('/api/admin/payroll', {
        method: 'POST',
        body: JSON.stringify({ monthYear })
    });
    const data = await response.json();

    setStatusText('payroll-run-message', data.message || (data.success ? `Payroll processed for ${data.monthYear}.` : 'Payroll could not be processed.'), !data.success);
    if (!data.success) return;

    document.getElementById('kpi-slip-count').textContent = data.generated;
    document.getElementById('kpi-period').textContent = data.monthYear;
    document.getElementById('payroll-generated-count').textContent = data.generated;
    document.getElementById('payroll-gross-total').textContent = formatCurrency(data.totalGross);
    document.getElementById('payroll-deduction-total').textContent = formatCurrency(data.totalDeductions);
    document.getElementById('payroll-net-total').textContent = formatCurrency(data.totalNet);

    await Promise.all([fetchPayrollHistory(), fetchDepartmentAnalytics(), fetchPayslips()]);
}

async function fetchAdjustments() {
    const response = await apiFetch('/api/admin/adjustments');
    cachedAdjustments = await response.json();
    renderAdjustments();
}

async function fetchPendingAdjustments() {
    const response = await apiFetch('/api/admin/adjustments/pending');
    cachedPendingAdjustments = await response.json();
    renderPendingApprovals();
    renderOverviewSnapshot();
}

function renderAdjustments() {
    const container = document.getElementById('adjustment-list');
    if (!container) return;

    if (!cachedAdjustments.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No payroll adjustments yet.</p><small>Submitted approvals and reviewed entries will appear here.</small>';
        return;
    }

    container.className = 'card-list';
    container.innerHTML = cachedAdjustments.map(item => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(item.employeeName)}</strong>
                    <span>${escapeHtml(item.monthYear)} - ${escapeHtml(item.reason)}</span>
                </div>
                <div class="align-right">
                    <strong class="${item.type === 'DEDUCTION' ? 'text-red' : 'text-green'}">${item.type === 'DEDUCTION' ? '-' : '+'}${formatCurrency(item.amount)}</strong>
                    <span>${escapeHtml(item.status)}</span>
                </div>
            </div>
            <div class="card-foot">
                <span>Requested by ${escapeHtml(item.requestedBy || 'Admin')}</span>
                <span>${item.reviewedBy ? `Reviewed by ${escapeHtml(item.reviewedBy)}` : 'Awaiting review'}</span>
            </div>
            ${item.adminComment ? `<p class="note-line">Review Note: ${escapeHtml(item.adminComment)}</p>` : ''}
        </div>
    `).join('');
}

function renderPendingApprovals() {
    const container = document.getElementById('adjustment-approval-list');
    if (!container) return;

    if (!cachedPendingAdjustments.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No pending approvals.</p><small>All submitted payroll adjustments have been reviewed.</small>';
        return;
    }

    container.className = 'card-list';
    container.innerHTML = cachedPendingAdjustments.map(item => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(item.employeeName)}</strong>
                    <span>${escapeHtml(item.monthYear)} - ${escapeHtml(item.reason)}</span>
                </div>
                <div class="align-right">
                    <strong class="${item.type === 'DEDUCTION' ? 'text-red' : 'text-green'}">${item.type === 'DEDUCTION' ? '-' : '+'}${formatCurrency(item.amount)}</strong>
                    <span>${escapeHtml(item.requestedBy || 'Admin')}</span>
                </div>
            </div>
            <div class="inline-actions">
                <input type="text" id="approval-note-${item.adjustmentId}" placeholder="Add review note">
                <button class="btn-primary btn-sm" onclick="reviewAdjustment(${item.adjustmentId}, 'Approved')">Approve</button>
                <button class="btn-ghost btn-sm" onclick="reviewAdjustment(${item.adjustmentId}, 'Rejected')">Reject</button>
            </div>
        </div>
    `).join('');
}

async function saveAdjustment() {
    const payload = {
        empId: Number(document.getElementById('adjustment-employee-select').value),
        monthYear: document.getElementById('adjustment-month').value.trim(),
        type: document.getElementById('adjustment-type').value,
        amount: Number(document.getElementById('adjustment-amount').value),
        reason: document.getElementById('adjustment-reason').value.trim()
    };

    if (!payload.empId || !payload.monthYear || payload.amount <= 0 || !payload.reason) {
        setStatusText('adjustment-status', 'Complete the adjustment form with a valid amount.', true);
        return;
    }

    const response = await apiFetch('/api/admin/add-adjustment', {
        method: 'POST',
        body: JSON.stringify(payload)
    });
    const data = await response.json();
    setStatusText('adjustment-status', data.message || (data.success ? 'Adjustment submitted.' : 'Could not submit adjustment.'), !data.success);

    if (data.success) {
        document.getElementById('adjustment-month').value = '';
        document.getElementById('adjustment-amount').value = '';
        document.getElementById('adjustment-reason').value = '';
        document.getElementById('adjustment-type').value = 'EARNING';
        await Promise.all([fetchAdjustments(), fetchPendingAdjustments()]);
    }
}

async function reviewAdjustment(adjustmentId, status) {
    const adminComment = document.getElementById(`approval-note-${adjustmentId}`)?.value.trim() || '';
    const response = await apiFetch('/api/admin/adjustments/review', {
        method: 'POST',
        body: JSON.stringify({ adjustmentId, status, adminComment })
    });
    const data = await response.json();
    setStatusText('adjustment-approval-status', data.message || (data.success ? 'Adjustment reviewed.' : 'Could not review adjustment.'), !data.success);

    if (data.success) {
        await Promise.all([fetchAdjustments(), fetchPendingAdjustments()]);
    }
}

async function fetchAdmins() {
    const response = await apiFetch('/api/admin/admins');
    cachedAdmins = await response.json();

    const container = document.getElementById('admin-list');
    if (!container) return;

    if (!cachedAdmins.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No admin accounts found.</p>';
        return;
    }

    container.className = 'card-list';
    container.innerHTML = cachedAdmins.map(admin => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(admin.username)}</strong>
                    <span>${escapeHtml(admin.role)}</span>
                </div>
                <span class="tag" style="color:#50fa7b; background:rgba(80,250,123,0.15); border:1px solid rgba(80,250,123,0.3); font-weight:700;">Active</span>
            </div>
        </div>
    `).join('');
}

async function saveAdmin() {
    const username = document.getElementById('admin-username').value.trim();
    const password = document.getElementById('admin-password').value.trim();

    if (!username || !password) {
        setStatusText('admin-status', 'Enter both username and password for the new admin.', true);
        return;
    }

    const response = await apiFetch('/api/admin/add-admin', {
        method: 'POST',
        body: JSON.stringify({ username, password })
    });
    const data = await response.json();
    setStatusText('admin-status', data.message || (data.success ? 'Admin account created.' : 'Could not create admin account.'), !data.success);

    if (data.success) {
        document.getElementById('admin-username').value = '';
        document.getElementById('admin-password').value = '';
        await fetchAdmins();
    }
}

async function openAdminEmployeeProfile(empId) {
    selectedEmployeeId = empId;
    const select = document.getElementById('admin-profile-select');
    if (select) select.value = String(empId);
    jumpToSection('sec-employee-profile', 'admin-screen');
    await loadAdminEmployeeProfile();
}

async function loadAdminEmployeeProfile() {
    const empId = Number(document.getElementById('admin-profile-select').value);
    if (!empId) return;
    selectedEmployeeId = empId;

    const [profileResponse, revisionsResponse] = await Promise.all([
        apiFetch(`/api/admin/employee-profile?empId=${empId}`),
        apiFetch(`/api/admin/revisions?empId=${empId}`)
    ]);

    const profile = await profileResponse.json();
    const revisions = await revisionsResponse.json();

    document.getElementById('admin-profile-name').value = profile.fullName || '';
    document.getElementById('admin-profile-username').value = profile.username || '';
    document.getElementById('admin-profile-department').value = profile.department || '';
    document.getElementById('admin-profile-designation').value = profile.designation || '';
    document.getElementById('admin-profile-salary').value = profile.baseSalary || '';
    document.getElementById('admin-profile-status').value = profile.status || 'Active';
    document.getElementById('admin-profile-bank').value = profile.bankAccount || '';
    document.getElementById('admin-profile-email').value = profile.email || '';
    document.getElementById('admin-profile-phone').value = profile.phone || '';
    document.getElementById('admin-profile-address').value = profile.address || '';
    document.getElementById('admin-profile-joining-date').value = profile.joiningDate || '';
    renderRevisionTimeline('admin-profile-revision-list', revisions);
}

async function saveAdminEmployeeProfile() {
    if (!selectedEmployeeId) {
        setStatusText('admin-profile-status-text', 'Select an employee profile first.', true);
        return;
    }

    const updates = {
        fullName: document.getElementById('admin-profile-name').value.trim(),
        department: document.getElementById('admin-profile-department').value.trim(),
        designation: document.getElementById('admin-profile-designation').value.trim(),
        baseSalary: Number(document.getElementById('admin-profile-salary').value),
        status: document.getElementById('admin-profile-status').value,
        bankAccount: document.getElementById('admin-profile-bank').value.trim(),
        email: document.getElementById('admin-profile-email').value.trim(),
        phone: document.getElementById('admin-profile-phone').value.trim(),
        address: document.getElementById('admin-profile-address').value.trim(),
        joiningDate: document.getElementById('admin-profile-joining-date').value
    };

    const response = await apiFetch('/api/admin/employee-profile/update', {
        method: 'POST',
        body: JSON.stringify({ empId: selectedEmployeeId, updates })
    });
    const data = await response.json();
    setStatusText('admin-profile-status-text', data.message || (data.success ? 'Employee profile updated.' : 'Could not update profile.'), !data.success);

    if (data.success) {
        await Promise.all([fetchEmployees(), loadAdminEmployeeProfile()]);
    }
}

function renderRevisionTimeline(containerId, revisions) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!revisions.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No revision history yet.</p>';
        return;
    }

    container.className = 'card-list';
    container.innerHTML = revisions.map(revision => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(revision.changeSummary)}</strong>
                    <span>${escapeHtml(revision.changedBy)} - ${escapeHtml(String(revision.createdAt).replace('T', ' ').slice(0, 19))}</span>
                </div>
            </div>
            <div class="revision-grid">
                <div>
                    <small>Previous</small>
                    <pre>${escapeHtml(JSON.stringify(revision.previousData, null, 2))}</pre>
                </div>
                <div>
                    <small>Updated</small>
                    <pre>${escapeHtml(JSON.stringify(revision.newData, null, 2))}</pre>
                </div>
            </div>
        </div>
    `).join('');
}

async function fetchComplaints() {
    const response = await apiFetch('/api/admin/complaints');
    cachedComplaints = await response.json();
    renderAdminComplaints();
    renderOverviewSnapshot();
}

function renderAdminComplaints() {
    const container = document.getElementById('complaint-list');
    if (!container) return;

    if (!cachedComplaints.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No payroll complaints submitted.</p><small>Employee payroll issues will appear here for resolution.</small>';
        return;
    }

    container.className = 'card-list';
    container.innerHTML = cachedComplaints.map(item => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(item.subject)}</strong>
                    <span>${escapeHtml(item.employeeName)}${item.monthYear ? ` - ${escapeHtml(item.monthYear)}` : ''}</span>
                </div>
                <span class="tag ${item.status === 'Resolved' ? 'tag-success' : 'tag-warning'}">${escapeHtml(item.status)}</span>
            </div>
            <p class="note-line">${escapeHtml(item.message)}</p>
            ${item.adminReply ? `<p class="note-line"><strong>Reply:</strong> ${escapeHtml(item.adminReply)}</p>` : ''}
            <div class="inline-actions">
                <input type="text" id="complaint-reply-${item.complaintId}" placeholder="Write payroll resolution reply">
                <button class="btn-primary btn-sm" onclick="replyToComplaint(${item.complaintId})">Reply & Resolve</button>
            </div>
        </div>
    `).join('');
}

async function replyToComplaint(complaintId) {
    const reply = document.getElementById(`complaint-reply-${complaintId}`)?.value.trim() || '';
    if (!reply) {
        setStatusText('complaint-status', 'Enter a reply before resolving the complaint.', true);
        return;
    }

    const response = await apiFetch('/api/admin/complaints/reply', {
        method: 'POST',
        body: JSON.stringify({ complaintId, reply })
    });
    const data = await response.json();
    setStatusText('complaint-status', data.message || (data.success ? 'Complaint resolved.' : 'Could not resolve complaint.'), !data.success);

    if (data.success) {
        await fetchComplaints();
    }
}

async function fetchDepartmentAnalytics() {
    const month = document.getElementById('analytics-month-input')?.value.trim() || '';
    const response = await apiFetch(`/api/admin/analytics/departments${month ? `?month=${encodeURIComponent(month)}` : ''}`);
    const data = await response.json();
    cachedAnalytics = data.departments || [];
    renderDepartmentAnalytics(data.monthYear || 'All Periods');
}

function renderDepartmentAnalytics(monthLabel) {
    const metrics = document.getElementById('analytics-period-label');
    const chart = document.getElementById('analytics-chart');
    const summary = document.getElementById('analytics-summary');

    if (metrics) metrics.textContent = monthLabel;
    if (!chart || !summary) return;

    if (!cachedAnalytics.length) {
        chart.className = 'placeholder-state';
        chart.innerHTML = '<p>No department payroll data yet.</p>';
        summary.innerHTML = '';
        return;
    }

    const maxNet = Math.max(...cachedAnalytics.map(item => Number(item.netTotal || 0)), 1);
    chart.className = 'bar-stack';
    chart.innerHTML = cachedAnalytics.map(item => `
        <div class="bar-row">
            <div class="bar-label">
                <strong>${escapeHtml(item.department)}</strong>
                <span>${item.employees} employees</span>
            </div>
            <div class="bar-track">
                <div class="bar-fill" style="width:${Math.max(12, (Number(item.netTotal || 0) / maxNet) * 100)}%"></div>
            </div>
            <div class="bar-value">${formatCurrency(item.netTotal)}</div>
        </div>
    `).join('');

    summary.innerHTML = `
        <div class="inline-grid stats-grid">
            <div class="mini-card"><small>Departments</small><strong>${cachedAnalytics.length}</strong></div>
            <div class="mini-card"><small>Total Net</small><strong>${formatCurrency(cachedAnalytics.reduce((sum, item) => sum + Number(item.netTotal || 0), 0))}</strong></div>
            <div class="mini-card"><small>Total Gross</small><strong>${formatCurrency(cachedAnalytics.reduce((sum, item) => sum + Number(item.grossTotal || 0), 0))}</strong></div>
        </div>`;
}

async function downloadExport(type) {
    let url = '';
    const monthInput = document.getElementById('export-payroll-month');
    const month = monthInput ? monthInput.value.trim() : '';

    if (type === 'payroll') {
        url = `/api/admin/export/payroll${month ? `?month=${encodeURIComponent(month)}` : ''}`;
    } else if (type === 'employees') {
        url = '/api/admin/export/employees';
    } else if (type === 'monthly') {
        url = '/api/admin/export/monthly-summary';
    } else if (type === 'quarterly') {
        url = '/api/admin/export/quarterly-summary';
    } else if (type === 'yearly') {
        url = '/api/admin/export/yearly-summary';
    }

    const response = await fetch(url);
    if (response.status === 401) {
        await forceLogoutToLogin();
        return;
    }
    const blob = await response.blob();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    const disposition = response.headers.get('Content-Disposition') || '';
    const match = disposition.match(/filename="([^"]+)"/);
    link.download = match ? match[1] : `${type}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
    const expStatus = document.getElementById('export-status');
    if (expStatus) setStatusText('export-status', 'Export downloaded successfully.');
}

function renderOverviewSnapshot() {
    const latestHistory = cachedPayrollHistory[0];
    const pending = document.getElementById('overview-pending-adjustments');
    const complaints = document.getElementById('overview-open-complaints');
    if (pending) pending.textContent = cachedPendingAdjustments.length;
    if (complaints) complaints.textContent = cachedComplaints.filter(item => item.status !== 'Resolved').length;
    
    const countKPI = document.getElementById('kpi-slip-count');
    const periodKPI = document.getElementById('kpi-period');
    if (countKPI) countKPI.textContent = latestHistory ? latestHistory.employeeCount : 0;
    if (periodKPI) periodKPI.textContent = latestHistory ? latestHistory.monthYear : 'Awaiting run';
}

async function fetchEmployeeProfile() {
    const [profileResponse, revisionsResponse] = await Promise.all([
        apiFetch('/api/employee/profile'),
        apiFetch('/api/employee/revisions')
    ]);

    const profile = await profileResponse.json();
    cachedEmployeeRevisions = await revisionsResponse.json();

    document.getElementById('emp-name-display').textContent = profile.fullName || cap(currentUser.username);
    document.getElementById('emp-avi').textContent = (profile.fullName || currentUser.username || 'E').charAt(0).toUpperCase();
    document.getElementById('emp-welcome').textContent = profile.fullName || cap(currentUser.username);
    document.getElementById('emp-profile-name').value = profile.fullName || '';
    document.getElementById('emp-profile-username').value = profile.username || '';
    document.getElementById('emp-profile-department').value = profile.department || '';
    document.getElementById('emp-profile-designation').value = profile.designation || '';
    document.getElementById('emp-profile-salary').value = profile.baseSalary || '';
    document.getElementById('emp-profile-status').value = profile.status || '';
    document.getElementById('emp-profile-bank').value = profile.bankAccount || '';
    document.getElementById('emp-profile-email').value = profile.email || '';
    document.getElementById('emp-profile-phone').value = profile.phone || '';
    document.getElementById('emp-profile-address').value = profile.address || '';
    document.getElementById('emp-profile-joining-date').value = profile.joiningDate || '';
    renderRevisionTimeline('emp-profile-revision-list', cachedEmployeeRevisions);
}

async function saveOwnProfile() {
    const payload = {
        bankAccount: document.getElementById('emp-profile-bank').value.trim(),
        email: document.getElementById('emp-profile-email').value.trim(),
        phone: document.getElementById('emp-profile-phone').value.trim(),
        address: document.getElementById('emp-profile-address').value.trim()
    };

    const response = await apiFetch('/api/employee/profile', {
        method: 'POST',
        body: JSON.stringify(payload)
    });
    const data = await response.json();
    setStatusText('emp-profile-status-text', data.message || (data.success ? 'Profile updated.' : 'Could not update profile.'), !data.success);

    if (data.success) {
        await fetchEmployeeProfile();
    }
}

async function fetchPayslips() {
    if (!currentUser || currentUser.role !== 'employee') return;
    const response = await apiFetch('/api/employee/payslips');
    cachedPayslips = await response.json();
    renderPayslips();
}

function renderPayslips() {
    const container = document.getElementById('emp-payslip-list');
    if (!container) return;

    if (!cachedPayslips.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No payslips available yet.</p><small>Your payroll records will appear here after the admin runs payroll.</small>';
        const periodEl = document.getElementById('emp-latest-period');
        const countEl = document.getElementById('emp-payslip-count');
        if (periodEl) periodEl.textContent = 'Awaiting payroll';
        if (countEl) countEl.textContent = '0';
        return;
    }

    const periodEl = document.getElementById('emp-latest-period');
    const countEl = document.getElementById('emp-payslip-count');
    if (periodEl) periodEl.textContent = cachedPayslips[0].month;
    if (countEl) countEl.textContent = String(cachedPayslips.length);
    
    container.className = 'card-list';
    container.innerHTML = cachedPayslips.map((slip, index) => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(slip.month)}</strong>
                    <span>${escapeHtml(slip.designation || 'Employee')}</span>
                </div>
                <div class="align-right">
                    <strong class="text-green">${formatCurrency(slip.net)}</strong>
                    <button class="btn-ghost btn-sm" onclick="downloadPayslipPdf(${index})">Download PDF</button>
                </div>
            </div>
            <div class="payslip-grid">
                <div><small>Base Pay</small><strong>${formatCurrency(slip.base)}</strong></div>
                <div><small>HRA</small><strong>${formatCurrency(slip.hra)}</strong></div>
                <div><small>DA</small><strong>${formatCurrency(slip.da)}</strong></div>
                <div><small>Transport</small><strong>${formatCurrency(slip.ta)}</strong></div>
                <div><small>Gross Pay</small><strong>${formatCurrency(slip.gross)}</strong></div>
                ${slip.adjustmentEarnings > 0 ? `<div><small>Bonus / Adjustments</small><strong class="text-green">+${formatCurrency(slip.adjustmentEarnings)}</strong></div>` : ''}
                <div><small>PF Deduction</small><strong style="color:var(--text-2);">-${formatCurrency(slip.pf)}</strong></div>
                <div><small>Professional Tax</small><strong style="color:var(--text-2);">-${formatCurrency(slip.pt)}</strong></div>
                ${slip.lop > 0 ? `<div><small>LOP Deduction (${slip.lopDays}d)</small><strong style="color:var(--text-2);">-${formatCurrency(slip.lop)}</strong></div>` : ''}
                ${slip.adjustmentDeductions > 0 ? `<div><small>Other Deductions</small><strong style="color:var(--text-2);">-${formatCurrency(slip.adjustmentDeductions)}</strong></div>` : ''}
                <div><small>Total Deductions</small><strong style="color:var(--text-2);">-${formatCurrency(slip.deductions)}</strong></div>
            </div>
        </div>
    `).join('');
}

function downloadPayslipPdf(index) {
    const slip = cachedPayslips[index];
    if (!slip) return;

    const printWindow = window.open('', '_blank', 'width=900,height=760');
    if (!printWindow) return;

    printWindow.document.write(`
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Payslip ${escapeHtml(slip.month)}</title>
            <style>
                body { font-family: Arial, sans-serif; padding: 32px; color: #111827; }
                .head { display:flex; justify-content:space-between; margin-bottom:24px; }
                .grid { display:grid; grid-template-columns:repeat(2, 1fr); gap:12px; }
                .card { border:1px solid #d1d5db; border-radius:12px; padding:12px; }
                .label { font-size:12px; text-transform:uppercase; color:#6b7280; margin-bottom:6px; }
                .value { font-size:18px; font-weight:700; }
            </style>
        </head>
        <body>
            <div class="head">
                <div>
                    <h1>PayCore Payslip</h1>
                    <div>${escapeHtml(slip.employeeName || currentUser.username)}<br>${escapeHtml(slip.designation || 'Employee')}<br>${escapeHtml(slip.month)}</div>
                </div>
                <div style="text-align:right;">
                    <div class="label">Net Salary</div>
                    <div style="font-size:30px; font-weight:800; color:#166534;">${formatCurrency(slip.net)}</div>
                </div>
            </div>
            <div class="grid">
                <div class="card"><div class="label">Base</div><div class="value">${formatCurrency(slip.base)}</div></div>
                <div class="card"><div class="label">HRA</div><div class="value">${formatCurrency(slip.hra)}</div></div>
                <div class="card"><div class="label">Bonus/Adjustments</div><div class="value">+${formatCurrency(slip.adjustmentEarnings)}</div></div>
                <div class="card"><div class="label">Transport/DA</div><div class="value">${formatCurrency(slip.ta + slip.da)}</div></div>
                <div class="card"><div class="label">Gross</div><div class="value">${formatCurrency(slip.gross)}</div></div>
                <div class="card"><div class="label">Total Deductions</div><div class="value">${formatCurrency(slip.deductions)}</div></div>
            </div>
            <script>window.onload = function(){ window.print(); };</script>
        </body>
        </html>
    `);
    printWindow.document.close();
}

async function fetchEmployeeComplaints() {
    const response = await apiFetch('/api/employee/complaints');
    cachedEmployeeComplaints = await response.json();
    renderEmployeeComplaints();
}

function renderEmployeeComplaints() {
    const container = document.getElementById('emp-complaint-list');
    if (!container) return;

    if (!cachedEmployeeComplaints.length) {
        container.className = 'placeholder-state';
        container.innerHTML = '<p>No payroll complaints raised yet.</p><small>Use the form above to report any salary, tax, or deduction issue.</small>';
        const openEl = document.getElementById('emp-open-complaints');
        if (openEl) openEl.textContent = '0';
        return;
    }

    const openEl = document.getElementById('emp-open-complaints');
    if (openEl) openEl.textContent = String(cachedEmployeeComplaints.filter(item => item.status !== 'Resolved').length);
    
    container.className = 'card-list';
    container.innerHTML = cachedEmployeeComplaints.map(item => `
        <div class="info-card">
            <div class="card-head">
                <div>
                    <strong>${escapeHtml(item.subject)}</strong>
                    <span>${item.monthYear ? escapeHtml(item.monthYear) : 'General payroll query'}</span>
                </div>
                <span class="tag ${item.status === 'Resolved' ? 'tag-success' : 'tag-warning'}">${escapeHtml(item.status)}</span>
            </div>
            <p class="note-line">${escapeHtml(item.message)}</p>
            ${item.adminReply ? `<p class="note-line"><strong>Admin Reply:</strong> ${escapeHtml(item.adminReply)}</p>` : '<p class="note-line">Awaiting payroll admin response.</p>'}
        </div>
    `).join('');
}

async function createComplaint() {
    const payload = {
        monthYear: document.getElementById('emp-complaint-month').value.trim(),
        subject: document.getElementById('emp-complaint-subject').value.trim(),
        message: document.getElementById('emp-complaint-message').value.trim()
    };

    if (!payload.subject || !payload.message) {
        setStatusText('emp-complaint-status', 'Add a subject and message before submitting.', true);
        return;
    }

    const response = await apiFetch('/api/employee/complaints', {
        method: 'POST',
        body: JSON.stringify(payload)
    });
    const data = await response.json();
    setStatusText('emp-complaint-status', data.message || (data.success ? 'Complaint submitted.' : 'Could not submit complaint.'), !data.success);

    if (data.success) {
        document.getElementById('emp-complaint-month').value = '';
        document.getElementById('emp-complaint-subject').value = '';
        document.getElementById('emp-complaint-message').value = '';
        await fetchEmployeeComplaints();
    }
}

async function setupAdmin(user) {
    const nameEl = document.getElementById('admin-name-display');
    const aviEl = document.getElementById('admin-avi');
    if (nameEl) nameEl.textContent = cap(user.username);
    if (aviEl) aviEl.textContent = user.username.charAt(0).toUpperCase();
    resetSections('admin-screen', 'sec-overview');
    await Promise.all([
        fetchEmployees(),
        fetchPayrollHistory(),
        fetchAdjustments(),
        fetchPendingAdjustments(),
        fetchAdmins(),
        fetchComplaints(),
        fetchDepartmentAnalytics()
    ]);
}

async function setupEmployee(user) {
    const nameEl = document.getElementById('emp-name-display');
    const aviEl = document.getElementById('emp-avi');
    if (nameEl) nameEl.textContent = cap(user.username);
    if (aviEl) aviEl.textContent = user.username.charAt(0).toUpperCase();
    resetSections('employee-screen', 'emp-sec-home');
    await Promise.all([
        fetchEmployeeProfile(),
        fetchPayslips(),
        fetchEmployeeComplaints()
    ]);
}

document.addEventListener('DOMContentLoaded', () => {
    const employeeSearch = document.getElementById('employee-search-input');
    if (employeeSearch) {
        employeeSearch.addEventListener('input', filterEmployees);
    }

    const adminProfileSelect = document.getElementById('admin-profile-select');
    if (adminProfileSelect) {
        adminProfileSelect.addEventListener('change', loadAdminEmployeeProfile);
    }
});