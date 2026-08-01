import React, { createContext, useContext, useState, ReactNode } from 'react';
import type { DeliveryAddressFormData } from '@/components/checkout/DeliveryAddressForm';

interface CheckoutContextType {
  currentStep: number;
  deliveryDetails: DeliveryAddressFormData | null;
  setDeliveryDetails: (data: DeliveryAddressFormData) => void;
  nextStep: () => void;
  prevStep: () => void;
  resetCheckout: () => void;
}

const CheckoutContext = createContext<CheckoutContextType | undefined>(undefined);

export const CheckoutProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [currentStep, setCurrentStep] = useState(1);
  const [deliveryDetails, setDeliveryDetails] = useState<DeliveryAddressFormData | null>(null);

  const nextStep = () => setCurrentStep((s) => s + 1);
  const prevStep = () => setCurrentStep((s) => Math.max(1, s - 1));
  const resetCheckout = () => {
    setCurrentStep(1);
    setDeliveryDetails(null);
  };

  return (
    <CheckoutContext.Provider
      value={{ currentStep, deliveryDetails, setDeliveryDetails, nextStep, prevStep, resetCheckout }}
    >
      {children}
    </CheckoutContext.Provider>
  );
};

export const useCheckout = (): CheckoutContextType => {
  const ctx = useContext(CheckoutContext);
  if (!ctx) throw new Error('useCheckout must be used within a CheckoutProvider');
  return ctx;
};
