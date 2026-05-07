<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
    topics: {
        type: Array,
        required: true,
        default: () => []
    },
    selectedTopic: {
        type: String,
        default: ''
    }
});

const emit = defineEmits(['topic-selected']);

const searchQuery = ref('');

const filteredTopics = computed(() => {
    if (!searchQuery.value) {
        return props.topics;
    }
    return props.topics.filter((topic) => topic.toLowerCase().includes(searchQuery.value.toLowerCase()));
});

const selectTopic = (topic) => {
    emit('topic-selected', topic);
};

const isPartitioned = (topic) => {
    return topic.includes('-partition-');
};
</script>

<template>
    <Card class="border-none shadow-sm">
        <template #title>
            <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                    <i class="pi pi-database text-primary"></i>
                    <span>Topics</span>
                </div>
                <Badge :value="`${filteredTopics.length} of ${topics.length}`" severity="secondary" />
            </div>
        </template>

        <template #content>
            <IconField>
                <InputIcon>
                    <i class="pi pi-search"></i>
                </InputIcon>
                <InputText v-model="searchQuery" placeholder="Search topics..." class="w-full" />
            </IconField>

            <div class="mt-4 space-y-2 max-h-96 overflow-y-auto">
                <div v-for="topic in filteredTopics" :key="topic" @click="selectTopic(topic)" class="p-3 rounded-lg cursor-pointer transition-all duration-200 border-2 bg-surface-50 hover:bg-surface-100 border-surface-200 hover:border-surface-300">
                    <div class="flex items-center justify-between">
                        <div class="flex items-center gap-3 flex-1 min-w-0">
                            <div class="flex-shrink-0">
                                <Avatar :icon="isPartitioned(topic) ? 'pi pi-th-large' : 'pi pi-file'" :class="['text-white', isPartitioned(topic) ? 'bg-primary' : 'bg-green-500']" size="large" shape="circle" />
                            </div>
                            <div class="flex-1 min-w-0">
                                <div class="text-sm font-medium text-surface-900 truncate">{{ topic }}</div>
                                <div class="flex items-center gap-2 mt-1">
                                    <Tag :value="isPartitioned(topic) ? 'Partitioned' : 'Non-Partitioned'" :severity="isPartitioned(topic) ? 'primary' : 'success'" size="small" />
                                </div>
                            </div>
                        </div>
                        <div class="flex-shrink-0 ml-2">
                            <i class="pi pi-chevron-right text-surface-400" />
                        </div>
                    </div>
                </div>
            </div>

            <div v-if="filteredTopics.length === 0" class="text-center py-8">
                <div class="w-12 h-12 bg-surface-100 rounded-full flex items-center justify-center mx-auto mb-3">
                    <i class="pi pi-file-excel text-xl text-surface-400"></i>
                </div>
                <h3 class="text-lg font-medium text-surface-900 mb-2">No topics found</h3>
                <p class="text-surface-600">Try adjusting your search or check your connection.</p>
            </div>
        </template>
    </Card>
</template>
