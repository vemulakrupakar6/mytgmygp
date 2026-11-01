import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { PaymentApiService, PaymentPageData, RazorpayOrder, VerifyResult, TaxBreakdown } from './payment-api.service';

declare global {
  interface Window { Razorpay?: new (options: Record<string, unknown>) => { open: () => void; on: (event: string, callback: (event: unknown) => void) => void } }
}

@Component({
  selector: 'app-payment-page', standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe],
  templateUrl: './payment-page.component.html',
  styleUrl: './payment-page.component.css'
})
export class PaymentPageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PaymentApiService);
  private subscription?: Subscription;
  token: string | null = null;
  receiptNumber: string | null = null;
  data: PaymentPageData | null = null;
  loading = true;
  paying = false;
  message = 'Loading property details securely…';
  error = false;

  get details() { return this.data?.details ?? null; }
  get assessment() { return this.details?.assessment ?? null; }
  get currentDemand() { return this.details?.currentDemand ?? null; }
  get arrears() { return this.details?.arrearDetails ?? null; }
  get isPaid() { return this.data?.status === 'PAID'; }
  get isExpired() { return this.data?.status === 'EXPIRED'; }
  get totalPayable() { return Number(this.details?.totalPayable ?? 0); }

  ngOnInit(): void {
    this.subscription = this.route.paramMap.subscribe(params => {
      this.token = params.get('token');
      this.receiptNumber = params.get('receiptNumber');
      this.load();
    });
  }
  ngOnDestroy(): void { this.subscription?.unsubscribe(); }

  load(): void {
    this.loading = true; this.error = false;
    const request = this.receiptNumber
      ? this.api.getByReceipt(this.receiptNumber)
      : this.token && this.token !== 'demo'
        ? this.api.getByToken(this.token)
        : null;
    if (!request) {
      this.loading = false; this.error = true;
      this.message = 'Open the secure payment link sent to your WhatsApp. Demo URLs do not represent a real payment session.';
      return;
    }
    request.subscribe({
      next: data => { this.data = data; this.loading = false; this.message = data.message ?? ''; this.error = data.status === 'EXPIRED'; },
      error: err => { this.loading = false; this.error = true; this.message = err?.error?.message ?? 'Unable to load this payment link. It may have expired. Return to WhatsApp and request a new link.'; }
    });
  }

  async completePayment(): Promise<void> {
    if (!this.token || this.paying || this.isPaid || this.isExpired) return;
    if (!window.Razorpay) {
      this.error = true; this.message = 'Razorpay Checkout did not load. Check your internet connection and reload this page.'; return;
    }
    this.paying = true; this.error = false; this.message = 'Creating a secure payment order…';
    this.api.createOrder(this.token).subscribe({
      next: order => this.openCheckout(order),
      error: err => { this.paying = false; this.error = true; this.message = err?.error?.message ?? 'Could not create a Razorpay order. Please retry.'; }
    });
  }

  private openCheckout(order: RazorpayOrder): void {
    if (!window.Razorpay) { this.paying = false; this.error = true; this.message = 'Razorpay Checkout did not load.'; return; }
    const options: Record<string, unknown> = {
      key: order.keyId, amount: order.amount, currency: order.currency,
      name: order.name, description: order.description, order_id: order.orderId,
      prefill: { contact: order.prefillContact ?? '' }, theme: { color: '#166534' },
      handler: (result: VerifyResult) => {
        this.message = 'Payment submitted. Verifying it securely with the server…';
        this.api.verify(this.token!, result).subscribe({
          next: response => {
            if (response.status === 'PAID') {
              this.paying = false;
              this.load(); // Fetch complete receipt details from the backend after verification.
            } else {
              this.data = { ...this.data, status: response.status, message: response.message } as PaymentPageData;
              this.message = response.message ?? 'Payment is being confirmed. Check the status before attempting another payment.';
              this.paying = false;
              setTimeout(() => this.load(), 3000);
            }
          },
          error: err => { this.paying = false; this.error = true; this.message = err?.error?.message ?? 'Verification is pending. Reload this page shortly to check the server-confirmed status.'; }
        });
      },
      modal: { ondismiss: () => { this.paying = false; if (!this.isPaid) this.message = 'Checkout closed. No successful payment has been confirmed.'; } },
      retry: { enabled: true }
    };
    const checkout = new window.Razorpay(options);
    checkout.on('payment.failed', () => { this.paying = false; this.error = true; this.message = 'Payment failed or was cancelled. No receipt has been issued. You may retry.'; });
    checkout.open(); this.message = 'Complete your payment in the Razorpay checkout window.';
  }

  money(value: number | string | null | undefined): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Number(value ?? 0));
  }
  taxRows(value: TaxBreakdown | null | undefined): {label: string; value: number}[] {
    if (!value) return [];
    return [
      {label:'House Tax',value:Number(value.houseTax ?? 0)},
      {label:'Library Cess',value:Number(value.libraryCess ?? 0)},
      {label:'Drainage Tax',value:Number(value.drainageTax ?? 0)},
      {label:'Water Tax',value:Number(value.waterTax ?? 0)},
      {label:'Lighting Tax',value:Number(value.lightingTax ?? 0)}
    ];
  }
  printReceipt(): void { window.print(); }
}
