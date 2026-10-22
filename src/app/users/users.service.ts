/** Angular Imports */
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Matches backend UserData from /v1/users */
export interface TrainingUser {
  id: number;
  username: string;
  displayName: string;
  email?: string | null;
  status: string;
  createdOn: string;
}

export interface CreateUserPayload {
  username: string;
  displayName: string;
  email?: string | null;
}

export interface UpdateUserPayload {
  displayName: string;
  email?: string | null;
}

/**
 * Users service — CRUD against mini-fineract /v1/users (soft delete = disable).
 */
@Injectable({
  providedIn: 'root'
})
export class UsersService {
  private readonly apiBase = '/mini-fineract-provider/api/v1/users';

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<TrainingUser[]> {
    return this.http.get<TrainingUser[]>(this.apiBase);
  }

  getOne(id: number): Observable<TrainingUser> {
    return this.http.get<TrainingUser>(`${this.apiBase}/${id}`);
  }

  create(payload: CreateUserPayload): Observable<TrainingUser> {
    return this.http.post<TrainingUser>(this.apiBase, payload);
  }

  update(id: number, payload: UpdateUserPayload): Observable<TrainingUser> {
    return this.http.put<TrainingUser>(`${this.apiBase}/${id}`, payload);
  }

  command(id: number, command: 'disable' | 'activate'): Observable<TrainingUser> {
    return this.http.post<TrainingUser>(
      `${this.apiBase}/${id}?command=${command}`,
      {}
    );
  }
}
