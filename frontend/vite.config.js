import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vite';

// 构建产物直接输出到后端静态资源目录，由 Quarkus 一并托管
export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/META-INF/resources',
    emptyOutDir: true
  }
});
