const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
const AUTH_BASE = import.meta.env.VITE_AUTH_BASE_URL || ''

async function request(baseUrl, path, options = {}) {
  const url = `${baseUrl}${path}`;

  // 1. 요청 전: 로컬 스토리지에서 Access Token 가져와서 헤더에 세팅
  const accessToken = localStorage.getItem('accessToken');
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  // 첫 번째 원본 API 요청
  let res = await fetch(url, { ...options, headers });

  // 2. 응답 후: 401 에러(토큰 만료) 시 자동 갱신 로직 실행 (skipAuthRedirect 시 리다이렉트 없이 throw)
  if (res.status === 401 && !path.includes('/auth/reissue')) {
    if (options.skipAuthRedirect) {
      const err = new Error('인증이 필요합니다.');
      err.status = 401;
      throw err;
    }
    const refreshToken = localStorage.getItem('refreshToken');

    if (refreshToken) {
      try {
        // AUTH_BASE를 사용하여 auth 서버로 토큰 재발급 요청
        const reissueRes = await fetch(`${AUTH_BASE}/auth/reissue`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ refreshToken: refreshToken })
        });

        if (reissueRes.ok) {
          // 재발급 성공! (data 래핑 여부 모두 처리)
          const reissueJson = await reissueRes.json();
          const reissueData = reissueJson.data !== undefined ? reissueJson.data : reissueJson;

          const newAccessToken = reissueData.accessToken;
          const newRefreshToken = reissueData.refreshToken;

          if (!newAccessToken) {
            throw new Error('Invalid reissue response: accessToken is missing');
          }

          // 로컬 스토리지 업데이트
          localStorage.setItem('accessToken', newAccessToken);
          if (newRefreshToken) {
            localStorage.setItem('refreshToken', newRefreshToken);
          }

          // 3. 재시도: 새로 발급받은 AT로 헤더를 덮어쓰고 원래 요청 다시 보내기
          headers['Authorization'] = `Bearer ${newAccessToken}`;
          res = await fetch(url, { ...options, headers });

        } else {
          throw new Error('Refresh Token expired');
        }
      } catch (err) {
        // 재발급 실패 시 auth 키만 제거 후 로그인으로
        const authKeys = ['accessToken', 'refreshToken', 'username', 'role', 'userId'];
        authKeys.forEach((k) => localStorage.removeItem(k));
        window.location.href = '/auth/login';
        throw err;
      }
    } else {
      const authKeys = ['accessToken', 'refreshToken', 'username', 'role', 'userId'];
      authKeys.forEach((k) => localStorage.removeItem(k));
      window.location.href = '/auth/login';
      throw new Error('No refresh token available');
    }
  }

  // 4. 최종 응답 파싱
  const json = await res.json().catch(() => ({}));

  // 백엔드 에러 처리를 HTTP 상태 코드(res.ok) 기반으로 변경
  if (!res.ok) {
    const err = new Error(json.message || res.statusText || '요청 실패');
    err.status = res.status;
    err.code = json.code;
    throw err;
  }

  // 성공 시 껍데기(data) 없이 전체 JSON 반환 (data 필드가 있으면 추출)
  return json.data !== undefined ? json.data : json;
}

export const api = {
  get: (path, headers, opts) => request(API_BASE, path, { method: 'GET', headers, ...opts }),
  post: (path, body, opts) => request(API_BASE, path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined, ...opts }),
  put: (path, body, opts) => request(API_BASE, path, { method: 'PUT', body: JSON.stringify(body), ...opts }),
  delete: (path, opts) => request(API_BASE, path, { method: 'DELETE', ...opts }),
}

export const authApi = {
  get: (path) => request(AUTH_BASE, path, { method: 'GET' }),
  post: (path, body) => request(AUTH_BASE, path, { method: 'POST', body: JSON.stringify(body) }),
  put: (path, body) => request(AUTH_BASE, path, { method: 'PUT', body: JSON.stringify(body) }),
}
