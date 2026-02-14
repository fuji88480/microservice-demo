import { signinApi, type SigninResponse } from '@/api/auth';
import type { ApiError } from '@/api/client';
import { useMutation } from '@tanstack/react-query';

export const useSignin = () => {
  return useMutation<
    SigninResponse,
    ApiError,
    { email: string; password: string }
  >({
    mutationFn: (data: { email: string; password: string }) =>
      signinApi(data),
  });
};
