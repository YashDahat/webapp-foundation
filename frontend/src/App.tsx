import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CartProvider } from '@/cart/CartContext'

const queryClient = new QueryClient()

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <CartProvider>
        <BrowserRouter>
          <Routes>
            <Route path="*" element={
              <div style={{ fontFamily: 'sans-serif', padding: '2rem', textAlign: 'center' }}>
                <h1>WebApp Foundation</h1>
                <p>Foundation scaffold is running. Business-specific pages are generated on top.</p>
                <p><a href="/api/actuator/health">Health check</a></p>
              </div>
            } />
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </QueryClientProvider>
  )
}
