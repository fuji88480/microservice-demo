let accessToekn: string | null = null;

export const getAccessToken = () => accessToekn;

export const setAccessToken = (token: string | null) =>
  (accessToekn = token);
