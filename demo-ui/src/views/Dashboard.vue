<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import TopicList from '@/components/TopicList.vue';
import { pulsarService } from '@/services/pulsarService';

const router = useRouter();
const topics = ref([]);
const loading = ref(false);
const error = ref('');
const autoRefresh = ref(false);

const partitionedCount = computed(() => {
    return topics.value.filter((topic) => topic.includes('-partition-')).length;
});

const nonPartitionedCount = computed(() => {
    return topics.value.filter((topic) => !topic.includes('-partition-')).length;
});

const loadTopics = async () => {
    loading.value = true;
    error.value = '';

    try {
        const tenants = await pulsarService.getTenants();
        let allTopics = [];

        for (const tenant of tenants) {
            const namespaces = await pulsarService.getNamespaces(tenant);

            for (const fullNamespace of namespaces) {
                // fullNamespace is already in format "tenant/namespace"
                const topicList = await pulsarService.getTopicsByNamespace(fullNamespace);
                allTopics = allTopics.concat(topicList); // topics already include persistent:// prefix
            }
        }

        topics.value = allTopics;
    } catch (err) {
        error.value = err.message || 'Failed to load topics';
        console.error('Error loading topics:', err);
    } finally {
        loading.value = false;
    }
};

const selectTopic = (topic) => {
    router.push({
        name: 'topic-stats',
        params: { topicName: topic }
    });
};

onMounted(() => {
    loadTopics();
});
</script>

<template>
    <div class="space-y-6">
        <!-- Header Section -->
        <Card class="border-none shadow-sm">
            <template #title>
                <div class="flex items-center justify-between">
                    <div>
                        <h2 class="text-2xl font-bold text-surface-900">Topic Explorer</h2>
                        <p class="text-surface-600 mt-1">Browse and monitor your Apache Pulsar topics</p>
                    </div>
                    <div class="flex items-center gap-3">
                        <div class="flex items-center gap-2">
                            <ToggleSwitch v-model="autoRefresh" />
                            <span class="text-sm text-surface-600">Auto-refresh</span>
                        </div>
                        <Button @click="loadTopics" :loading="loading" icon="pi pi-refresh" label="Refresh Topics" severity="primary" />
                    </div>
                </div>
            </template>

            <template #content>
                <Message v-if="error" severity="error" :closable="false">
                    {{ error }}
                </Message>

                <!-- Stats Overview -->
                <div v-if="topics.length > 0" class="grid grid-cols-1 md:grid-cols-4 gap-4 mt-4">
                    <div class="bg-gradient-to-br from-blue-500 to-blue-600 rounded-xl p-4 text-white shadow-lg">
                        <div class="text-2xl font-bold">{{ topics.length }}</div>
                        <div class="text-sm text-blue-100 font-medium">Total Topics</div>
                    </div>
                    <div class="bg-gradient-to-br from-emerald-500 to-emerald-600 rounded-xl p-4 text-white shadow-lg">
                        <div class="text-2xl font-bold">{{ partitionedCount }}</div>
                        <div class="text-sm text-emerald-100 font-medium">Partitioned</div>
                    </div>
                    <div class="bg-gradient-to-br from-purple-500 to-purple-600 rounded-xl p-4 text-white shadow-lg">
                        <div class="text-2xl font-bold">{{ nonPartitionedCount }}</div>
                        <div class="text-sm text-purple-100 font-medium">Non-Partitioned</div>
                    </div>
                    <div class="bg-gradient-to-br from-amber-500 to-amber-600 rounded-xl p-4 text-white shadow-lg">
                        <div class="text-2xl font-bold">{{ topics.length }}</div>
                        <div class="text-sm text-amber-100 font-medium">Available</div>
                    </div>
                </div>
            </template>
        </Card>

        <!-- Topic List (Full width) -->
        <div class="mb-6">
            <TopicList :topics="topics" @topic-selected="selectTopic" />
        </div>

        <!-- Empty State -->
        <div v-if="topics.length === 0" class="text-center py-12">
            <div class="w-16 h-16 bg-surface-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <i class="pi pi-chart-bar text-2xl text-primary"></i>
            </div>
            <h3 class="text-xl font-semibold text-surface-900 mb-2">Topic Explorer</h3>
            <p class="text-surface-600">Select a topic from the list above to view its detailed statistics and performance metrics.</p>
        </div>
    </div>
</template>
