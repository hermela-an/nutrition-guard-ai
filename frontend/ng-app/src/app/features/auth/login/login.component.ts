import { Component, inject, ChangeDetectorRef, ElementRef, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ProfileService } from '../../../core/services/profile.service';
import { TimeoutError, of } from 'rxjs';
import { catchError, finalize, switchMap, timeout } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit, AfterViewInit {
  @ViewChild('emailInput') private readonly emailInput!: ElementRef<HTMLInputElement>;
  @ViewChild('passwordInput') private readonly passwordInput!: ElementRef<HTMLInputElement>;

  loading = false;
  error = false;
  status = '';
  messageType: 'success' | 'error' | 'warning' | '' = '';
  showPassword = false;
  showResend = false;

  private readonly fb = inject(FormBuilder);
  private readonly profiles = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  constructor(public readonly auth: AuthService, private readonly router: Router) {}

  ngOnInit(): void {
    // Login page always loads with empty fields.
    // Only browser autofill suggestions should be available, not automatic page-load filling.
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.form.reset({ email: '', password: '' }, { emitEvent: false });
      this.emailInput.nativeElement.value = '';
      this.passwordInput.nativeElement.value = '';
      this.cdr.detectChanges();
    }, 0);
  }

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
      this.messageType = 'error';
      this.status = 'Please enter a valid email and password (min 6 characters).';
      this.showResend = false;
      return;
    }

    this.loading = true;
    this.status = '';
    this.messageType = '';
    this.error = false;
    this.showResend = false;

    this.auth
      .login(this.form.value.email!.trim(), this.form.value.password!)
      .pipe(
        timeout(8_000),
        switchMap(() => {
          // Admin users skip profile check entirely
          if (this.auth.isAdmin()) {
            return of({ isAdmin: true, nextUrl: '/admin' });
          }
          // For regular users: fetch profile, silently fall back to setup on any error
          return this.profiles.getProfile().pipe(
            timeout(8_000),
            catchError(() => of(null)),   // <-- suppresses 404/any error from console
            switchMap(profile => {
              const hasProfile = !!(profile && (profile as any).user_id);
              return of({ isAdmin: false, nextUrl: hasProfile ? '/dashboard' : '/profile-setup' });
            })
          );
        }),
        finalize(() => (this.loading = false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: ({ nextUrl }) => {
          this.router.navigateByUrl(nextUrl);
        },
        error: (err) => {
          this.error = true;
          const raw = this.extractErrorMessage(err).toLowerCase();

          if (err instanceof TimeoutError) {
            this.messageType = 'error';
            this.status = 'Request timed out. Please try again.';
          } else if (err?.status === 0) {
            this.messageType = 'error';
            this.status = 'Cannot reach server. Please ensure the backend is running.';
          } else if (raw.includes('email not verified')) {
            this.messageType = 'warning';
            this.status = 'Your email is not verified yet. Please verify your email before logging in.';
          } else if (raw.includes('invalid credentials')) {
            this.messageType = 'error';
            this.status = 'Invalid email or password.';
          } else if (raw.includes('no account') || raw.includes('user not found') || raw.includes('email not registered')) {
            this.messageType = 'error';
            this.status = 'No account found with this email. Please sign up first.';
          } else if (raw.includes('inactive')) {
            this.messageType = 'error';
            this.status = 'This account has been deactivated.';
          } else if (raw.trim()) {
            this.messageType = 'error';
            this.status = raw;
          } else {
            this.messageType = 'error';
            this.status = 'Login failed. Please try again.';
          }
          this.showResend = raw.includes('email not verified');
          this.loading = false;
          this.cdr.detectChanges();
        }
      });
  }

  resendVerification(): void {
    if (this.loading) return;
    const email = this.form.value.email?.trim() ?? '';
    if (!email) {
      this.error = true;
      this.messageType = 'error';
      this.status = 'Please enter your email address first.';
      return;
    }

    this.loading = true;
    this.auth.resendVerification(email)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => (this.loading = false))
      )
      .subscribe({
        next: (res) => {
          this.error = false;
          this.messageType = 'success';
          this.status = res?.message || 'If an account exists for that email, a verification link has been sent.';
          this.showResend = false;
          this.cdr.detectChanges();
        },
        error: () => {
          // Keep generic to avoid account enumeration
          this.error = false;
          this.messageType = 'success';
          this.status = 'If an account exists for that email, a verification link has been sent.';
          this.showResend = false;
          this.cdr.detectChanges();
        }
      });
  }

  /**
   * Robustly extract the error message from an Angular HttpErrorResponse.
   * The backend sends error bodies as plain text (no Content-Type header),
   * so Angular may store the message in different places depending on
   * whether JSON parsing succeeded or failed.
   */
  private extractErrorMessage(err: any): string {
    // 1. err.error is a plain string (most common for text error bodies)
    if (typeof err?.error === 'string' && err.error.trim()) {
      return err.error.trim();
    }
    // 2. err.error is an object with a message property (JSON error body)
    if (err?.error?.message && typeof err.error.message === 'string') {
      return err.error.message.trim();
    }
    // 3. err.error is an object with a text property (Angular JSON parse fallback)
    if (err?.error?.text && typeof err.error.text === 'string') {
      return err.error.text.trim();
    }
    // 4. err.message (general Error or HttpErrorResponse message)
    if (typeof err?.message === 'string' && err.message.trim()) {
      return err.message.trim();
    }
    // 5. err.statusText
    if (typeof err?.statusText === 'string' && err.statusText.trim()) {
      return err.statusText.trim();
    }
    return '';
  }
}
