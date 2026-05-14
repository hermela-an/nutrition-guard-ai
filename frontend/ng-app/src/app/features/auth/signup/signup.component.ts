import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent {
  loading = false;
  error = false;
  status = '';
  messageType: 'success' | 'error' | 'warning' | '' = '';
  showPassword = false;
  showResend = false;

  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);

  form = this.fb.group({
    fullName: [''],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  constructor(public readonly auth: AuthService, private readonly router: Router) {}

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  isInvalid(name: 'email' | 'password'): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  showError(name: 'email' | 'password', error: string): boolean {
    const control = this.form.get(name);
    return !!control && (control.dirty || control.touched) && !!control.getError(error);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error = true;
      this.messageType = 'error';
      this.status = 'Please enter valid details. Password must be at least 6 characters.';
      return;
    }
    this.loading = true;
    this.status = '';
    this.messageType = '';
    this.showResend = false;
    this.auth.register(
      this.form.value.email!.trim(),
      this.form.value.password!,
      (this.form.value.fullName ?? '').toString().trim()
    ).subscribe({
      next: (_res) => {
        this.error = false;
        this.messageType = 'success';
        this.status = 'Verification email sent successfully. Please check your email and verify your account before logging in.';
        this.showResend = true;
      },
      error: (err) => {
        this.error = true;
        const raw = this.extractErrorMessage(err).toLowerCase();
        if (err?.status === 0) {
          this.messageType = 'error';
          this.status = 'Cannot reach server. Please ensure the backend is running.';
        } else if (raw.includes('email already exists')) {
          this.messageType = 'warning';
          this.status = 'An account with this email already exists. Please log in or reset your password.';
        } else if (raw.includes('verification email has been sent') || raw.includes('check your inbox')) {
          this.messageType = 'warning';
          this.status = 'This email is already registered but not yet verified. A new verification email has been sent — please check your inbox.';
        } else if (raw) {
          this.messageType = 'error';
          this.status = raw;
        } else {
          this.messageType = 'error';
          this.status = 'Signup failed. Please try again.';
        }
        this.loading = false;
        this.cdr.detectChanges();
      },
      complete: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  resendVerification(): void {
    if (this.loading) return;
    const email = this.form.value.email?.trim() ?? '';
    if (!email) return;

    this.loading = true;
    this.auth.resendVerification(email).subscribe({
      next: () => {
        this.error = false;
        this.messageType = 'success';
        this.status = 'Verification email sent again. Please check your inbox.';
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = false;
        this.messageType = 'success';
        this.status = 'Verification email sent again. Please check your inbox.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private extractErrorMessage(err: any): string {
    if (typeof err?.error === 'string' && err.error.trim()) return err.error.trim();
    if (err?.error?.message && typeof err.error.message === 'string') return err.error.message.trim();
    if (err?.error?.text && typeof err.error.text === 'string') return err.error.text.trim();
    if (typeof err?.message === 'string' && err.message.trim()) return err.message.trim();
    if (typeof err?.statusText === 'string' && err.statusText.trim()) return err.statusText.trim();
    return '';
  }
}
