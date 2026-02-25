import { api } from './client';

export const fetchRecentLogs = async () => {
    const response = await api.get('/api/admin/logs');
    // ✅ 리뷰 반영: data가 없을 경우 undefined 대신 빈 배열([])을 반환하여 map() 에러 방지
    return response.data || [];
};