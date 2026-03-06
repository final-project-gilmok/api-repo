const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
const AUTH_BASE = import.meta.env.VITE_AUTH_BASE_URL || ''

/**
 * 토큰 재발급(Refresh) 관련 상태 관리
 * isRefreshing: 현재 재발급 프로세스가 진행 중인지 여부
 * refreshSubscribers: 재발급 진행 중 대기하게 된 요청들의 콜백 리스트 { resolve, reject }
 */
let isRefreshing = false;
let refreshSubscribers = [];

/**
 * 토큰 재발급 완료 후 대기 중인 모든 요청을 재시도
 */
function onRefreshed() {
    refreshSubscribers.forEach(({ resolve }) => {
        resolve();
    });
    refreshSubscribers = [];
}

/**
 * 토큰 재발급 실패 시 대기 중인 모든 요청을 에러 처리
 */
function onRefreshFailed(error) {
    refreshSubscribers.forEach(({ reject }) => {
        reject(error);
    });
    refreshSubscribers = [];
}

/**
 * 재발급 진행 중인 경우, 요청을 대기열에 추가
 */
function addRefreshSubscriber(resolve, reject) {
    refreshSubscribers.push({ resolve, reject });
}

/**
 * 쿠키 이름으로 값을 읽어오는 유틸리티
 */
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
    return null;
}

/**
 * 공통 fetch 래퍼 함수
 * - CSRF 토큰 자동 주입
 * - 401 에러 시 토큰 자동 재발급(Reissue) 및 요청 재시도 로직 포함
 */
async function request(baseUrl, path, options = {}) {
    const url = `${baseUrl}${path}`;

    const fetchOptions = {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': getCookie('XSRF-TOKEN'), // CSRF 방어용 헤더 추가
            ...options.headers,
        },
        credentials: 'include', // 쿠키 기반 인증을 위해 설정
    };

    let res = await fetch(url, fetchOptions);

    // 401 Unauthorized 처리 (이미 재발급 요청인 경우는 제외)
    if (res.status === 401 && !path.includes('/auth/reissue')) {

        // 특정 상황에서 리다이렉트를 건너뛰어야 할 경우 (로그인/회원가입 등)
        if (options.skipAuthRedirect) {
            const err = new Error('인증이 필요합니다.');
            err.status = 401;
            throw err;
        }

        // 1. 이미 다른 요청에 의해 재발급이 진행 중인 경우 대기열 진입
        if (isRefreshing) {
            await new Promise((resolve, reject) => {
                addRefreshSubscriber(resolve, reject);
            });
            // 재발급 완료 후 원래 요청 재시도
            res = await fetch(url, fetchOptions);
        }

        // 2. 처음으로 401을 받아 재발급을 주도하는 경우
        else {
            isRefreshing = true;

            try {
                // 서버에 토큰 재발급 요청
                const reissueRes = await fetch(`${AUTH_BASE}/auth/reissue`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                });

                if (reissueRes.ok) {
                    isRefreshing = false;
                    onRefreshed(); // 대기 중인 요청들 모두 실행

                    // 현재 요청 재시도
                    res = await fetch(url, fetchOptions);
                } else {
                    throw new Error('Refresh Token expired');
                }
            } catch (err) {
                isRefreshing = false;
                onRefreshFailed(err); // 💡 대기 중인 요청들도 모두 에러 처리 (Pending 방지)

                // 인증 정보 만료 시 로컬 스토리지 정리 및 로그인 페이지 이동
                localStorage.removeItem('isLoggedIn')
                localStorage.removeItem('username')
                localStorage.removeItem('role')
                window.location.href = '/auth/login';
                throw err;
            }
        }
    }

    // 응답 결과 처리
    const json = await res.json().catch(() => ({}));

    if (!res.ok) {
        const err = new Error(json.message || res.statusText || '요청 실패');
        err.status = res.status;
        err.code = json.code;
        throw err;
    }

    // API 관례에 따라 data 필드가 있으면 해당 데이터만 반환
    return json.data !== undefined ? json.data : json;
}

/**
 * 일반 서비스용 API 클라이언트
 */
export const api = {
    get: (path, headers, opts) => request(API_BASE, path, { method: 'GET', headers, ...opts }),
    post: (path, body, opts) => request(API_BASE, path, {
        method: 'POST',
        body: body !== undefined ? JSON.stringify(body) : undefined, ...opts
    }),
    put: (path, body, opts) => request(API_BASE, path, { method: 'PUT', body: JSON.stringify(body), ...opts }),
    delete: (path, opts) => request(API_BASE, path, { method: 'DELETE', ...opts }),
}

/**
 * 인증 전용 API 클라이언트
 */
export const authApi = {
    get: (path, opts) => request(AUTH_BASE, path, { method: 'GET', ...opts }),
    post: (path, body, opts) => request(AUTH_BASE, path, {
        method: 'POST',
        body: body !== undefined ? JSON.stringify(body) : undefined,
        ...opts
    }),
    put: (path, body, opts) => request(AUTH_BASE, path, {
        method: 'PUT',
        body: JSON.stringify(body),
        ...opts
    }),
}