import { createContext } from 'react';

type User = {
  email: string;
};
type LoginStatus = {
  user: User | null;
  isLoading: boolean;
  refresh: null | (() => Promise<void>);
};

export const AuthContext = createContext<LoginStatus>({
  user: null,
  isLoading: false,
  refresh: null,
});
