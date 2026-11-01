import { Routes } from '@angular/router';
import { PaymentPageComponent } from './payment/payment-page.component';

export const routes: Routes = [
  { path: 'payment/:token', component: PaymentPageComponent },
  { path: 'payment/receipt/:receiptNumber', component: PaymentPageComponent },
  { path: '', redirectTo: 'payment/demo', pathMatch: 'full' },
  { path: '**', redirectTo: '' }
];
