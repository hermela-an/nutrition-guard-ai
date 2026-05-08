import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TimeoutError } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.css']
})
export class ResetPasswordComponent {
  loading = false;
  error = false;
  status = '';
  showPassword = false;

  token = '';
  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);

  form = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(6)]]
  });

  constructor(
    route: ActivatedRoute,
    public readonly auth: AuthService,
    private readonly router: Router
  ) {
    route.queryParamMap.subscribe((params) => {
      const token =
        (params.get('token') ?? params.get('resetToken') ?? params.get('t') ?? '').trim();
      this.token = token;
    });
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  isInvalid(name: 'password' | 'confirmPassword'): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  showError(name: 'password' | 'confirmPassword', error: string): boolean {
    const control = this.form.get(name);
    return !!control && (control.dirty || control.touched) && !!control.getError(error);
  }

  private passwordsMatch(): boolean {
    return (this.form.value.password ?? '') === (this.form.value.confirmPassword ?? '');
  }

  submit(): void {
    if (this.loading) return;

    if (!this.token) {
      this.error = true;
      this.status = 'Invalid or missing reset token.';
      this.cdr.detectChanges();
      return;
    }
    if (this.form.invalid || !this.passwordsMatch()) {
      this.form.markAllAsTouched();
      this.error = true;
      this.status = this.passwordsMatch()
        ? 'Please enter a valid password (min 6 characters).'
        : 'Passwords do not match.';
      this.cdr.detectChanges();
      return;
    }

    this.loading = true;
    this.status = '';
    this.error = false;

    this.auth
      .resetPassword(this.token, this.form.value.password!)
      .pipe(
        timeout(15_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (res) => {
          this.loading = false;
          this.error = false;
          this.status = res?.message || 'Password updated successfully. Redirecting to login...';
          setTimeout(() => this.router.navigateByUrl('/login'), 800);
        },
        error: (err) => {
          this.loading = false;
          this.error = true;
          if (err instanceof TimeoutError) {
            this.status = 'Request timed out. Please try again.';
          } else if (err?.status === 0) {
            this.status = 'Cannot reach server. Please ensure backend is running.';
          } else if (typeof err?.error === 'string' && err.error.trim()) {
            this.status = err.error;
          } else if (typeof err?.error?.message === 'string' && err.error.message.trim()) {
            this.status = err.error.message;
          } else {
            this.status = 'Reset failed';
          }
        }
      });
  }
}
