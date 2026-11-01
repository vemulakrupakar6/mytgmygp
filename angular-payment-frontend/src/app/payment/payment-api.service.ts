import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface TaxBreakdown {
  periodFrom?: string | null; periodTo?: string | null;
  houseTax?: number | string | null; libraryCess?: number | string | null;
  drainageTax?: number | string | null; waterTax?: number | string | null;
  lightingTax?: number | string | null; totalAmount?: number | string | null;
}
export interface Assessment {
  assessmentNo?: number | string | null; ptin?: string | null; owner?: string | null;
  fatherHusbandName?: string | null; houseNo?: string | null; buildingUsage?: string | null;
  buildingClassification?: string | null; dtOfAsmt?: string | null; exemption?: string | null;
  courtCase?: string | null;
}
export interface HouseTaxDetails {
  assessment?: Assessment | null; currentDemand?: TaxBreakdown | null;
  arrearDetails?: TaxBreakdown | null; totalPayable?: number | string | null;
  districtName?: string | null; mandalName?: string | null; gramPanchayatName?: string | null;
  serviceName?: string | null; serviceCode?: string | null;
}
export interface PaymentPageData {
  status: string; message?: string; details?: HouseTaxDetails | null;
  amountPaise?: number; currency?: string; receiptNumber?: string | null;
  paymentId?: string | null; paidAt?: string | null;
}
export interface RazorpayOrder {
  keyId: string; orderId: string; amount: number; currency: string;
  name: string; description: string; prefillContact?: string;
}
export interface VerifyResult {
  razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getByToken(token: string): Observable<PaymentPageData> {
    return this.http.get<PaymentPageData>(`${this.base}/api/v1/payments/house-tax/${encodeURIComponent(token)}/details`);
  }
  getByReceipt(receiptNumber: string): Observable<PaymentPageData> {
    return this.http.get<PaymentPageData>(`${this.base}/api/v1/payments/receipt/${encodeURIComponent(receiptNumber)}`);
  }
  createOrder(token: string): Observable<RazorpayOrder> {
    return this.http.post<RazorpayOrder>(`${this.base}/api/v1/payments/house-tax/${encodeURIComponent(token)}/order`, {});
  }
  verify(token: string, result: VerifyResult): Observable<PaymentPageData> {
    return this.http.post<PaymentPageData>(`${this.base}/api/v1/payments/house-tax/${encodeURIComponent(token)}/verify`, result);
  }
}
