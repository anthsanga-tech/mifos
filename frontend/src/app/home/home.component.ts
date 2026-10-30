import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';
import { AuthSessionService } from '../core/auth-session.service';
import {
  SavingsAccount,
  SavingsAccountAuditEntry,
  SavingsClient,
  SavingsHold,
  MakerCheckerRequest,
  SavingsProduct,
  SavingsService
} from '../savings/savings.service';
import { activities } from './activities';

interface RecentActivity {
  when: string;
  event: string;
  accountNo: string;
  clientName: string;
  amount: number | null;
  by: string;
}

@Component({
  selector: 'mifosx-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  readonly activitySearch = new FormControl('');
  readonly filteredActivities: Observable<{ activity: string; path: string }[]>;
  private readonly allActivities = activities;
  username = 'User';
  accounts: SavingsAccount[] = [];
  clients: SavingsClient[] = [];
  products: SavingsProduct[] = [];
  recentActivities: RecentActivity[] = [];
  pendingMakerCheckerCount = 0;
  makerCheckerLoading = false;
  makerCheckerError = '';
  private activeHoldBalances: { [accountId: number]: number } = {};
  busy = true;
  error = '';

  constructor(
    private readonly savingsService: SavingsService,
    private readonly authSession: AuthSessionService,
    private readonly router: Router
  ) {
    this.filteredActivities = this.activitySearch.valueChanges.pipe(
      startWith(''),
      map((value) => (typeof value === 'string' ? value.trim().toLowerCase() : '')),
      map((search) => search
        ? this.allActivities.filter((activity) =>
          activity.activity.toLowerCase().startsWith(search)
        )
        : this.allActivities.slice())
    );
  }

  ngOnInit(): void {
    this.username = this.authSession.getUsername();
    this.refresh();
  }

  get activeCount(): number {
    return this.accounts.filter((account) =>
      account.status === 'ACTIVE' && account.freezeStatus !== 'FROZEN'
    ).length;
  }

  get frozenCount(): number {
    return this.accounts.filter((account) =>
      account.status === 'ACTIVE' && account.freezeStatus === 'FROZEN'
    ).length;
  }

  get submittedCount(): number {
    return this.accounts.filter((account) => account.status === 'SUBMITTED').length;
  }

  get approvedCount(): number {
    return this.accounts.filter((account) => account.status === 'APPROVED').length;
  }

  get closedCount(): number {
    return this.accounts.filter((account) => account.status === 'CLOSED').length;
  }

  get ledgerTotal(): number {
    return this.accounts
      .filter((account) => account.status !== 'CLOSED')
      .reduce((total, account) => total + this.accountBalance(account), 0);
  }

  get availableTotal(): number {
    return this.accounts
      .filter((account) => account.status !== 'CLOSED')
      .reduce((total, account) => {
        const product = this.products.find((item) => item.id === account.productId);
        const minimumBalance = Number(product && product.minRequiredBalance) || 0;
        const holds = this.activeHoldBalances[account.id] || 0;
        return total + Math.max(0, this.accountBalance(account) - holds - minimumBalance);
      }, 0);
  }

  get activeHoldsTotal(): number {
    return this.accounts
      .filter((account) => account.status !== 'CLOSED')
      .reduce((total, account) => total + (this.activeHoldBalances[account.id] || 0), 0);
  }

  refresh(): void {
    this.busy = true;
    this.error = '';
    this.loadPendingMakerCheckerCount();

    forkJoin({
      accounts: this.savingsService.getAccounts(),
      clients: this.savingsService.getClients(),
      products: this.savingsService.getProducts()
    }).subscribe({
      next: ({ accounts, clients, products }) => {
        this.accounts = accounts;
        this.clients = clients;
        this.products = products;
        this.loadAccountDetails(accounts);
      },
      error: (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = this.messageFrom(err, 'Failed to load savings dashboard.');
      }
    });
  }

  logout(): void {
    this.authSession.clear();
    this.router.navigateByUrl('/login');
  }

  goToActivity(path: string): void {
    this.activitySearch.setValue('');
    this.router.navigateByUrl(path);
  }

  openMakerCheckerInbox(): void {
    this.router.navigateByUrl('/maker-checker');
  }

  showAccounts(status: 'ALL' | 'ACTIVE' | 'FROZEN' | 'SUBMITTED' | 'APPROVED' | 'CLOSED'): void {
    this.router.navigate(['/savings-accounts'], { queryParams: { status } });
  }

  private loadAccountDetails(accounts: SavingsAccount[]): void {
    if (accounts.length === 0) {
      this.activeHoldBalances = {};
      this.recentActivities = [];
      this.busy = false;
      return;
    }

    forkJoin(accounts.map((account) => forkJoin({
      holds: this.savingsService.getHolds(account.id),
      auditEntries: this.savingsService.getAccountAuditTrail(account.id)
    }))).subscribe({
      next: (accountDetails) => {
        this.activeHoldBalances = {};
        const activities: RecentActivity[] = [];

        accounts.forEach((account, index) => {
          this.activeHoldBalances[account.id] = accountDetails[index].holds
            .filter((hold: SavingsHold) => hold.status === 'ACTIVE')
            .reduce((total: number, hold: SavingsHold) => total + (Number(hold.amount) || 0), 0);

          accountDetails[index].auditEntries.forEach((entry: SavingsAccountAuditEntry) => {
            activities.push({
              when: entry.when,
              event: entry.event,
              accountNo: account.accountNo,
              clientName: this.clientName(account.clientId),
              amount: entry.amount === null ? null : Number(entry.amount),
              by: entry.by
            });
          });
        });

        this.recentActivities = activities.sort((left, right) =>
          Date.parse(right.when) - Date.parse(left.when)
        );
        this.busy = false;
      },
      error: (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = this.messageFrom(err, 'Failed to load savings account activity and holds.');
      }
    });
  }

  private loadPendingMakerCheckerCount(): void {
    this.makerCheckerLoading = true;
    this.makerCheckerError = '';
    this.savingsService.getMakerCheckerRequests('PENDING').subscribe({
      next: (requests: MakerCheckerRequest[]) => {
        this.pendingMakerCheckerCount = requests.length;
        this.makerCheckerLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.makerCheckerLoading = false;
        this.makerCheckerError = this.messageFrom(err, 'Failed to load pending approvals.');
      }
    });
  }

  private accountBalance(account: SavingsAccount): number {
    return Number(account.balance) || 0;
  }

  private clientName(clientId: number): string {
    const client = this.clients.find((item) => item.id === clientId);
    return client ? `${client.firstName} ${client.lastName}` : `Client #${clientId}`;
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}
