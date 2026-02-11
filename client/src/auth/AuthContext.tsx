import { useCurrentuser } from '@/hooks/useCurrentuser';
import React, { createContext } from 'react';

type User = {
  email: string;
};
type LoginStatus = {
  user: User | null;
  isLoading: boolean;
  refresh: () => Promise<void>;
};

type props = {
  children: React.ReactNode;
};
const AuthContext = createContext<LoginStatus | null>(null);

export const AuthProvider = ({ children }: props) => {
  const { data, isLoading, refetch } = useCurrentuser();

  const refresh = async () => {
    await refetch();
  };

  return (
    <AuthContext.Provider
      value={{
        user: data?.email ? { email: data.email } : null,
        isLoading,
        refresh,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
