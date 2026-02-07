import { apiFetch } from './client';

export type RegisterRequest = {
  email: string;
  password: string;
};

type RegisterResponse = {
  id: string;
  email: string;
};

export const register = (data: RegisterRequest) => {
  const response = apiFetch<RegisterResponse>('/user/signup', {
    method: 'POST',
    body: JSON.stringify(data),
  });

  console.log("auth.ts" + response)
  return response;
};
