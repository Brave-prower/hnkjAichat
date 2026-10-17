<script setup>
/**
 * 检索透明化面板：「为什么这样答」。
 *
 * 展示一次问答的完整检索与决策过程：
 *   - 摘要条：检索模式徽标 / 耗时 / 命中数 / 闸门结论
 *   - 候选列表：每条片段的分数条（阈值刻度线 + 命中/未达阈值徽标）
 *   - 熔断提示：Embedding 不可用时的降级说明
 *
 * 数据来自后端 SSE trace 事件 / 同步接口 trace 字段，前端零造假：
 * 分数、阈值、闸门全部是后端真实计算结果。
 */
import { computed, ref } from 'vue'

const props = defineProps({
  trace: { type: Object, required: true },
  /** 默认展开候选数（其余折叠在「展开全部」里） */
  previewCount: { type: Number, default: 6 }
})

/** 检索模式 → 徽标文案与样式类 */
const modeBadge = computed(() => {
  switch (props.trace?.mode) {
    case 'semantic':
      return { text: '语义检索', cls: 'badge--semantic' }
    case 'keyword-fallback':
      return { text: '关键词兜底', cls: 'badge--keyword' }
    case 'keyword':
      return { text: '关键词检索', cls: 'badge--keyword' }
    default:
      return { text: props.trace?.mode || '未知', cls: 'badge--keyword' }
  }
})

/** 闸门结论 → 徽标 */
const gateBadge = computed(() => {
  const gate = props.trace?.gate
  if (gate === 'PASS') {
    return { text: '闸门放行', cls: 'badge--pass' }
  }
  return { text: '闸门拒绝', cls: 'badge--reject' }
})

const candidates = computed(() => props.trace?.candidates || [])
const hitCount = computed(() => candidates.value.filter((c) => c.hit).length)
const shown = computed(() => candidates.value.slice(0, props.previewCount))
const hasMore = computed(() => candidates.value.length > props.previewCount)

/** 展开全部/收起 */
const expanded = ref(false)
const visible = computed(() => (expanded.value ? candidates.value : shown.value))

const scorePct = (score) => `${Math.max(0, Math.min(1, score)) * 100}%`
const thresholdPct = computed(() => scorePct(props.trace?.threshold ?? 0))
const scoreText = (score) => score.toFixed(4)
</script>

<template>
  <div class="trace-panel">
    <!-- 熔断提示 -->
    <div v-if="trace.embeddingBroken" class="trace-panel__breaker">
      <el-icon><WarningFilled /></el-icon>
      Embedding 服务暂不可用，本次为关键词检索（自动恢复中）
    </div>

    <!-- 摘要条 -->
    <div class="trace-panel__summary">
      <span class="badge" :class="modeBadge.cls">{{ modeBadge.text }}</span>
      <span class="summary__item">耗时 {{ trace.costMs ?? '-' }} ms</span>
      <span class="summary__item">
        命中 <b>{{ hitCount }}</b> / 候选 <b>{{ candidates.length }}</b>
      </span>
      <span class="badge" :class="gateBadge.cls">{{ gateBadge.text }}</span>
    </div>

    <!-- 闸门解释 -->
    <p class="trace-panel__reason">{{ trace.gateReason }}</p>

    <!-- 候选打分明细 -->
    <div class="trace-panel__list">
      <div
        v-for="c in visible"
        :key="c.chunkId"
        class="cand"
        :class="{ 'cand--hit': c.hit }"
      >
        <div class="cand__head">
          <span class="cand__title">{{ c.title }}</span>
          <span v-if="c.pageNumber != null" class="cand__page">第 {{ c.pageNumber }} 页</span>
          <span class="cand__score">{{ scoreText(c.score) }}</span>
          <span class="cand__mark" :class="c.hit ? 'cand__mark--hit' : 'cand__mark--miss'">
            {{ c.hit ? '已命中' : '未达阈值' }}
          </span>
        </div>

        <!-- 分数条：主色=实际得分，刻度线=阈值位置 -->
        <div class="cand__bar">
          <div class="cand__bar-fill" :style="{ width: scorePct(c.score) }"></div>
          <div class="cand__bar-threshold" :style="{ left: thresholdPct }"></div>
        </div>

        <p class="cand__snippet">{{ c.snippet }}</p>
      </div>
    </div>

    <button
      v-if="hasMore"
      type="button"
      class="trace-panel__more"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起候选' : `展开全部 ${candidates.length} 条候选` }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.trace-panel {
  margin-top: 12px;
  padding: 14px;
  font-size: 13px;
  background: var(--app-bg-subtle);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);

  /* ---------- 熔断提示 ---------- */
  &__breaker {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 10px;
    padding: 8px 12px;
    color: #8a5a12;
    background: #fdf3e0;
    border: 1px solid #f0dcb4;
    border-radius: var(--app-radius);
  }

  /* ---------- 摘要条 ---------- */
  &__summary {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__reason {
    margin: 8px 0 0;
    color: var(--app-text-secondary);
    line-height: 1.6;
  }

  /* ---------- 候选列表 ---------- */
  &__list {
    display: grid;
    gap: 10px;
    margin-top: 12px;
  }

  &__more {
    margin-top: 10px;
    padding: 4px 0;
    width: 100%;
    font-family: inherit;
    font-size: 12.5px;
    color: var(--app-primary);
    background: none;
    border: none;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }
}

/* ---------- 徽标 ---------- */
.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: 12px;
  border-radius: var(--app-radius-pill);

  &--semantic {
    color: var(--app-primary);
    background: var(--app-primary-soft);
    border: 1px solid var(--app-primary-border);
  }

  &--keyword {
    color: #8a5a12;
    background: #fdf3e0;
    border: 1px solid #f0dcb4;
  }

  &--pass {
    color: #166534;
    background: #e8f7ee;
    border: 1px solid #bfe6cd;
  }

  &--reject {
    color: var(--app-danger);
    background: var(--app-danger-soft);
    border: 1px solid #f3cccc;
  }
}

/* ---------- 候选卡片 ---------- */
.cand {
  padding: 10px 12px;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);

  &--hit {
    border-color: var(--app-primary-border);
  }

  &__head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__title {
    font-weight: 600;
    color: var(--app-text-primary);
  }

  &__page {
    font-size: 12px;
    color: var(--app-text-secondary);
  }

  &__score {
    margin-left: auto;
    font-variant-numeric: tabular-nums;
    color: var(--app-text-regular);
  }

  &__mark {
    padding: 1px 8px;
    font-size: 11.5px;
    border-radius: var(--app-radius-pill);

    &--hit {
      color: var(--app-primary);
      background: var(--app-primary-soft);
    }

    &--miss {
      color: var(--app-text-placeholder);
      background: var(--app-bg-subtle);
    }
  }

  /* 分数条：灰底 + 主色填充 + 阈值刻度线 */
  &__bar {
    position: relative;
    height: 8px;
    margin-top: 8px;
    background: #e8ebf1;
    border-radius: var(--app-radius-pill);
    overflow: visible;

    &-fill {
      height: 100%;
      background: linear-gradient(90deg, #60a5fa 0%, var(--app-primary) 100%);
      border-radius: var(--app-radius-pill);
      transition: width 0.4s ease;
    }

    /* 阈值刻度线：竖线略微超出条体，一眼看到「线在哪里」 */
    &-threshold {
      position: absolute;
      top: -3px;
      bottom: -3px;
      width: 2px;
      background: var(--app-text-secondary);
      opacity: 0.65;

      &::after {
        content: '阈值';
        position: absolute;
        top: -16px;
        left: -10px;
        font-size: 10px;
        color: var(--app-text-placeholder);
        white-space: nowrap;
      }
    }
  }

  &__snippet {
    margin: 8px 0 0;
    font-size: 12.5px;
    line-height: 1.6;
    color: var(--app-text-secondary);
  }
}
</style>
