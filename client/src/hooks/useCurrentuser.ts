import { currentuserApi, refreshApi } from '@/api/auth';
import { useQuery } from '@tanstack/react-query';

export const useCurrentuser = () => {
  return useQuery({
    queryKey: ['currentuser'],
    queryFn: async () => {
      try {
        return await currentuserApi();
      } catch (error) {
        console.log(error);
        await refreshApi();
        return await currentuserApi();
      }
    },
    retry: false,
  });
};
