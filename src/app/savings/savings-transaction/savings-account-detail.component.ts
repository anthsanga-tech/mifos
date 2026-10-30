import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  SavingsAccount,
  SavingsClient,
  SavingsProduct,
  SavingsAccountPayload,
  SavingsService,
  SavingsTransferPayload,
  SavingsTransaction,
  SavingsHold,
  SavingsHoldPayload,
  StandingInstruction,
  StandingInstructionFrequency,
  StandingInstructionPayload,
  StandingInstructionRun,
  SavingsAccountNote,
  SavingsAccountAuditEntry,
  SavingsInterestPayload,
  SavingsInterestPreview,
  MakerCheckerRequest,
  SavingsAccountClosurePreview,
  SavingsAccountClosureResult
} from '../savings.service';

@Component({
  selector: 'mifosx-savings-account-detail',
  templateUrl: './savings-account-detail.component.html',
  styleUrls: ['./savings-account-detail.component.scss']
})
export class SavingsAccountDetailComponent implements OnInit {
  readonly makerCheckerThreshold = 10000;
  account: SavingsAccount | null = null;
  availableAccounts: SavingsAccount[] = [];
  standingInstructions: StandingInstruction[] = [];
  standingInstructionsLoading = false;
  standingInstructionsError = '';
  expandedStandingInstructionId: number | null = null;
  standingInstructionRuns: { [instructionId: number]: StandingInstructionRun[] } = {};
  standingInstructionRunsLoading: { [instructionId: number]: boolean } = {};
  standingInstructionRunsError: { [instructionId: number]: string } = {};
  standingInstructionActionId: number | null = null;
  standingInstructionAction: 'pause' | 'resume' | 'cancel' | null = null;
  standingInstructionSaving = false;
  standingInstructionsRunning = false;
  standingInstructionError = '';
  standingInstructionMessage = '';
  client: SavingsClient | null = null;
  product: SavingsProduct | null = null;
  transactions: SavingsTransaction[] = [];
  holds: SavingsHold[] = [];
  latestUndoableTransactionId: number | null = null;
  notes: SavingsAccountNote[] = [];
  notesLoading = false;
  notesError = '';
  noteSaving = false;
  auditTrail: SavingsAccountAuditEntry[] = [];
  auditTrailLoading = false;
  auditTrailError = '';
  makerCheckerRequests: MakerCheckerRequest[] = [];
  makerCheckerLoading = false;
  makerCheckerError = '';
  newNote = '';
  noteError = '';
  error = '';
  loading = true;
  saving = false;
  fromDate = '';
  toDate = '';
  appliedFromDate = '';
  appliedToDate = '';
  dateFilterError = '';
  holdsError = '';
  holdsLoading = false;
  interestPreview: SavingsInterestPreview | null = null;
  interestError = '';
  interestPreviewLoading = false;
  interestPosting = false;
  closurePreview: SavingsAccountClosurePreview | null = null;
  closurePreviewLoading = false;
  closurePreviewError = '';
  closureSettlementSaving = false;
  closureSettlementMessage = '';
  closureSettlementError = '';
  closureDestinationAccountId: number | null = null;

  interestForm = new FormGroup({
    fromDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    toDate: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  accountNumberForm = new FormGroup({
    accountNo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3)] })
  });

  transactionForm = new FormGroup({
    transactionType: new FormControl<'DEPOSIT' | 'WITHDRAWAL'>('DEPOSIT', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    amount: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.01)]
    })
  });

  transferForm = new FormGroup({
    toAccountId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    amount: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0.01)] })
  });

  standingInstructionForm = new FormGroup({
    toAccountId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    amount: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0.01)] }),
    frequency: new FormControl<StandingInstructionFrequency>('MONTHLY', { nonNullable: true, validators: [Validators.required] }),
    validFrom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    validTo: new FormControl('', { nonNullable: true })
  });

  holdForm = new FormGroup({
    amount: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0.01)] }),
    reason: new FormControl('', { nonNullable: true })
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly savingsService: SavingsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const todayInput = this.localDateInput(new Date());
    this.interestForm.reset({ fromDate: todayInput, toDate: todayInput });
    this.standingInstructionForm.controls.validFrom.setValue(todayInput);

    const accountId = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(accountId) || accountId < 1) {
      this.error = 'Invalid savings account.';
      this.loading = false;
      return;
    }
    this.loadAccount(accountId);
  }

  clientLabel(): string {
    if (!this.account) {
      return '';
    }
    return this.client
      ? `${this.client.firstName} ${this.client.lastName}`
      : `Client ${this.account.clientId}`;
  }

  productLabel(): string {
    if (!this.account) {
      return '';
    }
    return this.product
      ? this.product.name
      : `Product ${this.account.productId}`;
  }

  activeHoldAmount(): number {
    return (this.holds || [])
      .filter((hold) => hold.status === 'ACTIVE')
      .reduce((total, hold) => total + (Number(hold.amount) || 0), 0);
  }

  availableBalance(): number {
    if (!this.account) {
      return 0;
    }
    const minimumRequiredBalance = this.product?.minRequiredBalance ?? 0;
    const result = (Number(this.account.balance) || 0) - (this.activeHoldAmount() + Number(minimumRequiredBalance));
    return result < 0 ? 0 : result;
  }

  fieldError(name: 'amount'): string | null {
    const control = this.transactionForm.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      return 'Enter an amount.';
    }
    if (control.errors['min']) {
      return 'Amount must be greater than zero.';
    }
    return 'Invalid amount.';
  }

  transferFieldError(name: 'toAccountId' | 'amount'): string | null {
    const control = this.transferForm.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      return name === 'toAccountId' ? 'Choose a destination account.' : 'Enter an amount.';
    }
    if (control.errors['min']) {
      return 'Amount must be greater than zero.';
    }
    if (control.errors['invalidDestination']) {
      return 'Choose an active account belonging to the same client.';
    }
    return 'Invalid value.';
  }

  holdAmountError(): string | null {
    const control = this.holdForm.controls.amount;
    if (!control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      return 'Enter an amount.';
    }
    if (control.errors['min']) {
      return 'Amount must be greater than zero.';
    }
    return 'Invalid amount.';
  }

  destinationAccounts(): SavingsAccount[] {
    if (!this.account) {
      return [];
    }
    return this.availableAccounts.filter((candidate) =>
      candidate.id !== this.account?.id &&
      candidate.clientId === this.account?.clientId &&
      candidate.status === 'ACTIVE' &&
      !this.isAccountFrozen(candidate)
    );
  }

  standingInstructionFieldError(name: 'toAccountId' | 'amount' | 'frequency' | 'validFrom'): string | null {
    const control = this.standingInstructionForm.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      if (name === 'toAccountId') {
        return 'Choose a destination account.';
      }
      if (name === 'amount') {
        return 'Enter an amount.';
      }
      if (name === 'validFrom') {
        return 'Choose a valid from date.';
      }
      return 'Choose a frequency.';
    }
    if (control.errors['min']) {
      return 'Amount must be greater than zero.';
    }
    if (control.errors['invalidDestination']) {
      return 'Choose an active account belonging to the same client.';
    }
    return 'Invalid value.';
  }

  standingInstructionDestinationLabel(instruction: StandingInstruction): string {
    if (instruction.toAccountNo) {
      return instruction.toAccountNo;
    }
    const destination = this.availableAccounts.find((candidate) => candidate.id === instruction.toAccountId);
    return destination ? destination.accountNo : `Account ${instruction.toAccountId}`;
  }

  standingInstructionRunReference(run: StandingInstructionRun): string {
    const references: string[] = [];
    if (run.transferId !== null && run.transferId !== undefined) {
      references.push(`Transfer #${run.transferId}`);
    }
    if (run.transferGroupId) {
      references.push(`Transfer group ${run.transferGroupId}`);
    }
    if (run.makerCheckerRequestId !== null && run.makerCheckerRequestId !== undefined) {
      references.push(
        run.status.toUpperCase() === 'PENDING_APPROVAL'
          ? `pending #${run.makerCheckerRequestId}`
          : `MC #${run.makerCheckerRequestId}`
      );
    }
    return references.length > 0 ? references.join(' / ') : '—';
  }

  canShowStandingInstructionHistory(instructionId: number): boolean {
    return this.standingInstructionRunsLoading[instructionId] ||
      !!this.standingInstructionRunsError[instructionId] ||
      (this.standingInstructionRuns[instructionId] || []).length > 0;
  }

  toggleStandingInstructionHistory(instructionId: number): void {
    if (this.expandedStandingInstructionId === instructionId) {
      this.expandedStandingInstructionId = null;
      return;
    }

    this.expandedStandingInstructionId = instructionId;
    if (this.account) {
      this.loadStandingInstructionRuns(this.account.id, instructionId);
    }
  }

  loadStandingInstructionRuns(accountId: number, instructionId: number): void {
    this.standingInstructionRunsLoading[instructionId] = true;
    delete this.standingInstructionRunsError[instructionId];
    this.savingsService.getStandingInstructionRuns(accountId, instructionId).subscribe(
      (runs) => {
        const latestRun = Array.isArray(runs) ? runs[0] : undefined;
        this.standingInstructionRuns[instructionId] =
          latestRun && this.isDisplayableStandingInstructionRun(latestRun) ? [latestRun] : [];
        this.standingInstructionRunsLoading[instructionId] = false;
      },
      (err: HttpErrorResponse) => {
        this.standingInstructionRunsLoading[instructionId] = false;
        this.standingInstructionRunsError[instructionId] =
          this.messageFrom(err, 'Failed to load standing instruction run history.');
      }
    );
  }

  private isDisplayableStandingInstructionRun(run: StandingInstructionRun): boolean {
    const status = run.status.toUpperCase();
    return status === 'SUCCESS' || status === 'PENDING_APPROVAL' || status === 'REJECT';
  }

  canPauseStandingInstruction(instruction: StandingInstruction): boolean {
    return instruction.status.toUpperCase() === 'ACTIVE';
  }

  canResumeStandingInstruction(instruction: StandingInstruction): boolean {
    return instruction.status.toUpperCase() === 'PAUSED';
  }

  canCancelStandingInstruction(instruction: StandingInstruction): boolean {
    return instruction.status.toUpperCase() !== 'CANCELLED' &&
      instruction.status.toUpperCase() !== 'COMPLETED';
  }

  changeStandingInstructionStatus(instruction: StandingInstruction, action: 'pause' | 'resume' | 'cancel'): void {
    if (!this.account || this.standingInstructionActionId !== null ||
      (action === 'pause' && !this.canPauseStandingInstruction(instruction)) ||
      (action === 'resume' && !this.canResumeStandingInstruction(instruction)) ||
      (action === 'cancel' && !this.canCancelStandingInstruction(instruction))) {
      return;
    }

    const accountId = this.account.id;
    this.standingInstructionError = '';
    this.standingInstructionMessage = '';
    this.standingInstructionActionId = instruction.id;
    this.standingInstructionAction = action;
    this.savingsService.updateStandingInstructionStatus(accountId, instruction.id, action).subscribe(
      () => {
        this.standingInstructionActionId = null;
        this.standingInstructionAction = null;
        this.standingInstructionMessage =
          `Standing instruction #${instruction.id} ${action === 'pause' ? 'paused' : action === 'resume' ? 'resumed' : 'cancelled'}.`;
        this.loadAuditTrail(accountId);
        this.loadStandingInstructions(accountId);
      },
      (err: HttpErrorResponse) => {
        this.standingInstructionActionId = null;
        this.standingInstructionAction = null;
        this.standingInstructionError = this.messageFrom(
          err,
          `Failed to ${action} standing instruction #${instruction.id}.`
        );
      }
    );
  }

  createStandingInstruction(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() ||
      this.standingInstructionSaving || this.standingInstructionsRunning) {
      return;
    }
    this.standingInstructionError = '';
    if (this.standingInstructionForm.invalid) {
      this.standingInstructionForm.markAllAsTouched();
      return;
    }

    const value = this.standingInstructionForm.getRawValue();
    if (value.validTo && value.validTo < value.validFrom) {
      this.standingInstructionError = 'Valid to date must be on or after the valid from date.';
      return;
    }

    const toAccountId = Number(value.toAccountId);
    const destination = this.destinationAccounts().find((candidate) => candidate.id === toAccountId);
    if (!destination) {
      this.standingInstructionForm.controls.toAccountId.setErrors({ invalidDestination: true });
      this.standingInstructionForm.controls.toAccountId.markAsTouched();
      return;
    }

    const payload: StandingInstructionPayload = {
      toAccountId,
      amount: Number(value.amount),
      frequency: value.frequency,
      validFrom: value.validFrom,
      validTo: value.validTo || null,
      nextDue: value.validFrom,
    };
    const accountId = this.account.id;
    this.standingInstructionMessage = '';
    this.standingInstructionSaving = true;
    this.savingsService.createStandingInstruction(accountId, payload).subscribe(
      () => {
        this.standingInstructionSaving = false;
        this.standingInstructionForm.reset({
          toAccountId: null,
          amount: null,
          frequency: 'MONTHLY',
          validFrom: this.localDateInput(new Date()),
          validTo: ''
        });
        this.standingInstructionMessage = 'Standing instruction created.';
        this.loadAuditTrail(accountId);
        this.loadStandingInstructions(accountId);
      },
      (err: HttpErrorResponse) => {
        this.standingInstructionSaving = false;
        this.standingInstructionError = this.messageFrom(err, 'Failed to create standing instruction.');
      }
    );
  }

  runDueStandingInstructions(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() ||
      this.standingInstructionsRunning || this.standingInstructionSaving) {
      return;
    }

    this.standingInstructionError = '';
    this.standingInstructionMessage = '';
    this.standingInstructionsRunning = true;
    const accountId = this.account.id;
    this.savingsService.runDueStandingInstructions(accountId).subscribe(
      (result) => {
        this.standingInstructionsRunning = false;
        this.standingInstructionMessage = this.standingInstructionRunDueMessage(result);
        this.loadAuditTrail(accountId);
        this.refreshAccountData();
        this.loadStandingInstructions(accountId);
      },
      (err: HttpErrorResponse) => {
        this.standingInstructionsRunning = false;
        if (err.status === 409) {
          this.standingInstructionMessage = this.messageFrom(
            err,
            'A paused standing instruction was not run.'
          );
          this.loadAuditTrail(accountId);
          this.refreshAccountData();
          this.loadStandingInstructions(accountId);
        } else {
          this.standingInstructionError = this.messageFrom(
            err,
            'Failed to run due standing instructions.'
          );
        }
      }
    );
  }

  private standingInstructionRunDueMessage(result: unknown): string {
    if (typeof result === 'string' && result.trim()) {
      return result.trim();
    }

    if (typeof result === 'object' && result !== null) {
      const summary = result as { [key: string]: unknown };
      const message = summary['message'];
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }

      const businessDate = summary['businessDate'];
      const success = summary['success'];
      const skipped = summary['skipped'];
      const failed = summary['failed'];
      const pending = summary['pending'];
      if (
        typeof businessDate === 'string' &&
        typeof success === 'number' && Number.isFinite(success) &&
        typeof skipped === 'number' && Number.isFinite(skipped) &&
        typeof failed === 'number' && Number.isFinite(failed) &&
        typeof pending === 'number' && Number.isFinite(pending)
      ) {
        return `Run-due ${businessDate}: processed (${success} success, ${skipped} skipped, ${failed} failed, ${pending} pending).`;
      }
    }

    return 'Run-due request completed, but the provider returned no run summary.';
  }

  isAccountFrozen(account: SavingsAccount | null = this.account): boolean {
    return !!account && (account.status === 'FROZEN' || account.freezeStatus === 'FROZEN');
  }

  submitTransfer(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() || this.saving) {
      return;
    }
    if (this.transferForm.invalid) {
      this.transferForm.markAllAsTouched();
      return;
    }

    const value = this.transferForm.getRawValue();
    const toAccountId = Number(value.toAccountId);
    const destination = this.destinationAccounts().find((candidate) => candidate.id === toAccountId);
    if (!destination) {
      this.transferForm.controls.toAccountId.setErrors({ invalidDestination: true });
      this.transferForm.controls.toAccountId.markAsTouched();
      return;
    }

    const payload: SavingsTransferPayload = {
      fromAccountNo: this.account.accountNo,
      toAccountNo: destination.accountNo,
      amount: Number(value.amount)
    };
    this.error = '';
    this.saving = true;
    this.savingsService.transfer(payload).subscribe(
      () => {
        this.saving = false;
        this.transferForm.reset({ toAccountId: null, amount: null });
        this.refreshAccountData();
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, 'Failed to transfer funds.');
      }
    );
  }

  clearInterestPreview(): void {
    this.interestPreview = null;
    this.interestError = '';
  }

  previewInterest(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() ||
      this.interestPreviewLoading || this.interestPosting) {
      return;
    }
    if (this.interestForm.invalid) {
      this.interestForm.markAllAsTouched();
      this.interestError = 'Choose both a from date and a to date.';
      return;
    }

    const payload = this.interestForm.getRawValue();
    if (payload.fromDate > payload.toDate) {
      this.interestError = 'From date must be on or before the To date.';
      return;
    }
    if (this.account.activatedOn && payload.fromDate < this.account.activatedOn.slice(0, 10)) {
      this.interestError = `From date cannot be before activation ${this.account.activatedOn.slice(0, 10)}.`;
      return;
    }

    this.interestPreview = null;
    this.interestError = '';
    this.interestPreviewLoading = true;
    this.savingsService.previewInterest(this.account.id, payload).subscribe(
      (preview) => {
        this.interestPreview = preview;
        this.interestPreviewLoading = false;
      },
      (err: HttpErrorResponse) => {
        this.interestPreviewLoading = false;
        this.interestError = this.messageFrom(err, 'Failed to calculate interest preview.');
      }
    );
  }

  postInterest(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() ||
      this.interestPosting || !this.interestPreview || this.interestForm.invalid) {
      return;
    }
    const payload: SavingsInterestPayload = this.interestForm.getRawValue();
    this.interestError = '';
    this.interestPosting = true;
    this.savingsService.postInterest(this.account.id, payload).subscribe(
      () => {
        this.interestPosting = false;
        this.clearInterestPreview();
        this.refreshAccountData();
      },
      (err: HttpErrorResponse) => {
        this.interestPosting = false;
        this.interestError = this.messageFrom(err, 'Failed to post interest.');
      }
    );
  }

  accountNumberError(): string | null {
    const control = this.accountNumberForm.get('accountNo');
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      return 'Enter an account number.';
    }
    if (control.errors['minlength']) {
      return 'Account number must be at least 3 characters.';
    }
    return 'Invalid account number.';
  }

  saveAccountNumber(): void {
    if (!this.account || this.account.status === 'CLOSED' || this.accountNumberForm.invalid || this.saving) {
      this.accountNumberForm.markAllAsTouched();
      return;
    }

    const accountNo = this.accountNumberForm.getRawValue().accountNo.trim();
    if (accountNo.length < 3) {
      this.accountNumberForm.controls.accountNo.setErrors({ minlength: true });
      this.accountNumberForm.controls.accountNo.markAsTouched();
      return;
    }

    this.error = '';
    this.saving = true;
    const payload: SavingsAccountPayload = {
      clientId: this.account.clientId,
      productId: this.account.productId,
      accountNo
    };
    this.savingsService.updateAccount(this.account.id, payload).subscribe(
      () => {
        if (this.account) {
          this.account = { ...this.account, accountNo };
        }
        this.accountNumberForm.reset({ accountNo });
        this.saving = false;
        this.loadAuditTrail(this.account!.id);
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, 'Failed to update account number.');
      }
    );
  }

  addNote(): void {
    if (!this.account || this.noteSaving) {
      return;
    }
    const trimmed = this.newNote.trim();
    if (!trimmed) {
      this.noteError = 'Note body is required.';
      return;
    }
    if (trimmed.length > 500) {
      this.noteError = 'Note body must not exceed 500 characters.';
      return;
    }

    this.noteError = '';
    this.noteSaving = true;
    this.savingsService.createAccountNote(this.account.id, trimmed).subscribe(
      (note) => {
        this.notes = [note, ...this.notes];
        this.newNote = '';
        this.noteSaving = false;
      },
      (err: HttpErrorResponse) => {
        this.noteSaving = false;
        this.noteError = this.messageFrom(err, 'Failed to add note.');
      }
    );
  }

  deleteNote(note: SavingsAccountNote): void {
    if (!this.account || this.noteSaving) {
      return;
    }
    this.noteError = '';
    this.noteSaving = true;
    this.savingsService.deleteAccountNote(this.account.id, note.id).subscribe(
      () => {
        this.notes = this.notes.filter((item) => item.id !== note.id);
        this.noteSaving = false;
      },
      (err: HttpErrorResponse) => {
        this.noteSaving = false;
        this.noteError = this.messageFrom(err, 'Failed to delete note.');
      }
    );
  }

  reloadNotes(): void {
    if (this.account && !this.notesLoading) {
      this.loadNotes(this.account.id);
    }
  }

  reloadAuditTrail(): void {
    if (this.account && !this.auditTrailLoading) {
      this.loadAuditTrail(this.account.id);
    }
  }

  canUndo(transaction: SavingsTransaction): boolean {
    return this.account?.status === 'ACTIVE' && !this.isAccountFrozen() &&
      transaction.undoable !== false &&
      Number(transaction.reversed) === 0 && transaction.id === this.latestUndoableTransactionId;
  }

  isReversed(transaction: SavingsTransaction): boolean {
    return Number(transaction.reversed) === 1;
  }

  get filteredTransactions(): SavingsTransaction[] {
    return this.transactionsForDateRange(this.appliedFromDate, this.appliedToDate);
  }

  private transactionsForDateRange(fromDate: string, toDate: string): SavingsTransaction[] {
    return this.transactions.filter((transaction) => {
      const transactionDate = transaction.transactionDate.slice(0, 10);
      return (!fromDate || transactionDate >= fromDate) &&
        (!toDate || transactionDate <= toDate);
    });
  }

  applyDateFilter(): void {
    if (this.fromDate && this.toDate && this.fromDate > this.toDate) {
      this.dateFilterError = 'From date must be on or before the To date.';
      return;
    }
    this.dateFilterError = '';
    this.appliedFromDate = this.fromDate;
    this.appliedToDate = this.toDate;
  }

  clearDateFilter(): void {
    this.fromDate = '';
    this.toDate = '';
    this.appliedFromDate = '';
    this.appliedToDate = '';
    this.dateFilterError = '';
  }

  exportTransactionsCsv(): void {
    if (this.fromDate && this.toDate && this.fromDate > this.toDate) {
      this.dateFilterError = 'From date must be on or before the To date.';
      return;
    }
    this.dateFilterError = '';
    const headers = ['ID', 'Type', 'Amount', 'Running balance', 'Group', 'Reversed', 'Date'];
    const transactions = this.transactionsForDateRange(this.fromDate, this.toDate);
    const rows = transactions.map((transaction) => [
      transaction.id,
      transaction.transactionType,
      transaction.amount,
      transaction.runningBalance,
      transaction.transactionGroupId ?? '-',
      this.isReversed(transaction) ? 'Yes' : 'No',
      transaction.transactionDate
    ]);
    const csv = [headers, ...rows]
      .map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(','))
      .join('\r\n');
    const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const accountNo = this.account?.accountNo.replace(/[^a-zA-Z0-9_-]/g, '-') || 'account';
    link.href = url;
    link.download = `${accountNo}-transactions.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  undoTransaction(transaction: SavingsTransaction): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() || !this.canUndo(transaction) || this.saving) {
      return;
    }
    if (!window.confirm('Undo the latest transaction?')) {
      return;
    }

    this.error = '';
    this.saving = true;
    this.savingsService.undoTransaction(this.account.id, transaction.id).subscribe(
      () => {
        this.saving = false;
        this.refreshAccountData();
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, 'Failed to undo transaction.');
      }
    );
  }

  submitTransaction(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen()) {
      this.error = 'Transactions are available only for unfrozen ACTIVE accounts.';
      return;
    }
    if (this.transactionForm.invalid || this.saving) {
      this.transactionForm.markAllAsTouched();
      return;
    }

    this.error = '';
    this.saving = true;
    const value = this.transactionForm.getRawValue();
    const threshold = 10000;
    if (Number(value.amount) >= threshold) {
      this.savingsService.createCheckMaker({
        savingsAccountId: this.account.id,
        transactionType: value.transactionType,
        amount: Number(value.amount)
      }).subscribe(
        () => {
          this.saving = false;
          this.transactionForm.reset({ transactionType: 'DEPOSIT', amount: null });
          this.refreshAccountData();
        },
        (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = this.messageFrom(err, 'Failed to submit maker-checker request.');
        }
      );
    } else {
        this.savingsService.createTransaction({
        savingsAccountId: this.account.id,
        transactionType: value.transactionType,
        amount: Number(value.amount)
      }).subscribe(
        () => {
          this.saving = false;
          this.transactionForm.reset({ transactionType: 'DEPOSIT', amount: null });
          this.refreshAccountData();
        },
        (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = this.messageFrom(err, 'Failed to record transaction.');
        }
      );
    }
  }

  confirmCloseAndSettle(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.closureSettlementSaving) {
      return;
    }
    if (!this.closurePreview || !this.canConfirmClosure()) {
      this.closureSettlementError = 'Refresh the preview and resolve all blockers before confirming closure.';
      return;
    }
    this.closureSettlementError = '';
    this.closureSettlementMessage = '';
    this.closureSettlementSaving = true;
    this.savingsService.closeAndSettleAccount(this.account.id, this.closureDestinationAccountId).subscribe(
      (result: SavingsAccountClosureResult) => {
        this.closureSettlementSaving = false;
        this.closureSettlementMessage = result.message;
        this.refreshAccountData();
      },
      (err: HttpErrorResponse) => {
        this.closureSettlementSaving = false;
        this.closureSettlementError = this.messageFrom(err, 'Failed to close and settle savings account.');
        this.loadClosurePreview(this.account!.id);
      }
    );
  }

  refreshClosurePreview(): void {
    if (this.account) {
      this.loadClosurePreview(this.account.id);
    }
  }

  canConfirmClosure(): boolean {
    if (!this.closurePreview?.canClose) {
      return false;
    }
    if (this.closurePreview.residualAmount <= 0) {
      return true;
    }
    return this.closurePreview.eligibleDestinations.some(
      (destination) => destination.accountId === this.closureDestinationAccountId
    );
  }

  closureDestinationChanged(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.closureDestinationAccountId = value ? Number(value) : null;
  }

  private loadClosurePreview(accountId: number): void {
    this.closurePreviewLoading = true;
    this.closurePreviewError = '';
    this.closurePreview = null;
    this.savingsService.previewAccountClosure(accountId).subscribe(
      (preview) => {
        this.closurePreview = preview;
        const destinations = preview.eligibleDestinations || [];
        if (destinations.length === 1) {
          this.closureDestinationAccountId = destinations[0].accountId;
        } else if (!destinations.some((destination) => destination.accountId === this.closureDestinationAccountId)) {
          this.closureDestinationAccountId = null;
        }
        this.closurePreviewLoading = false;
      },
      (err: HttpErrorResponse) => {
        this.closurePreviewLoading = false;
        this.closurePreviewError = this.messageFrom(err, 'Failed to load close-and-settle preview.');
      }
    );
  }

  freezeAccount(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() || this.saving) {
      return;
    }
    this.error = '';
    this.saving = true;
    this.savingsService.accountCommand(this.account.id, 'freeze').subscribe(
      () => {
        this.saving = false;
        this.refreshAccountData();
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, 'Failed to freeze savings account.');
      }
    );
  }

  unfreezeAccount(): void {
    if (!this.account || this.account.status === 'CLOSED' || !this.isAccountFrozen() || this.saving) {
      return;
    }
    this.error = '';
    this.saving = true;
    this.savingsService.accountCommand(this.account.id, 'unfreeze').subscribe(
      () => {
        this.saving = false;
        this.refreshAccountData();
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.messageFrom(err, 'Failed to unfreeze savings account.');
      }
    );
  }

  placeHold(): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() || this.saving) {
      return;
    }
    if (this.holdForm.invalid) {
      this.holdForm.markAllAsTouched();
      return;
    }

    const value = this.holdForm.getRawValue();
    const payload: SavingsHoldPayload = {
      amount: Number(value.amount),
      reason: value.reason.trim() || undefined
    };
    this.holdsError = '';
    this.saving = true;
    this.savingsService.createHold(this.account.id, payload).subscribe(
      () => {
        this.saving = false;
        this.holdForm.reset({ amount: null, reason: '' });
        this.loadHolds(this.account!.id);
        this.loadAuditTrail(this.account!.id);
        this.loadClosurePreview(this.account!.id);
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.holdsError = this.messageFrom(err, 'Failed to place hold.');
      }
    );
  }

  releaseHold(hold: SavingsHold): void {
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen() || hold.status !== 'ACTIVE' || this.saving) {
      return;
    }
    this.holdsError = '';
    this.saving = true;
    this.savingsService.releaseHold(this.account.id, hold.id).subscribe(
      () => {
        this.saving = false;
        this.loadHolds(this.account!.id);
        this.loadAuditTrail(this.account!.id);
        this.loadClosurePreview(this.account!.id);
      },
      (err: HttpErrorResponse) => {
        this.saving = false;
        this.holdsError = this.messageFrom(err, 'Failed to release hold.');
      }
    );
  }

  private loadHolds(accountId: number): void {
    this.holdsLoading = true;
    this.savingsService.getHolds(accountId).subscribe(
      (holds) => {
        this.holds = Array.isArray(holds) ? holds : [];
        this.holdsLoading = false;
      },
      (err: HttpErrorResponse) => {
        this.holdsLoading = false;
        this.holdsError = this.messageFrom(err, 'Failed to load holds.');
      }
    );
  }

  private loadNotes(accountId: number): void {
    this.notesLoading = true;
    this.notesError = '';
    this.savingsService.getAccountNotes(accountId).subscribe(
      (notes) => {
        this.notes = Array.isArray(notes) ? notes : [];
        this.notesLoading = false;
      },
      (err: HttpErrorResponse) => {
        this.notesLoading = false;
        this.notesError = this.messageFrom(err, 'Failed to load account notes.');
      }
    );
  }

  private loadAuditTrail(accountId: number): void {
    this.auditTrailLoading = true;
    this.auditTrailError = '';
    this.savingsService.getAccountAuditTrail(accountId).subscribe(
      (entries) => {
        this.auditTrail = Array.isArray(entries) ? entries : [];
        this.auditTrailLoading = false;
      },
      (err: HttpErrorResponse) => {
        this.auditTrailLoading = false;
        this.auditTrailError = this.messageFrom(err, 'Failed to load audit trail.');
      }
    );
  }

  private loadMakerCheckerRequests(accountId: number): void {
    this.makerCheckerLoading = true;
    this.makerCheckerError = '';
    this.savingsService.getMakerCheckerRequests('PENDING', accountId).subscribe(
      (requests) => {
        this.makerCheckerRequests = requests;
        this.makerCheckerLoading = false;
      },
      (err: HttpErrorResponse) => {
        this.makerCheckerLoading = false;
        this.makerCheckerError = this.messageFrom(err, 'Failed to load maker-checker status.');
      }
    );
  }

  private loadAccount(accountId: number): void {
    this.loading = true;
    forkJoin(
      this.savingsService.getAccounts(),
      this.savingsService.getClients(),
      this.savingsService.getProducts(),
      this.savingsService.getTransactions(accountId)
    ).subscribe(
      ([accounts, clients, products, transactions]) => {
        this.availableAccounts = Array.isArray(accounts) ? accounts : [];
        this.account = this.availableAccounts.find((item) => item.id === accountId) || null;
        if (this.account) {
          this.accountNumberForm.reset({ accountNo: this.account.accountNo });
          this.loadClosurePreview(this.account.id);
          this.loadHolds(this.account.id);
          this.loadNotes(this.account.id);
          this.loadAuditTrail(this.account.id);
          this.loadMakerCheckerRequests(this.account.id);
          this.loadStandingInstructions(this.account.id);
        }
        this.client = (Array.isArray(clients) ? clients : []).find((item) => item.id === this.account?.clientId) || null;
        this.product = (Array.isArray(products) ? products : []).find((item) => item.id === this.account?.productId) || null;
        this.transactions = Array.isArray(transactions) ? transactions : [];
        this.updateLatestUndoableTransaction();
        if (!this.account) {
          this.error = 'Savings account not found.';
        }
        this.loading = false;
      },
      (err: HttpErrorResponse) => {
        this.error = this.messageFrom(err, 'Failed to load savings account.');
        this.loading = false;
      }
    );
  }

  private refreshAccountData(): void {
    if (!this.account) {
      return;
    }
    this.loadAuditTrail(this.account.id);
    this.loadMakerCheckerRequests(this.account.id);
    this.loadClosurePreview(this.account.id);
    forkJoin(
      this.savingsService.getAccounts(),
      this.savingsService.getTransactions(this.account.id)
    ).subscribe(
      ([accounts, transactions]) => {
        this.availableAccounts = Array.isArray(accounts) ? accounts : [];
        this.account = this.availableAccounts.find((item) => item.id === this.account?.id) || this.account;
        this.transactions = Array.isArray(transactions) ? transactions : [];
        this.updateLatestUndoableTransaction();
      },
      (err: HttpErrorResponse) => {
        this.error = this.messageFrom(err, 'Transaction posted, but account details could not be refreshed.');
      }
    );
  }

  loadStandingInstructions(accountId: number): void {
    this.standingInstructionsLoading = true;
    this.standingInstructionsError = '';
    this.savingsService.getStandingInstructions(accountId).subscribe(
      (instructions) => {
        this.standingInstructions = Array.isArray(instructions) ? instructions : [];
        this.standingInstructionsLoading = false;
        this.standingInstructionRuns = {};
        this.standingInstructionRunsLoading = {};
        this.standingInstructionRunsError = {};
        this.standingInstructions.forEach((instruction) => {
          this.loadStandingInstructionRuns(accountId, instruction.id);
        });
      },
      (err: HttpErrorResponse) => {
        this.standingInstructionsLoading = false;
        this.standingInstructionsError = this.messageFrom(err, 'Failed to load standing instructions.');
      }
    );
  }

  private localDateInput(date: Date): string {
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  }

  private updateLatestUndoableTransaction(): void {
    this.latestUndoableTransactionId = null;
    if (!this.account || this.account.status !== 'ACTIVE' || this.isAccountFrozen()) {
      return;
    }

    const candidates = this.transactions.filter((transaction) => Number(transaction.reversed) === 0);
    const latest = candidates.reduce<SavingsTransaction | null>((current, candidate) => {
      if (!current) {
        return candidate;
      }
      const candidateTime = Date.parse(candidate.transactionDate);
      const currentTime = Date.parse(current.transactionDate);
      if (candidateTime > currentTime || (candidateTime === currentTime && candidate.id > current.id)) {
        return candidate;
      }
      if (!Number.isFinite(candidateTime) && !Number.isFinite(currentTime) && candidate.id > current.id) {
        return candidate;
      }
      return current;
    }, null);

    this.latestUndoableTransactionId = latest ? latest.id : null;
  }

  private messageFrom(err: HttpErrorResponse, fallback: string): string {
    return (err && err.error && err.error.message) || err.message || fallback;
  }
}
