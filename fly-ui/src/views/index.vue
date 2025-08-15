<template>
  <div class="app-container home">
    <!-- 上半部分：显示 bgFooter.png，背景为白色 -->
    <el-row :gutter="0">
      <el-col :span="24">
        <div class="upper-section">
          <el-image :src="footerImageIndex" :fit="fit"></el-image>
        </div>
      </el-col>
    </el-row>

    <!-- 下半部分：使用 bgIndex.png 作为背景图，铺满整个区域 -->
    <el-row :gutter="0">
      <el-col :span="24">
        <div class="lower-section" :style="{ backgroundImage: 'url(' + bgImageIndex + ')' }"></div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: "Index",
  data() {
    return {
      bgImageIndex: require("@/assets/images/bgIndex.png"),
      footerImageIndex: require("@/assets/images/bgFooter.png"),
      fit: "fill", // 保持原值，用于上部图片
      // 版本号
      version: "3.8.5"
    };
  },
  methods: {
    goTarget(href) {
      window.open(href, "_blank");
    }
  }
};
</script>

<style scoped lang="scss">
.home {
  .upper-section {
    height: 300px; /* 固定高度 */
    background-color: white; /* 改为白色 */
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .lower-section {
    height: calc(100vh - 300px); /* 自动计算剩余高度 */
    background-size: cover; /* 等比放大，铺满容器 */
    background-position: center; /* 居中对齐 */
    background-repeat: no-repeat; /* 不重复 */
    display: flex;
    justify-content: center;
    align-items: center;
  }

  font-family: "open sans", "Helvetica Neue", Helvetica, Arial, sans-serif;
  font-size: 13px;
  color: #676a6c;
  overflow-x: hidden;
}
</style>