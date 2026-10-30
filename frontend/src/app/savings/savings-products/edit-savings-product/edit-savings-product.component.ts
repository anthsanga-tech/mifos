import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SavingsProductPayload, SavingsService } from '../../savings.service';

@Component({
  selector: 'mifosx-edit-savings-product',
  templateUrl: './edit-savings-product.component.html',
  styleUrls: ['./edit-savings-product.component.scss']
})
export class EditSavingsProductComponent implements OnInit {
  error = '';
  loading = true;
  saving = false;
  private productId = 0;
  productForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    nominalAnnualInterestRate: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    minRequiredBalance: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    withdrawalFee: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    closureFee: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] })
  });

  constructor(private readonly route: ActivatedRoute, private readonly savingsService: SavingsService, private readonly router: Router) {}

  ngOnInit(): void {
    this.productId = Number(this.route.snapshot.paramMap.get('id'));
    this.savingsService.getProduct(this.productId).subscribe(
      (product) => {
        this.productForm.patchValue({
          name: product.name,
          nominalAnnualInterestRate: product.nominalAnnualInterestRate,
          minRequiredBalance: product.minRequiredBalance ?? 0,
          withdrawalFee: product.withdrawalFee ?? 0,
          closureFee: product.closureFee ?? 0
        });
        this.loading = false;
      },
      (err: HttpErrorResponse) => { this.loading = false; this.error = this.messageFrom(err, 'Failed to load savings product.'); }
    );
  }

  onSubmit(): void {
    if (this.productForm.invalid || this.saving) { this.productForm.markAllAsTouched(); return; }
    const value = this.productForm.getRawValue();
    const payload: SavingsProductPayload = {
      name: value.name.trim(),
      nominalAnnualInterestRate: Number(value.nominalAnnualInterestRate),
      minRequiredBalance: Number(value.minRequiredBalance),
      withdrawalFee: Number(value.withdrawalFee),
      closureFee: Number(value.closureFee)
    };
    this.saving = true;
    this.savingsService.updateProduct(this.productId, payload).subscribe(
      () => this.router.navigateByUrl('/savings-products'),
      (err: HttpErrorResponse) => { this.saving = false; this.error = this.messageFrom(err, 'Failed to update savings product.'); }
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

  private messageFrom(err: HttpErrorResponse, fallback: string): string { return (err && err.error && err.error.message) || err.message || fallback; }
}