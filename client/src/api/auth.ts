import { apiFetch } from './client';

export type RegisterRequest = {
  email: string;
  password: string;
};

export type RegisterResponse = {
  id: string;
  email: string;
};

export const register = async (data: RegisterRequest) => {
  const response = await apiFetch<RegisterResponse>('/users/signup', {
    method: 'POST',
    body: JSON.stringify(data),
  });

  return response;
};
