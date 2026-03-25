import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastItem {
  id: string;
  type: ToastType;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly subject = new BehaviorSubject<ToastItem[]>([]);
  readonly toasts$ = this.subject.asObservable();

  success(message: string): void {
    this.push({ type: 'success', message });
  }

  error(message: string): void {
    this.push({ type: 'error', message });
  }

  info(message: string): void {
    this.push({ type: 'info', message });
  }

  dismiss(id: string): void {
    this.subject.next(this.subject.value.filter((t) => t.id !== id));
  }

  private push(input: Omit<ToastItem, 'id'>): void {
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const toast: ToastItem = { id, ...input };
    this.subject.next([toast, ...this.subject.value].slice(0, 4));
    setTimeout(() => this.dismiss(id), input.type === 'error' ? 5200 : 3800);
  }
}

