import { fileURLToPath, URL } from 'node:url';

import { PrimeVueResolver } from '@primevue/auto-import-resolver';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import VueDevTools from 'vite-plugin-vue-devtools';
import { defineConfig } from 'vite';

// https://vitejs.dev/config/
export default defineConfig({
    optimizeDeps: {
        noDiscovery: true
    },
    plugins: [
        vue(),
        Components({
            resolvers: [PrimeVueResolver()]
        }),
        VueDevTools()
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    server: {
        proxy: {
            '/admin': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                secure: false
            }
        }
    },
    define: {
        // Enable Vue devtools in development
        __VUE_OPTIONS_API__: true,
        __VUE_PROD_DEVTOOLS__: false,
        // Enable Vue debug mode
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: true
    }
});
