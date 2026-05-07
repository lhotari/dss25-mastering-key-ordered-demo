import AppLayout from '@/layout/AppLayout.vue';
import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/',
                    name: 'dashboard',
                    component: () => import('@/views/Dashboard.vue')
                },
                {
                    path: '/topic/:topicName',
                    name: 'topic-stats',
                    component: () => import('@/views/TopicStatsPage.vue'),
                    props: (route) => ({ topicName: route.params.topicName })
                }
            ]
        }
    ]
});

export default router;
