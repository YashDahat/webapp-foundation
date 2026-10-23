// Loads the Razorpay Checkout SDK on demand and opens the hosted modal. The gateway order is created
// server-side (OrderService → PaymentService.createOrder); this only drives the client-side checkout and
// resolves with the fields the backend's /payments/verify needs.

export interface RazorpayResult {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

export interface RazorpayOptions {
  keyId: string;
  orderId: string;
  amount: number; // smallest currency unit (paise)
  currency: string;
  name: string;
  description?: string;
}

interface RazorpayInstance {
  open: () => void;
}
type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayInstance;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

const SDK_URL = 'https://checkout.razorpay.com/v1/checkout.js';

/** Inject the Razorpay SDK once; resolves false if it fails to load. */
export function loadRazorpay(): Promise<boolean> {
  if (window.Razorpay) {
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    const existing = document.querySelector(`script[src="${SDK_URL}"]`);
    if (existing) {
      existing.addEventListener('load', () => resolve(true));
      existing.addEventListener('error', () => resolve(false));
      return;
    }
    const script = document.createElement('script');
    script.src = SDK_URL;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

/** Open the Razorpay modal; resolves on success, rejects on dismissal/failure. */
export function openRazorpay(options: RazorpayOptions): Promise<RazorpayResult> {
  return new Promise((resolve, reject) => {
    const Razorpay = window.Razorpay;
    if (!Razorpay) {
      reject(new Error('Razorpay SDK not available'));
      return;
    }
    const instance = new Razorpay({
      key: options.keyId,
      order_id: options.orderId,
      amount: options.amount,
      currency: options.currency,
      name: options.name,
      description: options.description,
      handler: (response: RazorpayResult) => resolve(response),
      modal: { ondismiss: () => reject(new Error('Payment cancelled')) },
    });
    instance.open();
  });
}
