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

export const currentuser = async () => {
  // const response = await apiFetch<RegisterResponse>(
  //   '/api/users/currentuser',
  //   {
  //     method: 'GET',
  //     credentials: 'include',
  //   },
  // );
  // return response;
  console.log('currentuser');
  await new Promise((resolve) => setTimeout(resolve, 1000));
  if (getAccessToken()) {
    return { email: 'test@ttt.com' };
  }
  return { email: null };
};
