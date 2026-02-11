import { apiFetch } from './client';

export type RegisterRequest = {
  email: string;
  password: string;
};

export type RegisterResponse = {
  id: string;
  email: string;
};

export const currentuser = async () => {
  const response = await apiFetch<RegisterResponse>(
    '/api/users/currentuser',
    {
      method: 'GET',
      credentials: 'include',
    },
  );
  // return response;
  return { email: 'test@test.com' };
};

export const register = async (data: RegisterRequest) => {
  const response = await apiFetch<RegisterResponse>(
    '/api/users/signup',
    {
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify(data),
    },
  );

  return response;
};
