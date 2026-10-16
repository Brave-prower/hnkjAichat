<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// 菜单项由路由表推导
const menus = [
  { path: '/home', title: '首页', icon: 'HomeFilled' },
  { path: '/qa', title: '智能问答', icon: 'ChatDotRound' },
  { path: '/document', title: '规章制度库', icon: 'Document' },
  { path: '/upload', title: '上传规章制度', icon: 'UploadFilled' }
]

const activeMenu = computed(() => route.path)

function handleSelect(path) {
  if (path !== route.path) router.push(path)
}
</script>

<template>
  <el-container class="basic-layout">
    <!-- 侧边栏 -->
    <el-aside width="var(--app-sider-width)" class="layout-aside">
      <div class="logo">
        <el-icon :size="22"><School /></el-icon>
        <span class="logo-text">校园问答系统</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="layout-menu"
        background-color="transparent"
        @select="handleSelect"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-title">
          {{ route.meta?.title || '校园问答系统' }}
        </div>
        <div class="header-right">
          <el-tag type="info" effect="plain" size="small">湖南科技职业学院</el-tag>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <div class="layout-container">
          <router-view v-slot="{ Component }">
            <keep-alive :include="['Home']">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.basic-layout {
  height: 100vh;
}

.layout-aside {
  background: #ffffff;
  border-right: 1px solid var(--app-border);
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
}

.logo {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: var(--app-primary);
  border-bottom: 1px solid var(--app-border);

  .logo-text {
    font-size: 16px;
    font-weight: 600;
    color: var(--app-text-primary);
  }
}

.layout-menu {
  border-right: none;
  flex: 1;
  padding-top: 8px;

  /* 选中项：浅蓝底 + 主色文字，弱化边框感 */
  :deep(.el-menu-item) {
    height: 42px;
    margin: 2px 10px;
    border-radius: var(--app-radius-sm);
    font-size: 14px;
    color: var(--app-text-regular);
    transition: background var(--app-transition), color var(--app-transition);

    &:hover {
      background: var(--app-bg-subtle);
      color: var(--app-text-primary);
    }

    &.is-active {
      background: var(--app-primary-soft);
      color: var(--app-primary);
      font-weight: 600;
    }
  }
}

.layout-header {
  height: var(--app-header-height);
  background: #ffffff;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-title {
    font-size: 16px;
    font-weight: 600;
  }
}

.layout-main {
  padding: 20px;
  overflow-y: auto;
  background: var(--app-bg);
}

/* 内容居中，最大宽度 1200px */
.layout-container {
  width: 100%;
  max-width: var(--app-container);
  height: 100%;
  margin: 0 auto;
}
</style>
