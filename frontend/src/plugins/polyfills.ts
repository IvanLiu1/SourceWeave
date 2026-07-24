/**
 * pdfjs-dist 5.x 直接调用 `Map.prototype.getOrInsertComputed`(TC39 "upsert" 提案，
 * 部分浏览器尚未原生实现）。缺失时 PDF 渲染会抛
 * `getOrInsertComputed is not a function`。
 *
 * 主线程与 pdf.js 的 Web Worker 是两个独立 realm，各自的 Map.prototype 互不影响，
 * 因此该文件同时被主线程入口(main.ts)和 worker 入口(pdf-worker.ts)引入。
 * 仅在原生缺失时安装，浏览器已支持则为空操作。
 */
if (typeof (Map.prototype as { getOrInsertComputed?: unknown }).getOrInsertComputed !== 'function') {
  Object.defineProperty(Map.prototype, 'getOrInsertComputed', {
    value<K, V>(this: Map<K, V>, key: K, callback: (key: K) => V): V {
      if (!this.has(key)) {
        this.set(key, callback(key));
      }
      return this.get(key) as V;
    },
    writable: true,
    configurable: true,
    enumerable: false
  });
}
