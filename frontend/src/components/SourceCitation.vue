<script setup>
/**
 * 回答来源标注卡片（可展开）。
 *
 * - 默认收起：只显示文档名、页码、相似度与 2 行摘要
 * - 点击卡片任意位置：展开 / 收起，展开后显示完整原文片段
 * - 顶部提供「全部展开 / 全部收起」，来源较多时省事
 */
import { computed, ref, watch } from 'vue'

const props = defineProps({
  sources: {
    type: Array,
    default: () => []
    // [{ documentId, title, page, snippet, score }]
  }
})

const emit = defineEmits(['locate'])

/** 已展开项的索引集合 */
const expanded = ref(new Set())

// 来源更新（新一轮问答）时重置展开态
watch(
  () => props.sources,
  () => {
    expanded.value = new Set()
  }
)

const allExpanded = computed(
  () => props.sources.length > 0 && expanded.value.size === props.sources.length
)

function toggle(index) {
  const next = new Set(expanded.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expanded.value = next
}

function toggleAll() {
  expanded.value = allExpanded.value
    ? new Set()
    : new Set(props.sources.map((_, i) => i))
}

/** 相似度转百分比（后端为 0~1） */
function scoreText(score) {
  if (score === null || score === undefined || Number.isNaN(Number(score))) return ''
  const pct = Number(score) <= 1 ? Number(score) * 100 : Number(score)
  return `${Math.round(pct)}%`
}
</script>

<template>
  <div v-if="sources.length" class="source-citation">
    <!-- 头部 -->
    <div class="source-citation__head">
      <div class="source-citation__label">
        <el-icon><Collection /></el-icon>
        <span>参考来源（{{ sources.length }}）</span>
      </div>
      <button type="button" class="source-citation__toggle-all" @click="toggleAll">
        {{ allExpanded ? '全部收起' : '全部展开' }}
        <el-icon class="source-citation__chevron" :class="{ 'is-open': allExpanded }">
          <ArrowDown />
        </el-icon>
      </button>
    </div>

    <!-- 列表 -->
    <div class="source-citation__list">
      <div
        v-for="(item, index) in sources"
        :key="`${item.documentId}-${item.page}-${index}`"
        class="source-item"
        :class="{ 'is-open': expanded.has(index) }"
      >
        <div
          class="source-item__head"
          role="button"
          tabindex="0"
          :aria-expanded="expanded.has(index)"
          @click="toggle(index)"
          @keydown.enter.prevent="toggle(index)"
          @keydown.space.prevent="toggle(index)"
        >
          <el-icon class="source-item__chevron" :class="{ 'is-open': expanded.has(index) }">
            <ArrowRight />
          </el-icon>

          <span class="source-item__index">{{ index + 1 }}</span>

          <span class="source-item__name" :title="item.title || '未知文档'">
            {{ item.title || '未知文档' }}
          </span>

          <span class="source-item__meta">
            <el-tag size="small" type="primary" effect="plain">
              第 {{ item.page ?? '-' }} 页
            </el-tag>
            <el-tag v-if="scoreText(item.score)" size="small" type="info" effect="plain">
              相似度 {{ scoreText(item.score) }}
            </el-tag>
          </span>
        </div>

        <!-- 内容区：收起时摘要 2 行，展开后显示完整原文 -->
        <div class="source-item__body">
          <div class="source-item__body-inner">
            <p class="source-item__snippet" :class="{ 'is-clamped': !expanded.has(index) }">
              {{ item.snippet || '（该来源没有可展示的原文片段）' }}
            </p>
            <div v-if="expanded.has(index)" class="source-item__footer">
              <span class="source-item__hint">
                <el-icon><Reading /></el-icon>
                以上为命中的制度原文片段
              </span>
              <el-button
                v-if="item.documentId"
                link
                type="primary"
                size="small"
                @click.stop="emit('locate', item)"
              >
                在文档库中查看
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.source-citation {
  margin-top: 10px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12.5px;
    font-weight: 600;
    color: var(--app-text-secondary);
  }

  &__toggle-all {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 2px 8px;
    font-size: 12px;
    font-family: inherit;
    color: var(--app-primary);
    background: transparent;
    border: none;
    border-radius: var(--app-radius-pill);
    cursor: pointer;
    transition: background var(--app-transition);

    &:hover {
      background: var(--app-primary-soft);
    }
  }

  &__chevron {
    transition: transform var(--app-transition);

    &.is-open {
      transform: rotate(180deg);
    }
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
}

.source-item {
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  overflow: hidden;
  transition: border-color var(--app-transition), box-shadow var(--app-transition);

  &:hover {
    border-color: var(--app-primary-border);
  }

  &.is-open {
    border-color: var(--app-primary-border);
    box-shadow: var(--app-shadow-xs);
  }

  /* ---------- 标题行（点击展开） ---------- */
  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    cursor: pointer;
    user-select: none;
    outline: none;

    &:hover {
      background: var(--app-bg-subtle);
    }

    &:focus-visible {
      box-shadow: inset 0 0 0 2px var(--app-primary-ring);
    }
  }

  &__chevron {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--app-text-secondary);
    transition: transform var(--app-transition);

    &.is-open {
      transform: rotate(90deg);
      color: var(--app-primary);
    }
  }

  &__index {
    flex-shrink: 0;
    width: 18px;
    height: 18px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 600;
    color: var(--app-primary);
    background: var(--app-primary-soft);
    border-radius: 50%;
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    font-weight: 500;
    color: var(--app-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__meta {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }

  /* ---------- 内容区（grid 0fr→1fr 实现自动高度展开动画） ---------- */
  &__body {
    display: grid;
    grid-template-rows: 0fr;
    transition: grid-template-rows var(--app-transition);
  }

  &.is-open &__body {
    grid-template-rows: 1fr;
  }

  &__body-inner {
    min-height: 0;
    overflow: hidden;
  }

  &__snippet {
    margin: 0;
    padding: 0 10px 10px 36px;
    font-size: 12.5px;
    line-height: 1.75;
    color: var(--app-text-regular);
    white-space: pre-wrap;
    word-break: break-word;

    /* 收起时只留 2 行 */
    &.is-clamped {
      display: -webkit-box;
      -webkit-line-clamp: 2;
      line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 0 10px 10px 36px;
  }

  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11.5px;
    color: var(--app-text-secondary);
  }
}
</style>
