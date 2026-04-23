import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ProfileService } from '../../../core/services/profile.service';
import { TimeoutError } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  loading = false;
  error = false;
  status = '';
  showPassword = false;
  showResend = false;

  private readonly fb = inject(FormBuilder);
  private readonly profiles = inject(ProfileService);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

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
    if (this.loading) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error = true;
      this.status = 'Please enter a valid email and password (min 6 characters).';
      this.showResend = false;
      return;
    }
    this.loading = true;
    this.status = 'Logging in...';
    this.error = false;
    this.showResend = false;
    this.auth
      .login(this.form.value.email!.trim(), this.form.value.password!)
      .pipe(
        timeout(15_000),
        finalize(() => (this.loading = false))
      )
      .subscribe({
        next: () => {
          this.error = false;
          this.status = 'Login successful. Checking your profile...';
          this.showResend = false;

          if (this.auth.isAdmin()) {
            setTimeout(() => this.router.navigateByUrl('/admin'), 300);
            return;
          }

          this.profiles
            .getProfile()
            .pipe(timeout(10_000))
            .subscribe({
              next: (profile) => {
                const hasProfile = !!(profile && (profile as any).user_id);
                const nextUrl = hasProfile ? '/dashboard' : '/profile-setup';
                setTimeout(() => this.router.navigateByUrl(nextUrl), 300);
              },
              error: () => {
                // If profile fetch fails, still let the user proceed to setup.
                setTimeout(() => this.router.navigateByUrl('/profile-setup'), 300);
              }
            });
        },
        error: (err) => {
          this.error = true;
          if (err instanceof TimeoutError) {
            this.status = 'Request timed out. Please try again.';
          } else if (err?.status === 0) {
            this.status = 'Cannot reach server. Please ensure backend is running.';
          } else if (typeof err?.error === 'string' && err.error.trim()) {
            this.status = err.error;
          } else {
            this.status = 'Login failed';
          }

          const msg = (typeof err?.error === 'string' ? err.error : '').toLowerCase();
          this.showResend = msg.includes('email not verified');
        }
      });
  }

  resendVerification(): void {
    if (this.loading) return;
    const email = this.form.value.email?.trim() ?? '';
    if (!email) {
      this.error = true;
      this.status = 'Please enter your email address first.';
      return;
    }

    this.loading = true;
    this.auth.resendVerification(email).subscribe({
      next: (res) => {
        this.error = false;
        this.status = res?.message || 'If an account exists for that email, a verification link has been sent.';
        this.showResend = false;
      },
      error: () => {
        // Keep generic to avoid enumeration and avoid blocking the user.
        this.error = false;
        this.status = 'If an account exists for that email, a verification link has been sent.';
        this.showResend = false;
      },
      complete: () => (this.loading = false)
    });
  }
}
