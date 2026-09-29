import { toast } from 'sonner';

/**
 * Success-toast helper for mutations. Awaits an API promise and shows one `toast.success(message)`
 * on success. Errors are NOT toasted here — the apiClient response interceptor already surfaces a
 * single `toast.error` for any failed request, so this rejects onward without double-toasting.
 *
 *   await withToast(supportService.reply(id, body), 'Reply sent');
 */
export async function withToast<T>(promise: Promise<T>, success: string): Promise<T> {
  const result = await promise;
  toast.success(success);
  return result;
}
