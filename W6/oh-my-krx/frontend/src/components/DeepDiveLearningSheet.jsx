import React from 'react';
import { X, ExternalLink, HelpCircle, AlertTriangle } from 'lucide-react';
import styles from './DeepDiveLearningSheet.module.css';

function formatWon(value) {
  if (value === null || value === undefined || value === '') return '확인 필요';
  const number = Number(value);
  if (!Number.isFinite(number)) return '확인 필요';
  return `${Math.round(number).toLocaleString()}원`;
}

function intervalLabel(interval) {
  if (interval === 'weekly') return '주봉';
  if (interval === 'monthly') return '월봉';
  return '일봉';
}

export default function DeepDiveLearningSheet({ isOpen, onClose, termData, chartContext }) {
  if (!isOpen || !termData) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.backdrop} onClick={onClose} />
      <div className={styles.sheet}>
        <div className={styles.header}>
          <div className={styles.titleGroup}>
            <span className={styles.badge}>초보자 딥다이브</span>
            <h2>{termData.term}</h2>
          </div>
          <button onClick={onClose} className={styles.closeBtn}><X /></button>
        </div>

        <div className={styles.content}>
          <div className={styles.heroSection}>
            <p className={styles.coreSentence}>
              <strong>핵심 한 줄:</strong> {termData.coreSummary}
            </p>
          </div>

          <div className={styles.detailSection}>
            <h3>쉬운 설명</h3>
            <p>{termData.longExplanation}</p>
            {termData.whyItMatters && <p style={{ marginTop: '8px' }}><strong>왜 중요한가요?</strong> {termData.whyItMatters}</p>}
            <p style={{ marginTop: '8px' }}><strong>어디서 보나요?</strong> {termData.chartUsage}</p>
          </div>

          {chartContext && (
            <div className={styles.chartContextSection}>
              <div className={styles.chartContextHeader}>
                <span>현재 차트 연결</span>
                <strong>{chartContext.stockName} · {intervalLabel(chartContext.interval)} · {chartContext.asOf}</strong>
              </div>
              <p>
                지금 보고 있는 차트에서는 현재가 {formatWon(chartContext.latestClose)}, 20일선 {formatWon(chartContext.ma20)},
                지지선 {formatWon(chartContext.supportLevel)}, 저항선 {formatWon(chartContext.resistanceLevel)}을 같이 봅니다.
              </p>
              <div className={styles.chartMetricGrid}>
                <span>현재가: {formatWon(chartContext.latestClose)}</span>
                <span>20일선: {formatWon(chartContext.ma20)}</span>
                <span>지지선: {formatWon(chartContext.supportLevel)}</span>
                <span>저항선: {formatWon(chartContext.resistanceLevel)}</span>
              </div>
              <p className={styles.contextNote}>
                {termData.term} 하나만 보고 결론을 내리지 말고, 같은 방향의 근거와 반대 신호를 함께 확인하세요.
              </p>
            </div>
          )}

          <div className={styles.gridSection}>
            <div className={styles.card}>
              <HelpCircle className={styles.cardIcon} />
              <h4>자주 하는 오해</h4>
              <p>{termData.commonMisunderstanding}</p>
            </div>
            <div className={styles.card}>
              <AlertTriangle className={styles.cardIcon} />
              <h4>실전 시나리오</h4>
              <p>{termData.scenario}</p>
            </div>
          </div>

          <div className={styles.actionSection}>
            {termData.relatedQuestions?.length > 0 && (
              <div className={styles.questionList}>
                {termData.relatedQuestions.slice(0, 3).map((question) => (
                  <span key={question}>{question}</span>
                ))}
              </div>
            )}
            <button className={styles.aiAskBtn}>
              {termData.askEntry} <ExternalLink size={16} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
