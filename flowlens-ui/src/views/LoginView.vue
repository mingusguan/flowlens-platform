<script setup lang="ts">import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';
import { ArrowRight, LockKeyhole, UserRound, Eye, EyeOff } from 'lucide-vue-next';
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const loading = ref(false);
const showPassword = ref(false);
const form = reactive({
 username: 'admin',
 password: 'admin123'
});
const submit = async () => {
 loading.value = true;
 try {
 await auth.login(form);
 ElMessage.success('欢迎回来');
 const redirectParam = typeof route.query.redirect === 'string' ? route.query.redirect : '';
 const redirect = redirectParam.startsWith('/') && !redirectParam.startsWith('//') ? redirectParam : '/dashboard';
 router.replace(redirect);
 }
 finally {
 loading.value = false;
 }
};
</script>

<template>
  <div class="login-container">
    <div class="login-bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-orbs">
        <div class="orb orb-1"></div>
        <div class="orb orb-2"></div>
        <div class="orb orb-3"></div>
      </div>
    </div>
    
    <div class="login-wrapper">
      <div class="login-visual">
        <div class="brand-section">
          <div class="brand-logo">
            <div class="logo-icon">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L2 7l10 5 10-5-10-5z" fill="currentColor" opacity="0.8"/>
                <path d="M2 17l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <path d="M2 12l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="logo-text">
              <h1>FlowLens</h1>
              <p>流量棱镜</p>
            </div>
          </div>
          
          <div class="stats-card">
            <div class="stat-item">
              <div class="stat-icon pulse">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
                </svg>
              </div>
              <div class="stat-content">
                <span class="stat-label">今日播放峰值</span>
                <strong class="stat-value">386.2w</strong>
                <span class="stat-trend up">+18.2%</span>
              </div>
            </div>
          </div>
          
          <div class="floating-cards">
            <div class="float-card card-1">
              <div class="card-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="2" y="3" width="15" height="13"/>
                  <polyline points="17 8 22 8 22 18 12 18 12 8 17 8"/>
                </svg>
              </div>
              <span class="card-label">爆量视频</span>
              <strong class="card-value">42</strong>
            </div>
            
            <div class="float-card card-2">
              <div class="card-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                  <path d="M13.73 21a9 9 0 0 1-8.73-11"/>
                </svg>
              </div>
              <span class="card-label">异常告警</span>
              <strong class="card-value">7</strong>
            </div>
          </div>
        </div>
      </div>
      
      <div class="login-panel">
        <div class="panel-inner">
          <div class="panel-header">
            <span class="panel-eyebrow">Traffic Intelligence Platform</span>
            <h2>欢迎回来</h2>
            <p class="panel-desc">登录您的账号，开始监控流量数据</p>
          </div>
          
          <form class="login-form" @submit.prevent="submit">
            <div class="form-group">
              <label class="form-label">用户名</label>
              <div class="form-field">
                <UserRound :size="18" class="field-icon"/>
                <input 
                  v-model="form.username" 
                  type="text" 
                  class="form-input" 
                  placeholder="请输入用户名"
                  autofocus
                />
              </div>
            </div>
            
            <div class="form-group">
              <label class="form-label">密码</label>
              <div class="form-field">
                <LockKeyhole :size="18" class="field-icon"/>
                <input 
                  v-model="form.password" 
                  :type="showPassword ? 'text' : 'password'" 
                  class="form-input" 
                  placeholder="请输入密码"
                />
                <button type="button" class="field-toggle" @click="showPassword = !showPassword">
                  <Eye v-if="showPassword" :size="18"/>
                  <EyeOff v-else :size="18"/>
                </button>
              </div>
            </div>
            
            <div class="form-options">
              <label class="checkbox">
                <input type="checkbox" />
                <span class="checkbox-icon"></span>
                记住我
              </label>
              <a href="#" class="forgot-link">忘记密码？</a>
            </div>
            
            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading" class="btn-loader"></span>
              <span>进入平台</span>
              <ArrowRight :size="18" />
            </button>
          </form>
          
          <div class="panel-footer">
            <span>还没有账号？</span>
            <a href="#" class="register-link">联系管理员</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
