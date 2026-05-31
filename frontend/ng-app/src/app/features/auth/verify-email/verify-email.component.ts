import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TimeoutError } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './verify-email.component.html',
  styleUrls: ['./verify-email.component.css']
})
export class VerifyEmailComponent {
  loading = true;
  error = false;
  status = '';

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  public readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!token.trim()) {
      this.loading = false;
      this.error = true;
      this.status = 'Missing verification token. Please open the link from your email again.';
      return;
    }

    this.auth
      .verifyEmail(token.trim())
      .pipe(
        timeout(15_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (res) => {
          this.error = false;
          this.status = res?.message || 'Email verified successfully. You can now log in.';
          // Redirect to login without pre-filling email - login page always loads empty
          setTimeout(() => this.router.navigate(['/login']), 1500);
        },
        error: (err) => {
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
            this.status = 'Verification failed. The link may be invalid or expired.';
          }
        }
      });
  }
}

