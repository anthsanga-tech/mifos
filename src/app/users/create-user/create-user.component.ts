/** Angular Imports */
import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

/** Custom Services */
import { CreateUserPayload, UsersService } from '../users.service';

/**
 * Create user — dedicated screen with field validation messages.
 */
@Component({
  selector: 'mifosx-create-user',
  templateUrl: './create-user.component.html',
  styleUrls: ['./create-user.component.scss']
})
export class CreateUserComponent {
  error = '';
  saving = false;

  userForm = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(3),
        Validators.pattern(/^[a-zA-Z0-9._-]+$/)
      ]
    }),
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2)]
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.email]
    })
  });

  constructor(
    private readonly usersService: UsersService,
    private readonly router: Router
  ) {}

  onSubmit(): void {
    if (this.userForm.invalid || this.saving) {
      this.userForm.markAllAsTouched();
      this.error = 'Please fix the highlighted fields.';
      return;
    }
    this.error = '';
    this.saving = true;
    const value = this.userForm.getRawValue();
    const payload: CreateUserPayload = {
      username: value.username.trim(),
      displayName: value.displayName.trim(),
      email: value.email.trim() || null
    };
    this.usersService.create(payload).subscribe(
      () => {
        this.saving = false;
        this.router.navigateByUrl('/users');
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = (err && err.error && err.error.message) || err.message || 'Failed to create user.';
      }
    );
  }

  cancel(): void {
    this.router.navigateByUrl('/users');
  }

  fieldError(name: 'username' | 'displayName' | 'email'): string | null {
    const control = this.userForm.get(name);
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
    if (control.errors['pattern'] && name === 'username') {
      return 'Username may only contain letters, numbers, dot, underscore, or hyphen.';
    }
    return 'Invalid value.';
  }
}
