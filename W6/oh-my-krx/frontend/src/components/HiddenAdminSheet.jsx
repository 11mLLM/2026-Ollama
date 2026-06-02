import React, { useState } from 'react';
import { runAdminAction } from '../services/apiClient';
import { X, Play, Database, CheckSquare } from 'lucide-react';
import clsx from 'clsx';
import styles from './HiddenAdminSheet.module.css';

export default function HiddenAdminSheet({ isOpen, onClose, asOf }) {
  const [adminKey, setAdminKey] = useState('');
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);

  const addLog = (msg, type = 'info') => {
    setLogs(prev => [`[${new Date().toLocaleTimeString()}] ${type.toUpperCase()}: ${msg}`, ...prev]);
  };

  const handleAction = async (actionName, label) => {
    if (!adminKey) {
      addLog('Admin Key를 입력해주세요.', 'error');
      return;
    }
    setLoading(true);
    addLog(`${label} 시작...`);
    try {
      const res = await runAdminAction(actionName, adminKey);
      addLog(`${label} 완료.`, 'success');
    } catch (err) {
      addLog(`${label} 실패: ${err.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={clsx(styles.overlay, isOpen && styles.open)}>
      <div className={styles.backdrop} onClick={onClose} />
      <div className={styles.sheet}>
        <div className={styles.header}>
          <h2>관리자 콘솔</h2>
          <button onClick={onClose} className={styles.closeBtn}><X /></button>
        </div>

        <div className={styles.content}>
          <div className={styles.inputGroup}>
            <label>Admin Key</label>
            <input 
              type="password" 
              value={adminKey}
              onChange={e => setAdminKey(e.target.value)}
              placeholder="시스템 제어 키 입력"
            />
          </div>

          <div className={styles.actionGrid}>
            <button className={styles.actionBtn} onClick={() => handleAction('today', '브리프 생성')} disabled={loading}>
              <Play size={18} /> 브리프 생성
            </button>
            <button className={styles.actionBtn} onClick={() => handleAction('backfill', '데이터 백필')} disabled={loading}>
              <Database size={18} /> 데이터 백필
            </button>
            <button className={styles.actionBtn} onClick={() => handleAction('verify', '데이터 검증')} disabled={loading}>
              <CheckSquare size={18} /> 데이터 검증
            </button>
          </div>

          <div className={styles.logBox}>
            {logs.length === 0 ? <p className={styles.emptyLog}>로그가 없습니다.</p> : logs.map((l, i) => <div key={i} className={styles.logLine}>{l}</div>)}
          </div>
        </div>
      </div>
    </div>
  );
}
