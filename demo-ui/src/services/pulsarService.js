import axios from 'axios';

const api = axios.create({
    baseURL: '/admin/v2',
    timeout: 10000
});

export const pulsarService = {
    async getClusters() {
        try {
            const response = await api.get('/clusters');
            return response.data;
        } catch (error) {
            console.error('Error fetching clusters:', error);
            throw error;
        }
    },

    async getTenants() {
        try {
            const response = await api.get('/tenants');
            return response.data;
        } catch (error) {
            console.error('Error fetching tenants:', error);
            throw error;
        }
    },

    async getNamespaces(tenant) {
        try {
            const response = await api.get(`/namespaces/${tenant}`);
            return response.data;
        } catch (error) {
            console.error('Error fetching namespaces:', error);
            throw error;
        }
    },

    async getTopics(tenant, namespace) {
        try {
            const response = await api.get(`/persistent/${tenant}/${namespace}`);
            return response.data;
        } catch (error) {
            console.error('Error fetching topics:', error);
            throw error;
        }
    },

    async getTopicsByNamespace(fullNamespace) {
        try {
            const response = await api.get(`/persistent/${fullNamespace}`);
            return response.data;
        } catch (error) {
            console.error('Error fetching topics by namespace:', error);
            throw error;
        }
    },

    async getTopicStats(topic) {
        try {
            const topicPath = topic.replace('persistent://', '');
            const response = await api.get(`/persistent/${topicPath}/stats`);
            return response.data;
        } catch (error) {
            console.error('Error fetching topic stats:', error);
            throw error;
        }
    },

    async getTopicInternalStats(topic) {
        try {
            const topicPath = topic.replace('persistent://', '');
            const response = await api.get(`/persistent/${topicPath}/internalStats`);
            return response.data;
        } catch (error) {
            console.error('Error fetching topic internal stats:', error);
            throw error;
        }
    },

    async getPartitionedTopicStats(topic) {
        try {
            const topicPath = topic.replace('persistent://', '');
            const response = await api.get(`/persistent/${topicPath}/partitioned-stats`);
            return response.data;
        } catch (error) {
            console.error('Error fetching partitioned topic stats:', error);
            throw error;
        }
    },

    async getPartitionStats(topic, partition) {
        try {
            const topicPath = topic.replace('persistent://', '');
            const response = await api.get(`/persistent/${topicPath}-partition-${partition}/stats`);
            return response.data;
        } catch (error) {
            console.error('Error fetching partition stats:', error);
            throw error;
        }
    }
};
