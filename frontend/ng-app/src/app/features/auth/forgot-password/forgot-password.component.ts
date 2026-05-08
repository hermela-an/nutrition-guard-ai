import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TimeoutError } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.css']
})
export class ForgotPasswordComponent {
  loading = false;
  error = false;
  success = false;
  status = '';

  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  constructor(
    public readonly auth: AuthService,
    private readonly router: Router
  ) {}

  isInvalid(): boolean {
    const control = this.form.get('email');
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  showError(error: string): boolean {
    const control = this.form.get('email');
    return !!control && (control.dirty || control.touched) && !!control.getError(error);
  }

  submit(): void {
    if (this.loading) return;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error = true;
      this.success = false;
      this.status = 'Please enter a valid email address.';
      this.cdr.detectChanges();
      return;
    }

    this.loading = true;
    this.status = '';
    this.error = false;
    this.success = false;
    const email = this.form.value.email!.trim();

    this.auth
      .forgotPassword(email)
      .pipe(
        timeout(15_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.loading = false;
          this.error = false;
          this.success = true;
          this.status =
            'If the email exists in our system, a password reset link has been sent to your email. Please check your inbox.';
      },
      error: (err) => {
          this.loading = false;
          this.error = true;
          this.success = false;
          if (err instanceof TimeoutError) {
            this.status = 'Request timed out. Please try again.';
          } else if (err?.status === 0) {
            this.status = 'Cannot reach server. Please ensure backend is running.';
          } else if (err?.status === 429) {
            this.status = 'Too many requests. Please wait a moment and try again.';
          } else if (typeof err?.error === 'string' && err.error.trim()) {
            this.status = err.error;
          } else if (typeof err?.error?.message === 'string' && err.error.message.trim()) {
            this.status = err.error.message;
          } else {
            this.status = 'Something went wrong while sending the reset link. Please try again.';
          }
      }
      });
  }

  resetMessage(): void {
    this.status = '';
    this.error = false;
    this.success = false;
    this.cdr.detectChanges();
  }

  backToLogin(): void {
    this.router.navigateByUrl('/login');
  }
}
