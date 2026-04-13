import { resolve } from 'path'

export default {
  server: {
    port: 5173
  },
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        encyclopedia: resolve(__dirname, 'encyclopedia.html'),
        query: resolve(__dirname, 'query.html')
      }
    }
  }
}
