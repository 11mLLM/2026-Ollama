import React, { useEffect, useMemo, useState } from 'react';
import { useWorkspace } from '../hooks/useWorkspace';
import { loadLearningTerms, loadStockOptions } from '../services/apiClient';
import ImmersiveChart from '../components/ImmersiveChart';
import DeepDiveLearningSheet from '../components/DeepDiveLearningSheet';
import styles from './App.module.css';

function App() {
  const {
    interval,
    data,
    loading,
    error,
    changeStock,
    changeInterval,
    refreshAi
  } = useWorkspace();

  const [learningMode, setLearningMode] = useState(false);
  const [learningSheetOpen, setLearningSheetOpen] = useState(false);
  const [termData, setTermData] = useState(null);
  const [stockOptions, setStockOptions] = useState([]);

  const chartContext = useMemo(() => {
    if (!data) return null;
    const latest = data.chart?.rows?.[data.chart.rows.length - 1] || null;
    return {
      stockName: data.stock?.name,
      code: data.stock?.code,
      asOf: data.asOf,
      interval,
      latestClose: latest?.close,
      latestVolume: latest?.volume,
      ma20: data.indicatorSnapshot?.movingAverages?.ma20,
      supportLevel: data.indicatorSnapshot?.supportLevel,
      resistanceLevel: data.indicatorSnapshot?.resistanceLevel,
      priceVsMa20: data.indicatorSnapshot?.priceVsMa20,
      summary: data.currentDecisionSummary?.summary,
      buyCondition: data.currentDecisionSummary?.buyReviewCondition,
      riskCondition: data.currentDecisionSummary?.riskCondition
    };
  }, [data, interval]);

  useEffect(() => {
    let mounted = true;
    loadStockOptions()
      .then((options) => {
        if (mounted) setStockOptions(options);
      })
      .catch(() => {
        if (mounted) setStockOptions([]);
      });
    return () => { mounted = false; };
  }, []);

  const handleToggleLearningMode = () => {
    if (!learningMode) {
      setLearningMode(true);
      // Just toggle the mode, don't open the sheet automatically unless a term is selected
    } else {
      setLearningMode(false);
      setLearningSheetOpen(false);
    }
  };

  const handleSelectTerm = async (termName) => {
    try {
      const terms = await loadLearningTerms();
      const found = terms.find(t => t.term === termName || t.title === termName);
      if (found) {
        setTermData(found);
      } else {
        // Create a fallback object if not found
        setTermData({
          term: termName,
          coreSummary: '용어 설명을 준비 중입니다.',
          longExplanation: '학습 콘텐츠가 아직 등록되지 않았습니다.',
          chartUsage: '차트에서 어떻게 보는지 업데이트 예정입니다.',
          commonMisunderstanding: '알려진 오해가 없습니다.',
          scenario: '실전 시나리오는 준비 중입니다.',
          askEntry: `${termName}에 대해 질문하기`
        });
      }
      setLearningMode(true);
      setLearningSheetOpen(true);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className={styles.appContainer}>
      {/* Background Hero Layer */}
      <div className={styles.chartLayer}>
        {data && (
          <ImmersiveChart 
            stock={data.stock} 
            chart={data.chart} 
            zones={data.zones} 
            events={data.events}
            ai={data.ai}
            indicatorSnapshot={data.indicatorSnapshot}
            decisionSummary={data.currentDecisionSummary}
            interval={interval}
            onChangeInterval={changeInterval}
            stockOptions={stockOptions}
            onChangeStock={changeStock}
            learningMode={learningMode}
            onTermClick={handleSelectTerm}
            onRefreshAi={refreshAi}
          />
        )}
      </div>

      {/* Loading Overlay */}
      {loading && !data && (
        <div className={styles.loadingOverlay}>
          <div className={styles.spinnerBox}>
            <div className={styles.spinner} />
            <span className={styles.loadingText}>데이터를 불러오고 있습니다</span>
          </div>
        </div>
      )}

      {/* Error / Fallback Toast */}
      {error && (
        <div className={styles.errorToast}>
          {error}
        </div>
      )}

      <DeepDiveLearningSheet 
        isOpen={learningSheetOpen} 
        onClose={() => setLearningSheetOpen(false)} 
        termData={termData}
        chartContext={chartContext}
      />
    </div>
  );
}

export default App;
