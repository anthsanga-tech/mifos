import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  SavingsAccount,
  SavingsAccountStatus,
  SavingsClient,
  SavingsHold,
  SavingsProduct,
  SavingsService
} from '../savings.service';

@Component({
  selector: 'mifosx-savings',
  templateUrl: './savings-accounts.component.html',
  styleUrls: ['./savings-accounts.component.scss']
})
export class SavingsComponent implements OnInit {
  products: SavingsProduct[] = [];
  accounts: SavingsAccount[] = [];
  clients: SavingsClient[] = [];
  private activeHoldBalances: { [accountId: number]: number } = {};
  activeHoldsLoaded = false;
  productsLoaded = false;
  error = '';
  busy = true;
  nameFilter = '';
  clientIdFilter = '';
  statusFilter: SavingsAccountStatus | '' = '';
  pageSize = 10;
  pageIndex = 0;
  readonly pageSizes = [10, 25, 50];
  private appliedName = '';
  private appliedClientId = '';
  private appliedStatus: SavingsAccountStatus | '' = '';

  constructor(
    private readonly savingsService: SavingsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const requestedClientId = Number(this.route.snapshot.queryParamMap.get('clientId'));
    if (Number.isInteger(requestedClientId) && requestedClientId > 0) {
      this.clientIdFilter = requestedClientId.toString();
      this.appliedClientId = this.clientIdFilter;
    }
    const requestedStatus = this.route.snapshot.queryParamMap.get('status');
    if (
      requestedStatus === 'ALL' ||
      requestedStatus === 'SUBMITTED' ||
      requestedStatus === 'APPROVED' ||
      requestedStatus === 'ACTIVE' ||
      requestedStatus === 'FROZEN' ||
      requestedStatus === 'CLOSED'
    ) {
      this.statusFilter = requestedStatus === 'ALL' ? '' : requestedStatus;
      this.appliedStatus = this.statusFilter;
    }
    this.reload();
  }

  reload(): void {
    this.busy = true;
    this.activeHoldsLoaded = false;
    this.productsLoaded = false;
    this.error = '';
    this.savingsService.getProducts().subscribe(
      (products) => {
        this.products = Array.isArray(products) ? products : [];
        this.productsLoaded = true;
      },
      (err: HttpErrorResponse) => (this.error = this.messageFrom(err, 'Failed to load savings products.'))
    );
    this.savingsService.getClients().subscribe(
      (clients) => (this.clients = Array.isArray(clients) ? clients : []),
      (err: HttpErrorResponse) => (this.error = this.messageFrom(err, 'Failed to load clients.'))
    );
    this.savingsService.getAccounts().subscribe(
      (accounts) => {
        this.accounts = Array.isArray(accounts) ? accounts : [];
        this.activeHoldBalances = {};
        this.busy = false;
        this.loadAccountHolds();
      },
      (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = this.messageFrom(err, 'Failed to load savings accounts.');
      }
    );
  }

  goCreateAccount(): void {
    this.router.navigateByUrl('/savings-accounts/create');
  }

  get filteredAccounts(): SavingsAccount[] {
    return this.accounts.filter((account) => {
      const search = this.appliedName.toLocaleLowerCase();
      const matchesName = !search ||
        account.accountNo.toLocaleLowerCase().includes(search) ||
        this.clientName(account.clientId).toLocaleLowerCase().includes(search);
      const matchesClient = !this.appliedClientId || account.clientId === Number(this.appliedClientId);
      const matchesStatus = this.matchesStatus(account);
      return matchesName && matchesClient && matchesStatus;
    });
  }

  get paginatedAccounts(): SavingsAccount[] {
    const start = this.pageIndex * this.pageSize;
    return this.filteredAccounts.slice(start, start + this.pageSize);
  }

  get pageCount(): number {
    return Math.ceil(this.filteredAccounts.length / this.pageSize);
  }

  get pageRangeLabel(): string {
    const total = this.filteredAccounts.length;
    if (total === 0) {
      return '0–0 of 0';
    }
    const start = this.pageIndex * this.pageSize + 1;
    const end = Math.min(start + this.pageSize - 1, total);
    return `${start}–${end} of ${total}`;
  }

  applyFilters(): void {
    this.appliedName = this.nameFilter.trim();
    this.appliedClientId = this.clientIdFilter.trim();
    this.appliedStatus = this.statusFilter;
    this.pageIndex = 0;
  }

  clearFilters(): void {
    this.nameFilter = '';
    this.clientIdFilter = '';
    this.statusFilter = '';
    this.appliedName = '';
    this.appliedClientId = '';
    this.appliedStatus = '';
    this.pageIndex = 0;
  }

  changePageSize(): void {
    this.pageIndex = 0;
  }

  goToPage(pageIndex: number): void {
    if (pageIndex >= 0 && pageIndex < this.pageCount) {
      this.pageIndex = pageIndex;
    }
  }

  get isClientFiltered(): boolean {
    return Boolean(this.appliedClientId);
  }

  openAccount(account: SavingsAccount): void {
    if (account.status !== 'ACTIVE' && account.status !== 'CLOSED') {
      return;
    }
    this.router.navigateByUrl(`/savings-accounts/${account.id}`);
  }

  approve(account: SavingsAccount) {
    this.savingsService.accountCommand(account.id, 'approve').subscribe({
      next: (updatedAccount) => {
        account.status = updatedAccount.status;
      },
      error: (err) => {
        console.error(err);
      }
    });
  }

  activate(account: SavingsAccount) {
    this.savingsService.accountCommand(account.id, 'activate').subscribe({
      next: (updatedAccount) => {
        account.status = updatedAccount.status;
      },
      error: (err) => {
        console.error(err);
      }
    });
  }

  clientName(clientId: number): string {
    const client = this.clients.find((item) => item.id === clientId);
    return client ? `${client.firstName} ${client.lastName}` : `Client #${clientId}`;
  }

  productName(productId: number): string {
    const product = this.products.find((item) => item.id === productId);
    return product ? product.name : `Product #${productId}`;
  }

  accountBalance(account: SavingsAccount): number {
    return Number(account.balance) || 0;
  }

  accountAvailableBalance(account: SavingsAccount): number | null {
    if (!this.activeHoldsLoaded || !this.productsLoaded) {
      return null;
    }
    const minimumBalance = Number(this.products.find((item) => item.id === account.productId)?.minRequiredBalance) || 0;
    return Math.max(0, this.accountBalance(account) - (this.activeHoldBalances[account.id] || 0) - minimumBalance);
  }

  private loadAccountHolds(): void {
    if (this.accounts.length === 0) {
      this.activeHoldsLoaded = true;
      return;
    }
    forkJoin(this.accounts.map((account) => this.savingsService.getHolds(account.id))).subscribe(
      (holdsByAccount: SavingsHold[][]) => {
        this.activeHoldBalances = {};
        this.accounts.forEach((account, index) => {
          this.activeHoldBalances[account.id] = (holdsByAccount[index] || [])
            .filter((hold) => hold.status === 'ACTIVE')
            .reduce((total, hold) => total + (Number(hold.amount) || 0), 0);
        });
        this.activeHoldsLoaded = true;
      },
      (err: HttpErrorResponse) => {
        this.error = this.messageFrom(err, 'Failed to load account holds.');
      }
    );
  }

  private matchesStatus(account: SavingsAccount): boolean {
    if (!this.appliedStatus) {
      return true;
    }
    if (this.appliedStatus === 'FROZEN') {
      return account.status === 'ACTIVE' && account.freezeStatus === 'FROZEN';
    }
    if (this.appliedStatus === 'ACTIVE') {
      return account.status === 'ACTIVE' && account.freezeStatus !== 'FROZEN';
    }
    return account.status === this.appliedStatus;
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}