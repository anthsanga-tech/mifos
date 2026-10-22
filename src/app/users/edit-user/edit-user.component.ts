/** Angular Imports */
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

/** Custom Services */
import { TrainingUser, UsersService } from '../users.service';

/**
 * Edit user — dedicated screen with field validation messages.
 */
@Component({
  selector: 'mifosx-edit-user',
  templateUrl: './edit-user.component.html',
  styleUrls: ['./edit-user.component.scss']
})
export class EditUserComponent implements OnInit {
  user: TrainingUser | null = null;
  error = '';
  busy = true;
  saving = false;

  userForm = new FormGroup({
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
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.busy = false;
      this.error = 'Invalid user id.';
      return;
    }
    this.usersService.getOne(id).subscribe(
      (user) => {
        this.user = user;
        this.userForm.patchValue({
          displayName: user.displayName,
          email: user.email || ''
        });
        this.busy = false;
      },
      (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = (err && err.error && err.error.message) || err.message || 'Failed to load user.';
      }
    );
  }

  onSubmit(): void {
    if (!this.user) {
      return;
    }
    if (this.userForm.invalid || this.saving) {
      this.userForm.markAllAsTouched();
      this.error = 'Please fix the highlighted fields.';
      return;
    }
    this.error = '';
    this.saving = true;
    const value = this.userForm.getRawValue();
    this.usersService
      .update(this.user.id, {
        displayName: value.displayName.trim(),
        email: value.email.trim() || null
      })
      .subscribe(
        () => {
          this.saving = false;
          this.router.navigateByUrl('/users');
        },
        (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = (err && err.error && err.error.message) || err.message || 'Failed to update user.';
        }
      );
  }

  cancel(): void {
    this.router.navigateByUrl('/users');
  }

  fieldError(name: 'displayName' | 'email'): string | null {
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
    return 'Invalid value.';
  }
}
