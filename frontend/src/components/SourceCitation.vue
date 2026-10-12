<script setup>
/**
 * 回答来源标注卡片。
 * 展示引用的制度文档、页码与原文片段。
 */
defineProps({
  sources: {
    type: Array,
    default: () => []
    // [{ documentId, title, page, snippet, score }]
  }
})

const emit = defineEmits(['locate'])
</script>

<template>
  <div v-if="sources.length" class="source-citation">
    <div class="source-citation__title">
      <el-icon><Collection /></el-icon>
      <span>参考来源（{{ sources.length }}）</span>
    </div>

    <div class="source-citation__list">
      <div
        v-for="(item, index) in sources"
        :key="index"
        class="source-item"
        @click="emit('locate', item)"
      >
        <div class="source-item__head">
          <span class="source-item__name">
            <el-icon><Document /></el-icon>
            {{ item.title || '未知文档' }}
          </span>
          <el-tag size="small" type="primary" effect="plain">
            第 {{ item.page ?? '-' }} 页
          </el-tag>
        </div>
        <p v-if="item.snippet" class="source-item__snippet">{{ item.snippet }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.source-citation {
  margin-top: 12px;
  padding: 12px;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius);
  background: #fafbfc;

  &__title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--app-text-secondary);
    margin-bottom: 8px;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
}

.source-item {
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--app-primary);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  &__name {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 500;
  }

  &__snippet {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--app-text-regular);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}
</style>
