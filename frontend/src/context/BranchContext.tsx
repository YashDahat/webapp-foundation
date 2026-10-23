// FOUNDATION-owned branch selection. Holds the chosen outlet (persisted to localStorage like the cart).
// On first visit it asks for geolocation and auto-selects the nearest active branch; the user can override
// via the header picker / the /branches page. The selected branchId is passed to catalog + recommendation
// reads and to placeOrder, so only items offered at that branch are shown and the order records the outlet.
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { branchService } from '@/services/branchService';
import type { BranchDto } from '@/types/branch';

interface BranchContextValue {
  branches: BranchDto[];
  selectedBranch: BranchDto | null;
  selectedBranchId: number | null;
  selectBranch: (id: number | null) => void;
  loading: boolean;
}

const STORAGE_KEY = 'selectedBranchId';
const BranchContext = createContext<BranchContextValue | undefined>(undefined);

export function BranchProvider({ children }: { children: ReactNode }) {
  const [branches, setBranches] = useState<BranchDto[]>([]);
  const [selectedBranchId, setSelectedBranchId] = useState<number | null>(() => {
    const v = localStorage.getItem(STORAGE_KEY);
    return v ? Number(v) : null;
  });
  const [loading, setLoading] = useState(true);

  const selectBranch = (id: number | null) => {
    setSelectedBranchId(id);
    if (id == null) localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, String(id));
  };

  useEffect(() => {
    let active = true;
    branchService.list()
      .then((bs) => {
        if (!active) return;
        setBranches(bs);
        const stored = localStorage.getItem(STORAGE_KEY);
        const storedId = stored ? Number(stored) : null;
        const storedValid = storedId != null && bs.some((b) => b.id === storedId);
        if (!storedValid) {
          selectBranch(null);
          // No prior choice → try to auto-select the nearest branch (if the user grants location).
          if (bs.length > 0 && typeof navigator !== 'undefined' && 'geolocation' in navigator) {
            navigator.geolocation.getCurrentPosition(
              (pos) => {
                branchService.nearest(pos.coords.latitude, pos.coords.longitude)
                  .then((near) => { if (active && near.length > 0) selectBranch(near[0].id); })
                  .catch(() => {});
              },
              () => {},
              { timeout: 8000 },
            );
          }
        }
      })
      .catch(() => {})
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value = useMemo<BranchContextValue>(() => ({
    branches,
    selectedBranch: branches.find((b) => b.id === selectedBranchId) ?? null,
    selectedBranchId,
    selectBranch,
    loading,
  }), [branches, selectedBranchId, loading]);

  return <BranchContext.Provider value={value}>{children}</BranchContext.Provider>;
}

export function useBranch(): BranchContextValue {
  const ctx = useContext(BranchContext);
  if (!ctx) throw new Error('useBranch must be used within a BranchProvider');
  return ctx;
}
