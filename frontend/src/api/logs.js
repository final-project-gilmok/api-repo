import { api } from './client';

// 시스템 전체 로그 조회 (Gateway 등에서 넘겨주는 로그)
export function fetchRecentLogs() {
    return api.get('/admin/logs');
}

// 필요하다면 시스템 전체 통계(Stats) 등 다른 어드민 공통 API도 여기에 추가
export function getSystemStats() {
    return api.get('/admin/stats');
}