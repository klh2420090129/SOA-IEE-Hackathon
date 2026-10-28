export class ApiError extends Error {
  public status: number;
  public code: string;
  public details?: any;

  constructor(status: number, message: string, code: string = 'UNKNOWN_ERROR', details?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

let activeToken: string | null = null;
let onUnauthorizedHandler: (() => void) | null = null;

export function setApiAuthToken(token: string | null) {
  activeToken = token;
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorizedHandler = handler;
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers || {});
  
  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (activeToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${activeToken}`);
  }

  const response = await fetch(path, {
    ...options,
    headers
  });

  if (response.status === 401) {
    if (onUnauthorizedHandler) {
      onUnauthorizedHandler();
    }
  }

  let data: any = null;
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  } else {
    data = await response.text();
  }

  if (!response.ok) {
    const errorCode = (data && typeof data === 'object' && data.code) 
      ? data.code 
      : `HTTP_${response.status}`;
    const errorMessage = (data && typeof data === 'object' && (data.message || data.error))
      ? (data.message || data.error)
      : (typeof data === 'string' && data ? data : response.statusText || 'API request failed');

    throw new ApiError(response.status, errorMessage, errorCode, data);
  }

  return data as T;
}
