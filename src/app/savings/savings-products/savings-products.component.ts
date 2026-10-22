import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { SavingsProduct, SavingsService } from '../savings.service';

@Component({
  selector: 'mifosx-savings-products',
  templateUrl: './savings-products.component.html',
  styleUrls: ['../savings-accounts/savings-accounts.component.scss']
})
export class SavingsProductsComponent implements OnInit {
  products: SavingsProduct[] = [];
  busy = true;
  error = '';

  constructor(private readonly savingsService: SavingsService, private readonly router: Router) {}

  ngOnInit(): void {
    this.savingsService.getProducts().subscribe(
      (products) => {
        this.products = Array.isArray(products) ? products : [];
        this.busy = false;
      },
      (err: HttpErrorResponse) => {
        this.error = (err && err.error && err.error.message) || err.message || 'Failed to load savings products.';
        this.busy = false;
      }
    );
  }

  goCreateProduct(): void {
    this.router.navigateByUrl('/savings-products/create');
  }

  goEditProduct(product: SavingsProduct): void {
    this.router.navigateByUrl(`/savings-products/${product.id}/edit`);
  }
}