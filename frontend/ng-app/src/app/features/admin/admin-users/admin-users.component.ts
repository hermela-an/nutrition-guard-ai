import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, timeout } from 'rxjs/operators';
import { AdminUser, AdminUsersService } from '../../../core/services/admin-users.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ToastService } from '../../../shared/services/toast.service';

type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html'
})
export class AdminUsersComponent {
  private readonly usersApi = inject(AdminUsersService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';

  query = '';
  status: StatusFilter = 'ALL';

  pageSize = 12;
  page = 1;
  total = 0;
  users: AdminUser[] = [];

  editing: AdminUser | null = null;
  editRole: string = 'USER';
  editActive = true;

  constructor() {
    this.refresh();
  }

  refresh(resetPage = false): void {
    if (resetPage) this.page = 1;
    this.loading = true;
    this.error = '';
    const offset = (this.page - 1) * this.pageSize;

    this.usersApi
      .list({ q: this.query, status: this.status, limit: this.pageSize, offset })
      .pipe(
        timeout(8_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (res) => {
          this.users = res?.items ?? [];
          this.total = Number(res?.total ?? 0);
        },
        error: (err) => {
          this.error = typeof err?.error === 'string' && err.error.trim() ? err.error : 'Failed to load users.';
        }
      });
  }

  displayName(u: AdminUser): string {
    const base = (u.email ?? '').split('@')[0] ?? '';
    if (!base.trim()) return 'User';
    return base
      .replace(/[._-]+/g, ' ')
      .split(' ')
      .filter(Boolean)
      .map((w) => w.slice(0, 1).toUpperCase() + w.slice(1))
      .join(' ');
  }

  initials(u: AdminUser): string {
    const name = this.displayName(u).trim();
    const parts = name.split(' ').filter(Boolean);
    const a = parts[0]?.[0] ?? 'U';
    const b = parts.length > 1 ? parts[1]?.[0] ?? '' : '';
    return (a + b).toUpperCase();
  }

  statusLabel(u: AdminUser): 'Active' | 'Inactive' {
    return u.is_active ? 'Active' : 'Inactive';
  }

  openEdit(u: AdminUser): void {
    this.editing = u;
    this.editRole = (u.role ?? 'USER').toString().toUpperCase();
    this.editActive = !!u.is_active;
  }

  closeEdit(): void {
    this.editing = null;
  }

  saveEdit(): void {
    if (!this.editing) return;
    const id = this.editing.id;
    const role = this.editRole.trim().toUpperCase();
    this.loading = true;
    this.usersApi
      .update(id, { role, is_active: this.editActive })
      .pipe(
        timeout(8_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.toast.success('User updated');
          this.closeEdit();
          this.refresh();
        },
        error: (err) => {
          this.toast.error(typeof err?.error === 'string' && err.error.trim() ? err.error : 'Update failed.');
        }
      });
  }

  async deleteUser(u: AdminUser): Promise<void> {
    const ok = await this.confirm.open({
      title: 'Delete user',
      message: `Delete ${u.email}? This cannot be undone.`,
      confirmText: 'Delete',
      intent: 'danger'
    });
    if (!ok) return;

    this.loading = true;
    this.usersApi
      .delete(u.id)
      .pipe(
        timeout(8_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.toast.success('User deleted');
          if (this.page > 1 && this.users.length === 1) this.page -= 1;
          this.refresh();
        },
        error: (err) => {
          this.toast.error(typeof err?.error === 'string' && err.error.trim() ? err.error : 'Delete failed.');
        }
      });
  }

  pages(): number[] {
    const pageCount = this.pageCount;
    const start = Math.max(1, this.page - 2);
    const end = Math.min(pageCount, start + 4);
    const result: number[] = [];
    for (let p = start; p <= end; p++) result.push(p);
    return result;
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  go(p: number): void {
    this.page = Math.min(Math.max(1, p), this.pageCount);
    this.refresh();
  }

  roleClass(role: string): 'safe' | 'caution' {
    return (role ?? '').toString().toUpperCase() === 'ADMIN' ? 'caution' : 'safe';
  }
}
