<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue';
import { pulsarService } from '@/services/pulsarService';

const props = defineProps({
    topic: {
        type: String,
        required: true
    },
    autoRefresh: {
        type: Boolean,
        default: true
    }
});

const stats = ref(null);
const loading = ref(false);
const error = ref('');
const isUpdating = ref(false);
const showRawJson = ref(false);
let refreshInterval = null;

const loadStats = async (isAutoRefresh = false) => {
    if (!props.topic) return;

    // Only show loading state for initial load or manual refresh
    if (!isAutoRefresh) {
        loading.value = true;
    } else {
        isUpdating.value = true;
    }
    error.value = '';

    try {
        const newStats = await pulsarService.getTopicInternalStats(props.topic);
        stats.value = newStats;

        // Clear any previous errors on successful refresh
        if (isAutoRefresh && error.value) {
            error.value = '';
        }
    } catch (err) {
        error.value = err.message || 'Failed to load topic internal stats';
        console.error('Error loading topic internal stats:', err);
    } finally {
        if (!isAutoRefresh) {
            loading.value = false;
        } else {
            isUpdating.value = false;
        }
    }
};

const startAutoRefresh = () => {
    if (props.autoRefresh && props.topic) {
        refreshInterval = setInterval(() => loadStats(true), 1000);
    }
};

const stopAutoRefresh = () => {
    if (refreshInterval) {
        clearInterval(refreshInterval);
        refreshInterval = null;
    }
};

const formatPlainNumber = (num) => {
    if (typeof num !== 'number') return '0';
    return Math.floor(num).toLocaleString();
};

const formatBytes = (bytes) => {
    if (typeof bytes !== 'number') return '0 B';
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatTimestamp = (timestamp) => {
    if (!timestamp) return 'N/A';
    return new Date(timestamp).toLocaleString();
};

const getCurrentLedgerId = () => {
    if (stats.value.ledgers && stats.value.ledgers.length > 0) {
        return stats.value.ledgers[stats.value.ledgers.length - 1].ledgerId;
    }
    return 'N/A';
};

watch(
    () => props.topic,
    (newTopic) => {
        if (newTopic) {
            loadStats();
        }
    },
    { immediate: true }
);

watch(
    () => props.autoRefresh,
    (newAutoRefresh) => {
        if (newAutoRefresh) {
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    }
);

onMounted(() => {
    startAutoRefresh();
});

onUnmounted(() => {
    stopAutoRefresh();
});
</script>

<template>
    <Card class="border-none shadow-sm">
        <template #title>
            <div class="flex items-center gap-2">
                <Avatar icon="pi pi-cog" class="bg-purple-500 text-white" size="small" />
                <span>Internal Statistics</span>
                <ProgressSpinner v-if="isUpdating" style="width: 16px; height: 16px" strokeWidth="4" class="ml-2" />
                <Badge :value="topic" severity="secondary" size="small" class="ml-auto" />
                <Button icon="pi pi-code" size="small" variant="text" severity="secondary" @click="showRawJson = !showRawJson" v-tooltip.top="showRawJson ? 'Hide JSON' : 'Show Raw JSON'" />
            </div>
        </template>

        <template #content>
            <div v-if="loading" class="text-center py-8">
                <ProgressSpinner />
                <p class="text-surface-600 mt-4">Loading internal statistics...</p>
            </div>

            <div v-else-if="error" class="text-center py-8">
                <i class="pi pi-exclamation-triangle text-3xl text-red-500 mb-4"></i>
                <h3 class="text-lg font-medium text-surface-900 mb-2">Error loading internal statistics</h3>
                <Message severity="error" :closable="false">{{ error }}</Message>
            </div>

            <div v-else-if="stats" class="space-y-6">
                <!-- Key Internal Metrics -->
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <Fieldset legend="Ledger Statistics">
                        <div class="space-y-3">
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Total Ledgers</span>
                                <Badge :value="stats.ledgers ? stats.ledgers.length : 0" severity="info" />
                            </div>
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Current Ledger ID</span>
                                <Badge :value="getCurrentLedgerId()" severity="success" />
                            </div>
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Current Ledger Entries</span>
                                <Badge :value="formatPlainNumber(stats.currentLedgerEntries)" severity="warning" />
                            </div>
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Current Ledger Size</span>
                                <Badge :value="formatBytes(stats.currentLedgerSize)" severity="danger" />
                            </div>
                        </div>
                    </Fieldset>

                    <Fieldset legend="Storage Statistics">
                        <div class="space-y-3">
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Total Entries</span>
                                <Badge :value="formatPlainNumber(stats.numberOfEntries)" severity="info" />
                            </div>
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Total Size</span>
                                <Badge :value="formatBytes(stats.totalSize)" severity="warning" />
                            </div>
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Entries Added</span>
                                <Badge :value="formatPlainNumber(stats.entriesAddedCounter)" severity="success" />
                            </div>
                            <div class="flex justify-between items-center p-3 bg-surface-50 rounded-lg">
                                <span class="text-sm text-surface-600">Ledger State</span>
                                <Badge :value="stats.state || 'N/A'" severity="secondary" />
                            </div>
                        </div>
                    </Fieldset>
                </div>

                <!-- Ledgers Table -->
                <Fieldset v-if="stats.ledgers && stats.ledgers.length > 0" legend="Ledger Details">
                    <DataTable :value="stats.ledgers" stripedRows responsiveLayout="scroll">
                        <Column field="ledgerId" header="Ledger ID" sortable />
                        <Column field="entries" header="Entries" sortable>
                            <template #body="slotProps">
                                {{ formatPlainNumber(slotProps.data.entries) }}
                            </template>
                        </Column>
                        <Column field="size" header="Size" sortable>
                            <template #body="slotProps">
                                {{ formatBytes(slotProps.data.size) }}
                            </template>
                        </Column>
                        <Column field="offloaded" header="Offloaded" sortable>
                            <template #body="slotProps">
                                <Tag :value="slotProps.data.offloaded ? 'Yes' : 'No'" :severity="slotProps.data.offloaded ? 'warning' : 'success'" size="small" />
                            </template>
                        </Column>
                        <Column field="underReplicated" header="Under Replicated" sortable>
                            <template #body="slotProps">
                                <Tag :value="slotProps.data.underReplicated ? 'Yes' : 'No'" :severity="slotProps.data.underReplicated ? 'danger' : 'success'" size="small" />
                            </template>
                        </Column>
                    </DataTable>
                </Fieldset>

                <!-- Last Confirmed Entry -->
                <Fieldset v-if="stats.lastConfirmedEntry" legend="Last Confirmed Entry">
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div class="bg-surface-50 p-4 rounded-lg">
                            <div class="text-xs text-surface-500 mb-1">Entry Position</div>
                            <div class="text-sm font-semibold text-surface-900">{{ stats.lastConfirmedEntry }}</div>
                        </div>
                        <div class="bg-surface-50 p-4 rounded-lg">
                            <div class="text-xs text-surface-500 mb-1">Last Ledger Created</div>
                            <div class="text-sm font-semibold text-surface-900">{{ formatTimestamp(stats.lastLedgerCreatedTimestamp) }}</div>
                        </div>
                    </div>
                </Fieldset>

                <!-- Compaction Info -->
                <Fieldset v-if="stats.compactedLedger && stats.compactedLedger.ledgerId > 0" legend="Compaction Information">
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div class="bg-surface-50 p-4 rounded-lg">
                            <div class="text-xs text-surface-500 mb-1">Compacted Ledger ID</div>
                            <div class="text-sm font-semibold text-surface-900">{{ stats.compactedLedger.ledgerId }}</div>
                        </div>
                        <div class="bg-surface-50 p-4 rounded-lg">
                            <div class="text-xs text-surface-500 mb-1">Compacted Entries</div>
                            <div class="text-sm font-semibold text-surface-900">{{ formatPlainNumber(stats.compactedLedger.entries) }}</div>
                        </div>
                        <div class="bg-surface-50 p-4 rounded-lg">
                            <div class="text-xs text-surface-500 mb-1">Compacted Size</div>
                            <div class="text-sm font-semibold text-surface-900">{{ formatBytes(stats.compactedLedger.size) }}</div>
                        </div>
                    </div>
                </Fieldset>

                <!-- Cursors Information -->
                <Fieldset v-if="stats.cursors && Object.keys(stats.cursors).length > 0" legend="Cursors Information">
                    <div class="space-y-4">
                        <div v-for="(cursor, name) in stats.cursors" :key="name" class="bg-surface-50 rounded-xl p-4 border border-surface-200">
                            <div class="flex items-center justify-between mb-3">
                                <span class="text-sm font-medium text-surface-900">{{ name }}</span>
                                <Tag :value="cursor.state" :severity="cursor.active ? 'success' : 'warning'" size="small" />
                            </div>
                            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 text-sm">
                                <div class="bg-surface-100 rounded-lg p-2">
                                    <div class="text-xs text-surface-500">Mark Delete Position</div>
                                    <div class="font-medium text-surface-900">{{ cursor.markDeletePosition }}</div>
                                </div>
                                <div class="bg-surface-100 rounded-lg p-2">
                                    <div class="text-xs text-surface-500">Read Position</div>
                                    <div class="font-medium text-surface-900">{{ cursor.readPosition }}</div>
                                </div>
                                <div class="bg-surface-100 rounded-lg p-2">
                                    <div class="text-xs text-surface-500">Messages Consumed</div>
                                    <div class="font-medium text-surface-900">{{ formatPlainNumber(cursor.messagesConsumedCounter) }}</div>
                                </div>
                                <div class="bg-surface-100 rounded-lg p-2">
                                    <div class="text-xs text-surface-500">Pending Read Ops</div>
                                    <div class="font-medium text-surface-900">{{ cursor.pendingReadOps }}</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </Fieldset>

                <!-- Raw JSON View -->
                <Fieldset v-if="showRawJson" legend="Raw JSON Data">
                    <div class="bg-surface-900 text-surface-100 p-4 rounded-lg overflow-x-auto">
                        <pre class="text-sm font-mono">{{ JSON.stringify(stats, null, 2) }}</pre>
                    </div>
                </Fieldset>
            </div>
        </template>
    </Card>
</template>
