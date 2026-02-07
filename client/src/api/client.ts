export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type FetchOptions = RequestInit & {
  params?: Record<string, string | number | undefined>;
};

export const apiFetch = async <T>(
  url: string,
  fetchOption: FetchOptions = {},
): Promise<T> => {
  const { params, headers, ...rest } = fetchOption;

  if (params) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) {
        search.append(key, String(value));
      }
    }

    url += `?${search.toString()}`;
  }

  const res = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    ...rest,
  });

  if (!res.ok) {
    let message = 'request failed';
    const data = await res.json();
    message = data?.message ?? message;
    throw new ApiError(res.status, message ?? 'Request failed');
  }

  return res.json();
};
