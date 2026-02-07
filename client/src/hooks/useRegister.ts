import {
  register,
  type RegisterRequest,
  type RegisterResponse,
} from '@/api/auth';
import { type ApiError } from '@/api/client';

import { useMutation } from '@tanstack/react-query';

export const useRegister = () => {
  return useMutation<RegisterResponse, ApiError, RegisterRequest>({
    mutationFn: (data: RegisterRequest) => register(data),
  });
};
