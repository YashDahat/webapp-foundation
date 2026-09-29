// GENERATED from the backend API contract — do not edit by hand.
import axios from 'axios';
import { toast } from 'sonner';

const apiClient = axios.create({ baseURL: '' });

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Global error feedback: every failed request surfaces a single toast.error. Do NOT add manual
// toast.error after a caught request error (it would double-toast). For success feedback on mutations,
// wrap the call in withToast(promise, 'message') from '@/lib/apiToast'.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const msg =
      error?.response?.data?.message ?? error?.message ?? 'Something went wrong';
    toast.error(msg);
    return Promise.reject(error);
  },
);

export default apiClient;
