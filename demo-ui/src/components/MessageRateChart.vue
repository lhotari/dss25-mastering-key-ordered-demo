<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import VueApexCharts from 'vue3-apexcharts';

const props = defineProps({
    msgRateIn: {
        type: Number,
        default: 0
    },
    msgRateOut: {
        type: Number,
        default: 0
    },
    throughputIn: {
        type: Number,
        default: 0
    },
    throughputOut: {
        type: Number,
        default: 0
    },
    autoRefresh: {
        type: Boolean,
        default: false
    }
});

const formatBytes = (bytes) => {
    if (typeof bytes !== 'number') return '0 B';
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const chartOptions = ref({
    chart: {
        type: 'line',
        height: 300,
        toolbar: {
            show: false
        },
        zoom: {
            enabled: false
        }
    },
    dataLabels: {
        enabled: false
    },
    stroke: {
        curve: 'smooth',
        width: 2
    },
    grid: {
        borderColor: '#e5e7eb',
        strokeDashArray: 4,
        xaxis: {
            lines: {
                show: true
            }
        },
        yaxis: {
            lines: {
                show: true
            }
        }
    },
    xaxis: {
        type: 'category',
        labels: {
            show: true,
            style: {
                colors: '#6b7280',
                fontSize: '12px'
            }
        }
    },
    yaxis: {
        labels: {
            style: {
                colors: '#6b7280',
                fontSize: '12px'
            }
        }
    },
    legend: {
        position: 'top',
        horizontalAlign: 'right',
        labels: {
            colors: '#374151',
            useSeriesColors: false
        }
    },
    colors: ['#3b82f6', '#10b981'],
    tooltip: {
        theme: 'light',
        y: {
            formatter: (value) => value.toFixed(2) + ' msg/s'
        }
    }
});

const chartSeries = ref([
    {
        name: 'Msg Rate In',
        data: []
    },
    {
        name: 'Msg Rate Out',
        data: []
    }
]);

const maxDataPoints = 20;
const updateInterval = ref(null);

const addDataPoint = () => {
    const now = new Date();
    const timeLabel = now.toLocaleTimeString();

    chartSeries.value[0].data.push({
        x: timeLabel,
        y: Math.round(props.msgRateIn)
    });

    chartSeries.value[1].data.push({
        x: timeLabel,
        y: Math.round(props.msgRateOut)
    });

    // Keep only the last maxDataPoints
    if (chartSeries.value[0].data.length > maxDataPoints) {
        chartSeries.value[0].data.shift();
        chartSeries.value[1].data.shift();
    }
};

const startAutoUpdate = () => {
    if (props.autoRefresh && !updateInterval.value) {
        updateInterval.value = setInterval(addDataPoint, 2000); // Update every 2 seconds
    }
};

const stopAutoUpdate = () => {
    if (updateInterval.value) {
        clearInterval(updateInterval.value);
        updateInterval.value = null;
    }
};

watch(
    () => props.autoRefresh,
    (newValue) => {
        if (newValue) {
            startAutoUpdate();
        } else {
            stopAutoUpdate();
        }
    }
);

onMounted(() => {
    // Initialize with current data
    addDataPoint();
    startAutoUpdate();
});

onUnmounted(() => {
    stopAutoUpdate();
});
</script>

<template>
    <Card class="border-none shadow-sm">
        <template #title>
            <div class="flex items-center gap-2">
                <i class="pi pi-chart-line text-primary"></i>
                <span>Message Rate History</span>
            </div>
        </template>

        <template #content>
            <div class="relative">
                <VueApexCharts type="line" height="300" :options="chartOptions" :series="chartSeries" />

                <div v-if="!autoRefresh" class="absolute top-2 right-2">
                    <Tag severity="warning" value="Paused" />
                </div>
            </div>

            <div class="mt-4 grid grid-cols-2 gap-4">
                <div class="bg-blue-50 rounded-lg p-3">
                    <div class="flex items-center justify-between mb-2">
                        <span class="text-sm font-medium text-blue-700">Current Rate In</span>
                        <div class="w-3 h-3 bg-blue-500 rounded-full"></div>
                    </div>
                    <div class="flex justify-between items-center">
                        <div class="text-lg font-bold text-blue-900">{{ Math.round(msgRateIn) }}</div>
                        <div class="text-sm font-medium text-blue-700">{{ formatBytes(throughputIn) }}/s</div>
                    </div>
                </div>

                <div class="bg-emerald-50 rounded-lg p-3">
                    <div class="flex items-center justify-between mb-2">
                        <span class="text-sm font-medium text-emerald-700">Current Rate Out</span>
                        <div class="w-3 h-3 bg-emerald-500 rounded-full"></div>
                    </div>
                    <div class="flex justify-between items-center">
                        <div class="text-lg font-bold text-emerald-900">{{ Math.round(msgRateOut) }}</div>
                        <div class="text-sm font-medium text-emerald-700">{{ formatBytes(throughputOut) }}/s</div>
                    </div>
                </div>
            </div>
        </template>
    </Card>
</template>
