import React, { useState, useEffect } from 'react';
import { fetchRecentLogs } from '../../api/logs.js'; // 방금 만든 API 함수 가져오기

const Monitoring = () => {
    const [logs, setLogs] = useState([]);

    useEffect(() => {
        const getLogs = async () => {
            try {
                const data = await fetchRecentLogs();
                setLogs(data);
            } catch (error) {
                console.error('로그 조회 실패:', error);
            }
        };

        getLogs(); // 처음 페이지 열 때 1번 호출

        // 5초마다 데이터 새로고침
        const interval = setInterval(getLogs, 5000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
            <h1>🚦 실시간 트래픽 관제 대시보드</h1>

            {/* 상단: 그라파나 대시보드 (Iframe) */}
            <section style={{ marginBottom: '40px' }}>
                <h2>📊 시스템 메트릭 (Grafana)</h2>
                <div style={{ border: '1px solid #ccc', borderRadius: '8px', overflow: 'hidden' }}>
                    <iframe
                        src="http://localhost:3000/goto/dfe6s8njprw1sa?orgId=1&kiosk"
                        width="100%"
                        height="500px"
                        frameBorder="0"
                        title="Grafana Dashboard"
                    ></iframe>
                </div>
            </section>

            {/* 하단: 실시간 API 요청 로그 */}
            <section>
                <h2>📝 최신 API 요청 로그 (Top 100)</h2>
                <div style={{ overflowX: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead style={{ backgroundColor: '#f8f9fa' }}>
                        <tr>
                            <th style={{ padding: '12px', borderBottom: '2px solid #ddd' }}>요청 시간</th>
                            <th style={{ padding: '12px', borderBottom: '2px solid #ddd' }}>메서드</th>
                            <th style={{ padding: '12px', borderBottom: '2px solid #ddd' }}>경로 (Path)</th>
                            <th style={{ padding: '12px', borderBottom: '2px solid #ddd' }}>상태 코드</th>
                            <th style={{ padding: '12px', borderBottom: '2px solid #ddd' }}>처리 시간</th>
                        </tr>
                        </thead>
                        <tbody>
                        {logs.map((log) => (
                            <tr key={log.id} style={{ borderBottom: '1px solid #eee' }}>
                                <td style={{ padding: '12px' }}>{new Date(log.timestamp).toLocaleString()}</td>
                                <td style={{ padding: '12px', fontWeight: 'bold', color: log.method === 'GET' ? 'blue' : 'green' }}>
                                    {log.method}
                                </td>
                                <td style={{ padding: '12px' }}>{log.path}</td>
                                <td style={{ padding: '12px', color: log.status >= 400 ? 'red' : '#28a745', fontWeight: 'bold' }}>
                                    {log.status}
                                </td>
                                <td style={{ padding: '12px' }}>{log.latencyMs} ms</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            </section>
        </div>
    );
};

export default Monitoring;
