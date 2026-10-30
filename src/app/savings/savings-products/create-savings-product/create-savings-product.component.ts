import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { SavingsProductPayload, SavingsService } from '../../savings.service';

@Component({
  selector: 'mifosx-create-savings-product',
  templateUrl: './create-savings-product.component.html',
  styleUrls: ['./create-savings-product.component.scss']
})
export class CreateSavingsProductComponent {
  error = '';
  saving = false;
  productForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    nominalAnnualInterestRate: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    minRequiredBalance: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    withdrawalFee: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    closureFee: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] })
  });

  constructor(private readonly savingsService: SavingsService, private readonly router: Router) {}

  onSubmit(): void {
    if (this.productForm.invalid || this.saving) {
      this.productForm.markAllAsTouched();
      return;
    }
    const value = this.productForm.getRawValue();
    const payload: SavingsProductPayload = {
      name: value.name.trim(),
      nominalAnnualInterestRate: Number(value.nominalAnnualInterestRate),
      minRequiredBalance: Number(value.minRequiredBalance),
      withdrawalFee: Number(value.withdrawalFee),
      closureFee: Number(value.closureFee)
    };
    this.saving = true;
    this.savingsService.createProduct(payload).subscribe(
      () => this.router.navigateByUrl('/savings-products'),
      (err: HttpErrorResponse) => { this.saving = false; this.error = this.messageFrom(err, 'Failed to create savings product.'); }
    );
  }

  cancel(): void { this.router.navigateByUrl('/savings-products'); }

  fieldError(name: 'name' | 'nominalAnnualInterestRate' | 'minRequiredBalance' | 'withdrawalFee' | 'closureFee'): string | null {
    const control = this.productForm.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) { return null; }
    if (control.errors['required']) { return 'This field is required.'; }
    if (control.errors['minlength']) { return 'Name must be at least 2 characters.'; }
    if (control.errors['min']) {
      if (name === 'nominalAnnualInterestRate') { return 'Interest rate cannot be negative.'; }
      if (name === 'minRequiredBalance') { return 'Minimum required balance cannot be negative.'; }
      if (name === 'withdrawalFee') { return 'Withdrawal fee cannot be negative.'; }
      return 'Closure fee cannot be negative.';
    }
    return 'Invalid value.';
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}