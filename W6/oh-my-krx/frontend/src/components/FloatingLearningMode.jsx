import React from 'react';
import { BookOpen } from 'lucide-react';
import clsx from 'clsx';
import styles from './FloatingLearningMode.module.css';

export default function FloatingLearningMode({ isActive, onToggle }) {
  return (
    <button 
      className={clsx(styles.button, isActive && styles.active)}
      onClick={onToggle}
      aria-label="Toggle Learning Mode"
    >
      <div className={styles.iconWrapper}>
        <BookOpen size={20} />
      </div>
      <span className={styles.label}>
        {isActive ? '학습 모드 켜짐' : '초보자 설명'}
      </span>
    </button>
  );
}
