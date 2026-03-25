import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmDialogService } from '../services/confirm-dialog.service';

@Component({
  selector: 'app-confirm-dialog-host',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ng-container *ngIf="confirm.state$ | async as s">
      <div class="modal-backdrop" (click)="confirm.resolve(false)">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-head">
            <h3>{{ s.title }}</h3>
            <button type="button" class="btn ghost small" (click)="confirm.resolve(false)">Close</button>
          </div>
          <p class="muted" style="margin:12px 0 0;">{{ s.message }}</p>

          <div class="modal-actions">
            <button type="button" class="btn ghost" (click)="confirm.resolve(false)">{{ s.cancelText }}</button>
            <button
              type="button"
              class="btn"
              [class.danger]="s.intent === 'danger'"
              [class.primary]="s.intent !== 'danger'"
              (click)="confirm.resolve(true)"
            >
              {{ s.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </ng-container>
  `
})
export class ConfirmDialogHostComponent {
  constructor(public readonly confirm: ConfirmDialogService) {}
}

