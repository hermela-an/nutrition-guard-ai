import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-stack" aria-live="polite" aria-atomic="true">
      <div
        class="toast"
        *ngFor="let t of toast.toasts$ | async"
        [class.success]="t.type === 'success'"
        [class.error]="t.type === 'error'"
        [class.info]="t.type === 'info'"
        (click)="toast.dismiss(t.id)"
        role="status"
      >
        <span class="toast-dot"></span>
        <span class="toast-msg">{{ t.message }}</span>
      </div>
    </div>
  `
})
export class ToastContainerComponent {
  constructor(public readonly toast: ToastService) {}
}

