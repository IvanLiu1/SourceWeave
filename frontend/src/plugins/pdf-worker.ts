/**
 * pdf.js Web Worker 入口。
 *
 * pdf.js 的官方 worker 文件同样调用 `Map.prototype.getOrInsertComputed`，而 worker 是
 * 独立 realm，主线程的 polyfill 覆盖不到。这里先安装 polyfill，再加载官方 worker，
 * 保证 worker 线程内的 Map 也具备该方法。
 *
 * 组件通过 `import workerUrl from '@/plugins/pdf-worker?worker&url'` 拿到本入口打包后的
 * URL，赋给 GlobalWorkerOptions.workerSrc，替代直接使用 pdfjs-dist 自带 worker。
 */
import './polyfills';
import 'pdfjs-dist/build/pdf.worker.min.mjs';
