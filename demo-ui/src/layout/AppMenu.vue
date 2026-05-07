<script setup>
import { ref } from 'vue';

const model = ref([
    {
        label: 'Home',
        items: [{ label: 'Dashboard', icon: 'pi pi-fw pi-home', to: '/' }]
    }
]);
</script>

<template>
    <ul class="layout-menu">
        <template v-for="item in model" :key="item.label">
            <template v-if="!item.separator">
                <li>
                    <router-link :to="item.items[0].to" class="flex items-center gap-2 p-3 text-surface-700 hover:bg-surface-100 rounded-lg transition-colors">
                        <i :class="item.items[0].icon"></i>
                        <span>{{ item.items[0].label }}</span>
                    </router-link>
                </li>
            </template>
            <li v-if="item.separator" class="menu-separator"></li>
        </template>
    </ul>
</template>

<style lang="scss" scoped></style>
