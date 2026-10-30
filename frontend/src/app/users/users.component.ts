/** Angular Imports */
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

/** Custom Services */
import { TrainingUser, UsersService } from './users.service';

/**
 * Users list — table view (create/edit are separate components).
 */
@Component({
  selector: 'mifosx-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})
export class UsersComponent implements OnInit {
  users: TrainingUser[] = [];
  error = '';
  busy = true;
  saving = false;

  constructor(
    private readonly usersService: UsersService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.busy = true;
    this.error = '';
    this.usersService.getAll().subscribe(
      (data) => {
        this.users = Array.isArray(data) ? data : [];
        this.busy = false;
      },
      (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = this.messageFrom(err, 'Failed to load users.');
      }
    );
  }

  goCreate(): void {
    this.router.navigateByUrl('/users/create');
  }

  goEdit(user: TrainingUser): void {
    this.router.navigateByUrl(`/users/${user.id}/edit`);
  }

  onDisable(user: TrainingUser): void {
    this.runCommand(user.id, 'disable');
  }

  onActivate(user: TrainingUser): void {
    this.runCommand(user.id, 'activate');
  }

  private runCommand(id: number, command: 'disable' | 'activate'): void {
    this.error = '';
    this.saving = true;
    this.usersService.command(id, command).subscribe(
      () => {
        this.saving = false;
        this.reload();
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, `Failed to ${command} user.`);
      }
    );
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}
