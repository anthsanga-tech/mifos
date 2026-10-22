import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SavingsAccountPayload, SavingsClient, SavingsProduct, SavingsService } from '../../savings.service';

@Component({
  selector: 'mifosx-create-savings-account',
  templateUrl: './create-savings-account.component.html',
  styleUrls: ['./create-savings-account.component.scss']
})
export class CreateSavingsAccountComponent implements OnInit {
  clients: SavingsClient[] = [];
  products: SavingsProduct[] = [];
  error = '';
  loading = true;
  saving = false;
  private pendingLookups = 2;
  preselectedClientId: number | null = null;
  accountForm = new FormGroup({
    clientId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    productId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    accountNo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3)] })
  });

  constructor(
    private readonly savingsService: SavingsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const requestedClientId = Number(this.route.snapshot.queryParamMap.get('clientId'));
    if (Number.isInteger(requestedClientId) && requestedClientId > 0) {
      this.preselectedClientId = requestedClientId;
      this.accountForm.controls.clientId.setValue(requestedClientId);
    }

    this.savingsService.getClients().subscribe(
      (clients) => { this.clients = Array.isArray(clients) ? clients : []; this.finishLoading(); },
      (err: HttpErrorResponse) => { this.error = this.messageFrom(err, 'Failed to load clients.'); this.finishLoading(); }
    );
    this.savingsService.getProducts().subscribe(
      (products) => { this.products = Array.isArray(products) ? products : []; this.finishLoading(); },
      (err: HttpErrorResponse) => { this.error = this.messageFrom(err, 'Failed to load savings products.'); this.finishLoading(); }
    );
  }

  onSubmit(): void {
    if (this.accountForm.invalid || this.saving) { this.accountForm.markAllAsTouched(); return; }
    const value = this.accountForm.getRawValue();
    const payload: SavingsAccountPayload = {
      clientId: Number(value.clientId),
      productId: Number(value.productId),
      accountNo: value.accountNo.trim()
    };
    this.saving = true;
    this.savingsService.createAccount(payload).subscribe(
      () => this.returnToPortfolio(),
      (err: HttpErrorResponse) => { this.saving = false; this.error = this.messageFrom(err, 'Failed to open savings account.'); }
    );
  }

  cancel(): void { this.returnToPortfolio(); }

  selectedClientName(): string {
    if (this.preselectedClientId === null) {
      return '';
    }
    const client = this.clients.find((item) => item.id === this.preselectedClientId);
    return client
      ? `${client.firstName} ${client.lastName} (#${client.id})`
      : `Client #${this.preselectedClientId}`;
  }

  private returnToPortfolio(): void {
    this.router.navigateByUrl(
      this.preselectedClientId
        ? `/clients/${this.preselectedClientId}/edit`
        : '/savings-accounts'
    );
  }

  fieldError(name: 'clientId' | 'productId' | 'accountNo'): string | null {
    const control = this.accountForm.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) { return null; }
    if (control.errors['required']) { return 'This field is required.'; }
    if (control.errors['minlength']) { return 'Account number must be at least 3 characters.'; }
    return 'Invalid value.';
  }

  private finishLoading(): void {
    this.pendingLookups -= 1;
    if (this.pendingLookups === 0) { this.loading = false; }
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string { return (err && err.error && err.error.message) || err.message || fallback; }
}