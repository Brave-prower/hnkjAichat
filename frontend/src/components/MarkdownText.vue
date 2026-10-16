<script setup>
/**
 * Markdown 渲染组件。
 *
 * 用于渲染 AI 回答中的 Markdown（标题、列表、加粗、行内代码、代码块、
 * 引用、表格、链接等）。渲染流程：
 *
 *   Markdown 源码
 *     → 预处理（把 [第X页] 页码引用包成 inline span）
 *     → marked 解析为 HTML
 *     → DOMPurify 消毒（防 XSS，AI 输出不可信）
 *     → v-html 渲染
 *
 * 说明：marked 自 v5 起不再内置消毒能力，因此必须配合 DOMPurify。
 */
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  /** Markdown 原文 */
  content: { type: String, default: '' },
  /** 是否把 [第X页] 渲染成页码标签 */
  citePages: { type: Boolean, default: true }
})

// GFM + 单个换行也换行（聊天场景更符合直觉）
marked.setOptions({ gfm: true, breaks: true })

// ---------- 给所有外链补上安全属性（模块级只注册一次） ----------
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('href')) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

/**
 * 把 [第16页] / [第 16 页] / [第16页, 第4页] 这类页码引用渲染成 inline 标签。
 *
 * 注意：必须在 Markdown 解析「之后」处理，并跳过 <pre>/<code> 区域，
 * 否则代码块里的示例文本会被破坏。
 */
const PAGE_REF_RE = /\[第\s*\d+\s*页(?:\s*[,，]\s*第?\s*\d+\s*页)*\]/g

function decoratePageRefs(html) {
  // 用捕获组切分，奇数下标即为代码片段，原样保留
  return html
    .split(/(<pre[\s\S]*?<\/pre>|<code[\s\S]*?<\/code>)/gi)
    .map((segment, index) => {
      if (index % 2 === 1) return segment
      return segment.replace(
        PAGE_REF_RE,
        (match) => `<span class="md-page-ref">${match}</span>`
      )
    })
    .join('')
}

const html = computed(() => {
  const raw = props.content || ''
  if (!raw.trim()) return ''

  let parsed = ''
  try {
    parsed = marked.parse(raw)
  } catch {
    // 解析异常时退化为转义后的纯文本，保证不白屏
    parsed = `<p>${escapeHtml(raw)}</p>`
  }

  const clean = DOMPurify.sanitize(parsed, { USE_PROFILES: { html: true } })

  // 消毒之后再注入页码标签：内容可控，且不会被消毒器剥离
  return props.citePages ? decoratePageRefs(clean) : clean
})

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div class="markdown-body" v-html="html" />
</template>

<style scoped>
/* 页码引用标签：[第16页] */
.markdown-body :deep(.md-page-ref) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  border: 1px solid var(--app-primary-border);
  border-radius: var(--app-radius-pill);
  white-space: nowrap;
}
</style>
