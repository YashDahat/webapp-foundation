// GENERATED from the backend API contract — do not edit by hand.
import axios from 'axios';

const apiClient = axios.create({ baseURL: '' });

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default apiClient;
