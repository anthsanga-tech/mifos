/** Angular Imports */
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Matches backend ClientData from /v1/clients */
export interface TrainingClient {
  id: number;
  firstName: string;
  lastName: string;
  email?: string | null;
  phoneNumber?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  zipCode?: string | null;
  country?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
  status: string;
  createdOn: string;
}

export interface CreateClientPayload {
  firstName: string;
  lastName: string;
  email?: string | null;
  phoneNumber?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  zipCode?: string | null;
  country?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
}

export interface UpdateClientPayload {
  firstName: string;
  lastName: string;
  email?: string | null;
  phoneNumber?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  zipCode?: string | null;
  country?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
}

/**
 * Clients service — CRUD against mini-fineract /v1/clients (soft delete = close).
 */
@Injectable({
  providedIn: 'root'
})
export class ClientsService {
  private readonly apiBase = '/mini-fineract-provider/api/v1/clients';

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<TrainingClient[]> {
    return this.http.get<TrainingClient[]>(this.apiBase);
  }

  getOne(id: number): Observable<TrainingClient> {
    return this.http.get<TrainingClient>(`${this.apiBase}/${id}`);
  }

  create(payload: CreateClientPayload): Observable<TrainingClient> {
    return this.http.post<TrainingClient>(this.apiBase, payload);
  }

  update(id: number, payload: UpdateClientPayload): Observable<TrainingClient> {
    return this.http.put<TrainingClient>(`${this.apiBase}/${id}`, payload);
  }

  command(id: number, command: 'close' | 'activate'): Observable<TrainingClient> {
    return this.http.post<TrainingClient>(
      `${this.apiBase}/${id}?command=${command}`,
      {}
    );
  }
}
