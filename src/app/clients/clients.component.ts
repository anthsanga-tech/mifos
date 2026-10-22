/** Angular Imports */
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

/** Custom Services */
import { ClientsService, TrainingClient } from './clients.services';

/**
 * Clients list — table view (create/edit are separate components).
 */
@Component({
  selector: 'mifosx-clients',
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.css']
})
export class ClientsComponent implements OnInit {
  clients: TrainingClient[] = [];
  error = '';
  busy = true;
  saving = false;

  constructor(
    private readonly clientsService: ClientsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.busy = true;
    this.error = '';
    this.clientsService.getAll().subscribe(
      (data) => {
        this.clients = Array.isArray(data) ? data : [];
        this.busy = false;
      },
      (err: HttpErrorResponse) => {
        this.busy = false;
        this.clients = [];
        this.error = this.messageFrom(err, 'Failed to load clients.');
      }
    );
  }

  goCreate(): void {
    this.router.navigateByUrl('/clients/create');
  }

  goEdit(client: TrainingClient): void {
    this.router.navigateByUrl(`/clients/${client.id}/edit`);
  }

  onClose(client: TrainingClient): void {
    this.runCommand(client.id, 'close');
  }

  onActivate(client: TrainingClient): void {
    this.runCommand(client.id, 'activate');
  }

  private runCommand(id: number, command: 'close' | 'activate'): void {
    this.error = '';
    this.saving = true;
    this.clientsService.command(id, command).subscribe(
      () => {
        this.saving = false;
        this.reload();
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, `Failed to ${command} client.`);
      }
    );
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}
