import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SavingsProduct {
  id: number;
  name: string;
  nominalAnnualInterestRate: number;
  minRequiredBalance?: number;
  withdrawalFee?: number;
  createdOn: string;
}

export type SavingsAccountStatus =
  | 'SUBMITTED'
  | 'APPROVED'
  | 'ACTIVE'
  | 'FROZEN'
  | 'CLOSED';

export interface SavingsAccount {
  id: number;
  clientId: number;
  productId: number;
  accountNo: string;
  status: SavingsAccountStatus;
  balance: number;
  createdOn: string;
  activatedOn?: string;
  freezeStatus: 'FROZEN' | 'UNFROZEN';
  freezeReason?: string;
  frozenOn?: string;
}

export interface SavingsTransaction {
  id: number;
  savingsAccountId: number;
  transactionType: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_IN' | 'TRANSFER_OUT';
  amount: number;
  transactionDate: string;
  runningBalance: number;
  reversed: number | string;
  transactionGroupId?: string;
}

export interface SavingsClient {
  id: number;
  firstName: string;
  lastName: string;
}

export interface SavingsProductPayload {
  name: string;
  nominalAnnualInterestRate: number;
  minRequiredBalance: number;
  withdrawalFee: number;
}

export interface SavingsAccountPayload {
  clientId: number;
  productId: number;
  accountNo: string;
}

export interface SavingsTransactionPayload {
  savingsAccountId: number;
  transactionType: 'DEPOSIT' | 'WITHDRAWAL';
  amount: number;
}

export interface SavingsCheckMakerPayload {
  savingsAccountId: number;
  transactionType: 'DEPOSIT' | 'WITHDRAWAL';
  amount: number;
}

export interface SavingsTransferPayload {
  fromAccountNo: string;
  toAccountNo: string;
  amount: number;
}

export type StandingInstructionFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

export interface StandingInstruction {
  id: number;
  fromAccountId: number;
  toAccountId: number;
  fromAccountNo?: string;
  toAccountNo?: string;
  amount: number;
  frequency: StandingInstructionFrequency;
  nextDue: string;
  status: string;
  validFrom: string;
  validTo?: string | null;
}

export interface StandingInstructionRun {
  id: number;
  dueDate: string;
  businessDate: string;
  status: string;
  reason?: string | null;
  transferId?: number | null;
  makerCheckerRequestId?: number | null;
  ranOn: string;
}

export interface StandingInstructionPayload {
  toAccountId: number;
  amount: number;
  frequency: StandingInstructionFrequency;
  validFrom: string;
  validTo?: string | null;
  nextDue: string;
}

export interface SavingsHold {
  id: number;
  savingsAccountId: number;
  amount: number;
  reason?: string | null;
  status: 'ACTIVE' | 'RELEASED';
  createdOn: string;
  releasedOn?: string | null;
}

export interface SavingsHoldPayload {
  amount: number;
  reason?: string;
}

export interface SavingsAccountNote {
  id: number;
  savingsAccountId: number;
  note: string;
  by: string;
  when: string;
}

export interface SavingsAccountAuditEntry {
  id: number;
  savingsAccountId: number;
  when: string;
  event: string;
  summary: string;
  amount: number | null;
  by: string;
}

export interface SavingsInterestPreview {
  fromDate: Date;
  toDate: Date;
  days: number;
  averageDailyBalance: number;
  amount: number;
  calculationMethod: string;
  annualInterestRate: number;
}

export interface SavingsInterestPayload {
  fromDate: string;
  toDate: string;
}

export type MakerCheckerStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface MakerCheckerRequest {
  id: number;
  savingsAccountNo: string;
  transactionType: 'DEPOSIT' | 'WITHDRAWAL' | 'INTEREST';
  payloadJson: string;
  amount: number;
  status: MakerCheckerStatus;
  madeBy: string;
  madeOn: Date;
  checkBy?: string | null;
  checkOn?: Date | null;
  rejectReason?: string | null;
}

@Injectable({ providedIn: 'root' })
export class SavingsService {
  private readonly apiBase = '/mini-fineract-provider/api/v1';

  constructor(private readonly http: HttpClient) {}

  getProducts(): Observable<SavingsProduct[]> {
    return this.http.get<SavingsProduct[]>(`${this.apiBase}/savings-products`);
  }

  getProduct(id: number): Observable<SavingsProduct> {
    return this.http.get<SavingsProduct>(`${this.apiBase}/savings-products/${id}`);
  }

  createProduct(payload: SavingsProductPayload): Observable<SavingsProduct> {
    return this.http.post<SavingsProduct>(`${this.apiBase}/savings-products`, payload);
  }

  updateProduct(id: number, payload: SavingsProductPayload): Observable<SavingsProduct> {
    return this.http.put<SavingsProduct>(`${this.apiBase}/savings-products/${id}`, payload);
  }

  getAccounts(): Observable<SavingsAccount[]> {
    return this.http.get<SavingsAccount[]>(`${this.apiBase}/savings-accounts`);
  }

  createAccount(payload: SavingsAccountPayload): Observable<SavingsAccount> {
    return this.http.post<SavingsAccount>(`${this.apiBase}/savings-accounts`, payload);
  }

  updateAccount(id: number, payload: SavingsAccountPayload): Observable<SavingsAccount> {
    return this.http.put<SavingsAccount>(`${this.apiBase}/savings-accounts/${id}`, payload);
  }

  accountCommand(id: number, command: 'approve' | 'activate' | 'freeze' | 'unfreeze' | 'close'): Observable<SavingsAccount> {
    return this.http.post<SavingsAccount>(
      `${this.apiBase}/savings-accounts/${id}?command=${command}`,
      {}
    );
  }

  getClients(): Observable<SavingsClient[]> {
    return this.http.get<SavingsClient[]>(`${this.apiBase}/clients`);
  }

  getTransactions(accountId: number): Observable<SavingsTransaction[]> {
    const params = new HttpParams().set('accountId', accountId.toString());
    return this.http.get<SavingsTransaction[]>(`${this.apiBase}/savings-transactions`, { params });
  }

  createTransaction(payload: SavingsTransactionPayload): Observable<SavingsTransaction> {
    return this.http.post<SavingsTransaction>(`${this.apiBase}/savings-transactions`, payload);
  }

  createCheckMaker(payload: SavingsCheckMakerPayload): Observable<MakerCheckerRequest> {
    return this.http.post<MakerCheckerRequest>(`${this.apiBase}/savings-accounts-check-maker`, payload);
  }

  transfer(payload: SavingsTransferPayload): Observable<SavingsTransaction> {
    return this.http.post<SavingsTransaction>(`${this.apiBase}/savings-transactions/transfer`, payload);
  }

  getStandingInstructions(accountId: number): Observable<StandingInstruction[]> {
    return this.http.get<StandingInstruction[]>(
      `${this.apiBase}/savings-accounts/${accountId}/standing-instructions`
    );
  }

  createStandingInstruction(
    accountId: number,
    payload: StandingInstructionPayload
  ): Observable<StandingInstruction> {
    return this.http.post<StandingInstruction>(
      `${this.apiBase}/savings-accounts/${accountId}/standing-instructions`,
      payload
    );
  }

  getStandingInstructionRuns(accountId: number, instructionId: number): Observable<StandingInstructionRun[]> {
    return this.http.get<StandingInstructionRun[]>(
      `${this.apiBase}/savings-accounts/${accountId}/standing-instructions/${instructionId}/runs`
    );
  }

  updateStandingInstructionStatus(
    accountId: number,
    instructionId: number,
    action: 'pause' | 'resume' |'cancel'
  ): Observable<StandingInstruction> {
    return this.http.post<StandingInstruction>(
      `${this.apiBase}/savings-accounts/${accountId}/standing-instructions/${instructionId}/${action}`,
      {}
    );
  }

  runDueStandingInstructions(accountId: number): Observable<unknown> {
    return this.http.post<unknown>(
      `${this.apiBase}/savings-accounts/${accountId}/standing-instructions/run-due`,
      {}
    );
  }

  undoTransaction(accountId: number, transactionId: number): Observable<SavingsTransaction> {
    return this.http.post<SavingsTransaction>(
      `${this.apiBase}/savings-transactions/${accountId}/transactions/${transactionId}`,
      {}
    );
  }

  getHolds(accountId: number): Observable<SavingsHold[]> {
    return this.http.get<SavingsHold[]>(`${this.apiBase}/savings-accounts/${accountId}/holds`);
  }

  createHold(accountId: number, payload: SavingsHoldPayload): Observable<SavingsHold> {
    return this.http.post<SavingsHold>(`${this.apiBase}/savings-accounts/${accountId}/holds`, payload);
  }

  releaseHold(accountId: number, holdId: number): Observable<SavingsHold> {
    return this.http.post<SavingsHold>(
      `${this.apiBase}/savings-accounts/${accountId}/holds/${holdId}/release`,
      {}
    );
  }

  getAccountNotes(accountId: number): Observable<SavingsAccountNote[]> {
    return this.http.get<SavingsAccountNote[]>(`${this.apiBase}/savings-accounts/${accountId}/notes`);
  }

  createAccountNote(accountId: number, note: string): Observable<SavingsAccountNote> {
    return this.http.post<SavingsAccountNote>(
      `${this.apiBase}/savings-accounts/${accountId}/notes`,
      { note }
    );
  }

  deleteAccountNote(accountId: number, noteId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/savings-accounts/${accountId}/notes/${noteId}`);
  }

  getAccountAuditTrail(accountId: number): Observable<SavingsAccountAuditEntry[]> {
    return this.http.get<SavingsAccountAuditEntry[]>(
      `${this.apiBase}/savings-accounts/${accountId}/audit-trail`
    );
  }

  previewInterest(accountId: number, payload: SavingsInterestPayload): Observable<SavingsInterestPreview> {
    return this.http.post<SavingsInterestPreview>(
      `${this.apiBase}/savings-transactions/${accountId}/interest-preview`,
      payload
    );
  }

  postInterest(accountId: number, payload: SavingsInterestPayload): Observable<unknown> {
    return this.http.post<unknown>(
      `${this.apiBase}/savings-transactions/${accountId}/interest-posting`,
      payload
    );
  }

  getMakerCheckerRequests(
    status?: MakerCheckerStatus,
    accountId?: number
  ): Observable<MakerCheckerRequest[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    if (accountId !== undefined) {
      params = params.set('accountId', accountId.toString());
    }
    return this.http.get<MakerCheckerRequest[]>(`${this.apiBase}/savings-accounts-check-maker`, { params });
  }

  approveMakerCheckerRequest(id: number): Observable<MakerCheckerRequest> {
    return this.http.post<MakerCheckerRequest>(
      `${this.apiBase}/savings-accounts-check-maker/${id}/approve`,
      {}
    );
  }

  rejectMakerCheckerRequest(id: number, reason: string): Observable<MakerCheckerRequest> {
    return this.http.post<MakerCheckerRequest>(
      `${this.apiBase}/savings-accounts-check-maker/${id}/reject`,
      { reason }
    );
  }
}