import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Login from '../pages/Login';

const login = vi.fn();

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ login, user: null }),
}));

describe('Login form', () => {
  beforeEach(() => {
    login.mockReset();
  });

  it('shows required-field errors and does not call the API', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><Login /></MemoryRouter>);

    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText('Email is required.')).toBeInTheDocument();
    expect(screen.getByText('Password is required.')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('validates the email shape', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><Login /></MemoryRouter>);

    await user.type(screen.getByLabelText(/email/i), 'not-an-email');
    await user.type(screen.getByLabelText(/password/i), 'Password123');
    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('submits valid credentials', async () => {
    login.mockResolvedValue({ role: 'STUDENT', name: 'Aarav' });
    const user = userEvent.setup();
    render(<MemoryRouter><Login /></MemoryRouter>);

    await user.type(screen.getByLabelText(/email/i), 'aarav@smartlib.test');
    await user.type(screen.getByLabelText(/password/i), 'Password123');
    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(login).toHaveBeenCalledWith('aarav@smartlib.test', 'Password123');
  });

  it('keeps the entered values and shows the server error after a failed login', async () => {
    login.mockRejectedValue(new Error('Invalid email or password.'));
    const user = userEvent.setup();
    render(<MemoryRouter><Login /></MemoryRouter>);

    await user.type(screen.getByLabelText(/email/i), 'aarav@smartlib.test');
    await user.type(screen.getByLabelText(/password/i), 'wrong-password');
    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toHaveValue('aarav@smartlib.test');
    expect(screen.getByLabelText(/password/i)).toHaveValue('wrong-password');
  });
});
