/**
 * pdfjs-dist 5.x 使用了若干较新的 JS 特性但不自带 polyfill，在尚未实现这些特性的浏览器
 * （典型是较老版本 Safari）上会导致 PDF 预览崩溃。此文件集中做最小 polyfill，仅在原生
 * 缺失时安装，已支持的浏览器为空操作。
 *
 * 主线程与 pdf.js 的 Web Worker 是两个独立 realm，prototype 互不影响，因此该文件同时被
 * 主线程入口(main.ts)和 worker 入口(pdf-worker.ts)引入。
 */

// 1) Map.prototype.getOrInsertComputed（TC39 "upsert" 提案）
// pdf.js 的 MessageHandler 直接调用，缺失时渲染抛 "getOrInsertComputed is not a function"。
if (typeof (Map.prototype as { getOrInsertComputed?: unknown }).getOrInsertComputed !== 'function') {
  // eslint-disable-next-line no-extend-native -- pdf.js requires this standards-track polyfill in older browsers.
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

// 2) ReadableStream 异步迭代器（Symbol.asyncIterator / values）
// pdf.js 用 `for await (const value of readableStream)` 消费 getTextContent 的文本流，
// 缺失时文本层渲染抛 "undefined is not a function (near '...value of readableStream...')"。
if (
  typeof ReadableStream !== 'undefined' &&
  typeof (ReadableStream.prototype as { [Symbol.asyncIterator]?: unknown })[Symbol.asyncIterator] !== 'function'
) {
  const asyncIterator = function asyncIterator(
    this: ReadableStream,
    options?: { preventCancel?: boolean }
  ) {
    const preventCancel = options?.preventCancel ?? false;
    const reader = this.getReader();
    return {
      async next() {
        try {
          const result = await reader.read();
          if (result.done) {
            reader.releaseLock();
          }
          return result;
        } catch (error) {
          reader.releaseLock();
          throw error;
        }
      },
      async return(value?: unknown) {
        if (!preventCancel) {
          const cancelPromise = reader.cancel(value);
          reader.releaseLock();
          await cancelPromise;
        } else {
          reader.releaseLock();
        }
        return { done: true, value };
      },
      [Symbol.asyncIterator]() {
        return this;
      }
    };
  };

  Object.defineProperty(ReadableStream.prototype, Symbol.asyncIterator, {
    value: asyncIterator,
    writable: true,
    configurable: true,
    enumerable: false
  });
  Object.defineProperty(ReadableStream.prototype, 'values', {
    value: asyncIterator,
    writable: true,
    configurable: true,
    enumerable: false
  });
}
