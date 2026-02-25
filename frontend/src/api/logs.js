import { api } from './client';

export const fetchRecentLogs = async () => {
    // ⭐️ 아까 브라우저에서 {"status":"success"} 확인했던 바로 그 포트번호를 적으세요! (8080 또는 8081)
    const response = await fetch('http://localhost:8081/api/admin/logs');
    const json = await response.json();
    return json.data; // 성공적으로 받은 data 배열만 리턴!
};
