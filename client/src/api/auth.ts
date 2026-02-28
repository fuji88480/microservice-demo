import { getAccessToken, setAccessToken } from '@/auth/tokenStore';
import { apiFetch } from './client';

export type SignupResponse = {
  accessToken: string;
  message: string;
};

export const signupApi = async (data: {
  email: string;
  password: string;
}) => {
  const response = await apiFetch<SignupResponse>(
    '/api/users/signup',
    {
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify(data),
    },
  );
  setAccessToken(response.accessToken);
  return response;
};

export type SigninResponse = {
  accessToken: string;
  message: string;
};

export const signinApi = async (data: {
  email: string;
  password: string;
}) => {
  const response = await apiFetch<SigninResponse>(
    '/api/users/signin',
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  setAccessToken(response.accessToken);
  return response;
};

export type CurrentuserResponse = {
  email: string;
};
export const currentuserApi = async () => {
  const response = await apiFetch<CurrentuserResponse>(
    '/api/users/currentuser',
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
      credentials: 'include',
    },
  );
  return response;
};

export const refreshApi = async () => {
  const response = await apiFetch<SigninResponse>('/api/users/refresh', {
    method: 'POST',
    credentials: 'include',
  });
  setAccessToken(response.accessToken);
};
