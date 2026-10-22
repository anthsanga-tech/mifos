/** Angular Imports */
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';

/** Custom Services */
import { ClientsService, TrainingClient, UpdateClientPayload } from '../clients.services';
import {
  SavingsAccount,
  SavingsAccountStatus,
  SavingsHold,
  SavingsProduct,
  SavingsService
} from '../../savings/savings.service';

/** Optional email: empty OK, otherwise must be a valid email. */
function optionalEmail(control: AbstractControl): ValidationErrors | null {
  const value = (control.value || '').toString().trim();
  if (!value) {
    return null;
  }
  return Validators.email(control);
}

/** Phone: digits only, 7–15 length (required). */
const PHONE_PATTERN = /^[0-9]{7,15}$/;

/**
 * Edit client — dedicated screen with field validation messages.
 */
@Component({
  selector: 'mifosx-edit-client',
  templateUrl: './edit-client.component.html',
  styleUrls: ['./edit-client.component.css']
})
export class EditClientComponent implements OnInit {
  client: TrainingClient | null = null;
  error = '';
  busy = true;
  saving = false;
  portfolioAccounts: SavingsAccount[] = [];
  portfolioProducts: SavingsProduct[] = [];
  portfolioError = '';
  portfolioActionError = '';
  portfolioBusy = false;
  portfolioCommandBusy = false;
  portfolioStatusFilter: SavingsAccountStatus | '' = '';
  readonly portfolioStatuses: SavingsAccountStatus[] = ['SUBMITTED', 'APPROVED', 'ACTIVE', 'FROZEN', 'CLOSED'];
  private availableBalances: { [accountId: number]: number } = {};

  clientForm = new FormGroup({
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2)]
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2)]
    }),
    email: new FormControl('', { nonNullable: true, validators: [optionalEmail] }),
    phoneNumber: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(PHONE_PATTERN)]
    }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    state: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    zipCode: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Za-z0-9\-\s]{3,12}$/)]
    }),
    country: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    dateOfBirth: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    gender: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor(
    private readonly clientsService: ClientsService,
    private readonly savingsService: SavingsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.busy = false;
      this.error = 'Invalid client id.';
      return;
    }
    this.clientsService.getOne(id).subscribe(
      (client) => {
        this.client = client;
        this.loadSavingsPortfolio(client.id);
        this.clientForm.patchValue({
          firstName: client.firstName || '',
          lastName: client.lastName || '',
          email: client.email || '',
          phoneNumber: client.phoneNumber || '',
          address: client.address || '',
          city: client.city || '',
          state: client.state || '',
          zipCode: client.zipCode || '',
          country: client.country || '',
          dateOfBirth: client.dateOfBirth || '',
          gender: client.gender || ''
        });
        this.busy = false;
      },
      (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = (err && err.error && err.error.message) || err.message || 'Failed to load client.';
      }
    );
  }

  onSubmit(): void {
    if (!this.client) {
      return;
    }
    if (this.clientForm.invalid || this.saving) {
      this.clientForm.markAllAsTouched();
      this.error = 'Please fix the highlighted fields.';
      return;
    }
    this.error = '';
    this.saving = true;
    this.clientsService.update(this.client.id, this.toPayload()).subscribe(
      () => {
        this.saving = false;
        this.router.navigateByUrl('/clients');
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = (err && err.error && err.error.message) || err.message || 'Failed to update client.';
      }
    );
  }

  cancel(): void {
    this.router.navigateByUrl('/clients');
  }

  viewSavingsAccounts(): void {
    if (this.client) {
      this.router.navigate(['/savings-accounts'], { queryParams: { clientId: this.client.id } });
    }
  }

  get filteredPortfolioAccounts(): SavingsAccount[] {
    return this.portfolioStatusFilter
      ? this.portfolioAccounts.filter((account) => this.portfolioAccountStatus(account) === this.portfolioStatusFilter)
      : this.portfolioAccounts;
  }

  portfolioStatusCount(status: SavingsAccountStatus): number {
    return this.portfolioAccounts.filter((account) => this.portfolioAccountStatus(account) === status).length;
  }

  portfolioAccountStatus(account: SavingsAccount): SavingsAccountStatus {
    return account.status === 'ACTIVE' && account.freezeStatus === 'FROZEN'
      ? 'FROZEN'
      : account.status;
  }

  get totalPortfolioBalance(): number {
    return this.portfolioAccounts
      .filter((account) => this.portfolioAccountStatus(account) === 'ACTIVE')
      .reduce((total, account) => total + (Number(account.balance) || 0), 0);
  }

  get totalPortfolioAvailable(): number {
    return this.portfolioAccounts
      .filter((account) => this.portfolioAccountStatus(account) === 'ACTIVE')
      .reduce((total, account) => total + (this.availableBalances[account.id] || 0), 0);
  }

  accountAvailableBalance(account: SavingsAccount): number {
    return this.portfolioAccountStatus(account) === 'ACTIVE'
      ? this.availableBalances[account.id] || 0
      : 0;
  }

  portfolioProductName(productId: number): string {
    const product = this.portfolioProducts.find((item) => item.id === productId);
    return product ? product.name : `Product #${productId}`;
  }

  openSavingsAccount(account: SavingsAccount): void {
    if (account.status === 'ACTIVE' || account.status === 'CLOSED') {
      this.router.navigateByUrl(`/savings-accounts/${account.id}`);
    }
  }

  approveSavingsAccount(account: SavingsAccount): void {
    this.runSavingsAccountCommand(account, 'approve');
  }

  activateSavingsAccount(account: SavingsAccount): void {
    this.runSavingsAccountCommand(account, 'activate');
  }

  createSavingsAccount(): void {
    if (this.client) {
      this.router.navigate(['/savings-accounts/create'], { queryParams: { clientId: this.client.id } });
    }
  }

  private runSavingsAccountCommand(account: SavingsAccount, command: 'approve' | 'activate'): void {
    if (this.portfolioCommandBusy) {
      return;
    }
    this.portfolioCommandBusy = true;
    this.portfolioActionError = '';
    this.savingsService.accountCommand(account.id, command).subscribe(
      (updatedAccount) => {
        account.status = updatedAccount.status;
        this.portfolioCommandBusy = false;
      },
      (err: HttpErrorResponse) => {
        this.portfolioCommandBusy = false;
        this.portfolioActionError =
          (err && err.error && err.error.message) || err.message || `Failed to ${command} savings account.`;
      }
    );
  }

  fieldError(name: string): string | null {
    const control = this.clientForm.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      return 'This field is required.';
    }
    if (control.errors['minlength']) {
      const min = control.errors['minlength'].requiredLength;
      return `Must be at least ${min} characters.`;
    }
    if (control.errors['email']) {
      return 'Enter a valid email address (e.g. name@example.com).';
    }
    if (control.errors['pattern'] && name === 'phoneNumber') {
      return 'Phone must be numbers only (7–15 digits).';
    }
    if (control.errors['pattern'] && name === 'zipCode') {
      return 'Enter a valid zip/postal code (3–12 letters or numbers).';
    }
    return 'Invalid value.';
  }

  private toPayload(): UpdateClientPayload {
    const value = this.clientForm.getRawValue();
    return {
      firstName: value.firstName?.trim() || '',
      lastName: value.lastName?.trim() || '',
      email: value.email?.trim() || null,
      phoneNumber: value.phoneNumber?.trim() || null,
      address: value.address?.trim() || null,
      city: value.city?.trim() || null,
      state: value.state?.trim() || null,
      zipCode: value.zipCode?.trim() || null,
      country: value.country?.trim() || null,
      dateOfBirth: value.dateOfBirth?.trim() || null,
      gender: value.gender?.trim() || null
    };
  }

  private loadSavingsPortfolio(clientId: number): void {
    this.portfolioBusy = true;
    this.portfolioError = '';
    forkJoin([
      this.savingsService.getAccounts(),
      this.savingsService.getProducts()
    ]).subscribe(
      ([accounts, products]) => {
        this.portfolioAccounts = (Array.isArray(accounts) ? accounts : [])
          .filter((account) => account.clientId === clientId);
        this.portfolioProducts = Array.isArray(products) ? products : [];
        if (this.portfolioAccounts.length === 0) {
          this.availableBalances = {};
          this.portfolioBusy = false;
          return;
        }

        forkJoin(this.portfolioAccounts.map((account) => this.savingsService.getHolds(account.id))).subscribe(
          (holdsByAccount: SavingsHold[][]) => {
            this.availableBalances = {};
            this.portfolioAccounts.forEach((account, index) => {
              const product = this.portfolioProducts.find((item) => item.id === account.productId);
              const activeHolds = (holdsByAccount[index] || [])
                .filter((hold) => hold.status === 'ACTIVE')
                .reduce((total, hold) => total + (Number(hold.amount) || 0), 0);
              const minimumBalance = Number(product?.minRequiredBalance) || 0;
              this.availableBalances[account.id] = Math.max(
                0,
                (Number(account.balance) || 0) - activeHolds - minimumBalance
              );
            });
            this.portfolioBusy = false;
          },
          (err: HttpErrorResponse) => {
            this.portfolioBusy = false;
            this.portfolioError = (err && err.error && err.error.message) || err.message || 'Failed to load account holds.';
          }
        );
      },
      (err: HttpErrorResponse) => {
        this.portfolioBusy = false;
        this.portfolioError = (err && err.error && err.error.message) || err.message || 'Failed to load savings portfolio.';
      }
    );
  }
}
