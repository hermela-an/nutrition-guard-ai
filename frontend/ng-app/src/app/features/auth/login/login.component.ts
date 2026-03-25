import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

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

  private readonly fb = inject(FormBuilder);

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
        const nextUrl = this.auth.isAdmin() ? '/admin' : '/dashboard';
        setTimeout(() => this.router.navigateByUrl(nextUrl), 500);
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
