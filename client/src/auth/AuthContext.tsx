import { useCurrentuser } from '@/hooks/useCurrentuser';
import { AuthContext } from './auth-context';

type props = {
  children: React.ReactNode;
};

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
