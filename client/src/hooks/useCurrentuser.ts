import { currentuser } from '@/api/auth';
import { useQuery } from '@tanstack/react-query';

export const useCurrentuser = () => {
  console.log("useCurrentuser")
  return useQuery({
    queryKey: ['currentuser'],
    queryFn: currentuser,
  });
};
