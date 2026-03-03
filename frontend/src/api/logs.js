import { api } from './client';

export const fetchRecentLogs = async () => {
    const response = await api.get('/admin/logs', undefined, { skipAuthRedirect: true });
    return Array.isArray(response) ? response : (response?.data || []);
};