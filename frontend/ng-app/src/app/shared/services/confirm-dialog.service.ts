import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ConfirmIntent = 'default' | 'danger';

export interface ConfirmDialogState {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  intent?: ConfirmIntent;
}

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly subject = new BehaviorSubject<ConfirmDialogState | null>(null);
  readonly state$ = this.subject.asObservable();

  private resolver: ((value: boolean) => void) | null = null;

  open(state: ConfirmDialogState): Promise<boolean> {
    if (this.resolver) {
      this.resolver(false);
      this.resolver = null;
    }

    this.subject.next({
      cancelText: 'Cancel',
      confirmText: 'Confirm',
      intent: 'default',
      ...state
    });

    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
    });
  }

  resolve(value: boolean): void {
    if (this.resolver) {
      this.resolver(value);
      this.resolver = null;
    }
    this.subject.next(null);
  }
}

