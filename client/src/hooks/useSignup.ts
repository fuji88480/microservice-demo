import { signupApi, type SignupResponse } from '@/api/auth';
import { type ApiError } from '@/api/client';
import { useMutation } from '@tanstack/react-query';

export const useSignup = () => {
  return useMutation<
    SignupResponse,
    ApiError,
    { email: string; password: string }
  >({
    mutationFn: (data: { email: string; password: string }) =>
      signupApi(data),
  });
};
