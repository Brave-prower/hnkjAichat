<script setup>
import { useRouter } from 'vue-router'

defineOptions({ name: 'Home' })

const router = useRouter()

const features = [
  {
    title: '智能问答',
    desc: '用自然语言提问，AI 依据学校规章制度作答，并标注来源页码。',
    icon: 'ChatDotRound',
    path: '/qa'
  },
  {
    title: '规章制度库',
    desc: '上传与维护学校规章制度 PDF，系统自动解析、切分并建立索引。',
    icon: 'Document',
    path: '/document'
  }
]

const examples = [
  '学生请销假需要办理哪些手续？',
  '国家奖学金的评审条件是什么？',
  '宿舍用电有哪些禁止性规定？',
  '考试违纪会如何处理？'
]

function go(path) {
  router.push(path)
}
</script>

<template>
  <div class="home-page">
    <!-- 欢迎区 -->
    <div class="hero">
      <h1 class="hero__title">湖南科技职业学院 · 校园问答系统</h1>
      <p class="hero__subtitle">
        基于学校规章制度文档的智能问答，每个回答都可追溯原文与页码
      </p>
      <div class="hero__actions">
        <el-button type="primary" size="large" @click="go('/qa')">
          <el-icon><ChatDotRound /></el-icon>
          开始提问
        </el-button>
        <el-button size="large" class="hero__upload" @click="go('/document')">
          <el-icon><UploadFilled /></el-icon>
          上传规章制度 PDF
        </el-button>
      </div>
    </div>

    <!-- 功能入口 -->
    <el-row :gutter="16" class="feature-row">
      <el-col v-for="item in features" :key="item.path" :xs="24" :sm="12">
        <div class="feature-card" @click="go(item.path)">
          <el-icon class="feature-card__icon" :size="28">
            <component :is="item.icon" />
          </el-icon>
          <div class="feature-card__body">
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </div>
          <el-icon class="feature-card__arrow"><ArrowRight /></el-icon>
        </div>
      </el-col>
    </el-row>

    <!-- 示例问题 -->
    <div class="card-panel">
      <PageHeader title="试试这样问" description="点击即可跳转到问答页面" />
      <div class="example-list">
        <el-tag
          v-for="q in examples"
          :key="q"
          class="example-tag"
          effect="plain"
          size="large"
          @click="go('/qa')"
        >
          {{ q }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  max-width: var(--app-container);
  margin: 0 auto;
}

.hero {
  padding: 40px 32px;
  border-radius: var(--app-radius-lg);
  background: linear-gradient(135deg, #2563eb 0%, #4c8dff 100%);
  box-shadow: var(--app-shadow-primary);
  color: #ffffff;
  margin-bottom: 18px;

  &__title {
    margin: 0;
    font-size: 25px;
    font-weight: 700;
    letter-spacing: 0.5px;
  }

  &__subtitle {
    margin: 10px 0 22px;
    font-size: 14px;
    opacity: 0.92;
  }

  &__actions {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }
}

.hero__upload.el-button {
  background: #ffffff;
  border-color: #ffffff;
  color: var(--app-primary);
  font-weight: 500;

  &:hover,
  &:focus {
    background: #eef4ff;
    border-color: #eef4ff;
    color: var(--app-primary);
  }
}

.feature-row {
  margin-bottom: 4px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  margin-bottom: 16px;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--app-primary);
    box-shadow: 0 4px 12px rgba(31, 111, 235, 0.1);
    transform: translateY(-2px);
  }

  &__icon {
    color: var(--app-primary);
    flex-shrink: 0;
  }

  &__body {
    flex: 1;

    h3 {
      margin: 0 0 4px;
      font-size: 15px;
    }

    p {
      margin: 0;
      font-size: 13px;
      color: var(--app-text-secondary);
      line-height: 1.6;
    }
  }

  &__arrow {
    color: var(--app-text-secondary);
    flex-shrink: 0;
  }
}

.example-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.example-tag {
  cursor: pointer;
}
</style>
