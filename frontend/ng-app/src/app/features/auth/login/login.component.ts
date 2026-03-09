import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
  <main class="auth-wrap">
    <section class="auth-shell">
      <aside class="auth-side">
        <a class="brand" routerLink="/" style="color:#fff;"><span class="brand-dot"></span> NutriGuard AI</a>
        <h2 style="margin-top:30px;">Welcome back.</h2>
        <p>Log in to access your medically-aware dashboard and nutrition plan.</p>
      </aside>
      <div class="auth-form">
        <h2>Login</h2>
        <p class="lead">Secure access to your profile.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="field"><label>Email</label><input class="input auth-credential" formControlName="email" type="email" autocomplete="email" placeholder="you@example.com"></div>
          <div class="field">
            <label>Password</label>
            <div class="spaced" style="align-items:stretch;">
              <input class="input auth-credential" formControlName="password" [type]="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="********">
              <button
                class="icon-btn"
                type="button"
                (click)="togglePassword()"
                [attr.aria-label]="showPassword ? 'Hide password' : 'Show password'"
                [title]="showPassword ? 'Hide password' : 'Show password'">
                <svg *ngIf="!showPassword" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                  <path d="M1.5 12s4-7 10.5-7 10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12Z" fill="none" stroke="currentColor" stroke-width="1.8"/>
                  <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/>
                </svg>
                <svg *ngIf="showPassword" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                  <path d="m3 3 18 18" fill="none" stroke="currentColor" stroke-width="1.8"/>
                  <path d="M10.6 6.4A11.7 11.7 0 0 1 12 6c6.5 0 10.5 6 10.5 6a16 16 0 0 1-4 4.9M6.1 9A16.9 16.9 0 0 0 1.5 12s4 7 10.5 7a10 10 0 0 0 4-.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                </svg>
              </button>
            </div>
            <small class="muted">Password must be at least 6 characters.</small>
          </div>
          <button class="btn primary" [disabled]="loading || form.invalid" type="submit" style="width:100%; margin-top:8px;">Login</button>
        </form>
        <div *ngIf="status" class="status" [class.ok]="!error" [class.err]="error">{{ status }}</div>
        <p style="margin-top:12px; color:var(--muted);">No account? <a routerLink="/signup" style="color:var(--brand); font-weight:700;">Sign up</a></p>
      </div>
    </section>
  </main>
  `
})
export class LoginComponent {
  loading = false;
  error = false;
  status = '';
  showPassword = false;

  private readonly fb = inject(FormBuilder);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error = true;
      this.status = 'Please enter a valid email and password (min 6 characters).';
      return;
    }
    this.loading = true;
    this.status = '';
    this.auth.login(this.form.value.email!.trim(), this.form.value.password!).subscribe({
      next: () => {
        this.error = false;
        this.status = 'Login successful. Redirecting...';
        setTimeout(() => this.router.navigateByUrl('/dashboard'), 500);
      },
      error: (err) => {
        this.error = true;
        if (err?.status === 0) {
          this.status = 'Cannot reach server. Please ensure backend is running.';
        } else if (typeof err?.error === 'string' && err.error.trim()) {
          this.status = err.error;
        } else {
          this.status = 'Login failed';
        }
        this.loading = false;
      },
      complete: () => (this.loading = false)
    });
  }
}
