<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import TopicStats from '@/components/TopicStats.vue';
import TopicInternalStats from '@/components/TopicInternalStats.vue';

const router = useRouter();
const topic = ref('');
const autoRefresh = ref(true);

const props = defineProps({
    topicName: {
        type: String,
        required: true
    }
});

const goBack = () => {
    router.push('/');
};

const formatTopicName = (topic) => {
    if (!topic) return '';
    return topic.replace('persistent://', '');
};

onMounted(() => {
    topic.value = props.topicName;
});

onUnmounted(() => {
    // Clean up if needed
});
</script>

<template>
    <div class="space-y-6">
        <!-- Topic Header with Back Button -->
        <Card class="border-none shadow-sm">
            <template #title>
                <div class="flex items-center justify-between">
                    <div class="flex items-center gap-3">
                        <Button @click="goBack" icon="pi pi-arrow-left" size="small" variant="text" severity="secondary" title="Back to topics" />
                        <div class="flex items-center gap-2">
                            <Avatar icon="pi pi-bolt" class="bg-primary text-white" size="small" />
                            <span class="text-lg font-semibold text-surface-900">{{ formatTopicName(topic) }}</span>
                        </div>
                    </div>
                    <div class="flex items-center gap-3">
                        <div class="flex items-center gap-2">
                            <ToggleSwitch v-model="autoRefresh" />
                            <span class="text-sm text-surface-600">Auto-refresh</span>
                        </div>
                    </div>
                </div>
            </template>
        </Card>

        <!-- Topic Statistics -->
        <TopicStats v-if="topic" :topic="topic" :auto-refresh="autoRefresh" />
        <TopicInternalStats v-if="topic" :topic="topic" :auto-refresh="autoRefresh" />
    </div>
</template>
