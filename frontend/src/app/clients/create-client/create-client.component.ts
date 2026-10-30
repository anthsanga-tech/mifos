/** Angular Imports */
import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';

/** Custom Services */
import { ClientsService, CreateClientPayload } from '../clients.services';

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
 * Create client — dedicated screen with field validation messages.
 */
@Component({
  selector: 'mifosx-create-client',
  templateUrl: './create-client.component.html',
  styleUrls: ['./create-client.component.css']
})
export class CreateClientComponent {
  error = '';
  saving = false;

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
    private readonly router: Router
  ) {}

  onSubmit(): void {
    if (this.clientForm.invalid || this.saving) {
      this.clientForm.markAllAsTouched();
      this.error = 'Please fix the highlighted fields.';
      return;
    }
    this.error = '';
    this.saving = true;
    this.clientsService.create(this.toPayload()).subscribe(
      () => {
        this.saving = false;
        this.router.navigateByUrl('/clients');
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = (err && err.error && err.error.message) || err.message || 'Failed to create client.';
      }
    );
  }

  cancel(): void {
    this.router.navigateByUrl('/clients');
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

  private toPayload(): CreateClientPayload {
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
}
