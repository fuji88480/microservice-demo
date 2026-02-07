import { register, type RegisterRequest } from '@/api/auth';
import { useMutation } from '@tanstack/react-query';

export const useRegister = () => {
  return useMutation({
    mutationFn: (data: RegisterRequest) => register(data),
  });
};
