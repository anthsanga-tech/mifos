import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';
import {
  MakerCheckerRequest,
  MakerCheckerStatus,
  SavingsAccount,
  SavingsService
} from '../savings.service';

type MakerCheckerFilter = 'ALL' | MakerCheckerStatus;

@Component({
  selector: 'mifosx-maker-checker-inbox',
  templateUrl: './maker-checker-inbox.component.html',
  styleUrls: ['./maker-checker-inbox.component.scss']
})
export class MakerCheckerInboxComponent implements OnInit {
  readonly pageSize = 10;
  readonly username: string;
  requests: MakerCheckerRequest[] = [];
  filter: MakerCheckerFilter = 'PENDING';
  page = 0;
  loading = false;
  actionRequestId: number | null = null;
  error = '';
  actionError = '';

  constructor(
    private readonly savingsService: SavingsService,
    authSession: AuthSessionService,
    private readonly router: Router
  ) {
    this.username = authSession.getUsername();
  }

  ngOnInit(): void {
    this.refresh();
  }

  get filteredRequests(): MakerCheckerRequest[] {
    return this.filter === 'ALL'
      ? this.requests
      : this.requests.filter((request) => request.status === this.filter);
  }

  get visibleRequests(): MakerCheckerRequest[] {
    const start = this.page * this.pageSize;
    return this.filteredRequests.slice(start, start + this.pageSize);
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.filteredRequests.length / this.pageSize));
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.actionError = '';
    this.savingsService.getMakerCheckerRequests().subscribe({
      next: (requests) => {
        this.requests = requests;
        this.page = Math.min(this.page, this.pageCount - 1);
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.messageFrom(err, 'Failed to load maker-checker requests.');
      }
    });
  }

  onFilterChange(): void {
    this.page = 0;
  }

  isOwnRequest(request: MakerCheckerRequest): boolean {
    return request.madeBy.trim().toLowerCase() === this.username.trim().toLowerCase();
  }

  openAccount(request: MakerCheckerRequest, event: MouseEvent): void {
    event.preventDefault();
    this.error = '';
    this.savingsService.getAccounts().subscribe({
      next: (accounts: SavingsAccount[]) => {
        const account = accounts.find((item) => item.accountNo === request.savingsAccountNo);
        if (!account) {
          this.error = `Savings account ${request.savingsAccountNo} was not found.`;
          return;
        }
        this.router.navigate(['/savings-accounts', account.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.messageFrom(err, 'Failed to open savings account.');
      }
    });
  }

  approve(request: MakerCheckerRequest): void {
    if (request.status !== 'PENDING' || this.isOwnRequest(request) || this.actionRequestId !== null) {
      return;
    }
    this.actionRequestId = request.id;
    this.actionError = '';
    this.savingsService.approveMakerCheckerRequest(request.id).subscribe({
      next: () => {
        this.actionRequestId = null;
        this.refresh();
      },
      error: (err: HttpErrorResponse) => {
        this.actionRequestId = null;
        this.actionError = this.messageFrom(err, 'Failed to approve the request.');
      }
    });
  }

  reject(request: MakerCheckerRequest): void {
    if (request.status !== 'PENDING' || this.isOwnRequest(request) || this.actionRequestId !== null) {
      return;
    }
    const enteredReason = window.prompt('Enter a reason for rejecting this request:');
    if (enteredReason === null) {
      return;
    }
    const reason = enteredReason.trim();
    if (!reason) {
      this.actionError = 'Enter a reason before rejecting the request.';
      return;
    }

    this.actionRequestId = request.id;
    this.actionError = '';
    this.savingsService.rejectMakerCheckerRequest(request.id, reason).subscribe({
      next: () => {
        this.actionRequestId = null;
        this.refresh();
      },
      error: (err: HttpErrorResponse) => {
        this.actionRequestId = null;
        this.actionError = this.messageFrom(err, 'Failed to reject the request.');
      }
    });
  }

  previousPage(): void {
    this.page = Math.max(0, this.page - 1);
  }

  nextPage(): void {
    this.page = Math.min(this.pageCount - 1, this.page + 1);
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}
