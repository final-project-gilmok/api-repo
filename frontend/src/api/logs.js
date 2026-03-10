import { api } from './client';

export const fetchRecentLogs = async () => {
    const response = await api.get('/admin/logs');
    return Array.isArray(response) ? response : [];
};