<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: 'custom' }
})

/** 模块图标按语义归类，自定义模块统一用标签图形。 */
const PATHS = {
  person: 'M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm0 10c4.4 0 8 1.8 8 4v2H4v-2c0-2.2 3.6-4 8-4Z',
  cap: 'M12 3 1 8l11 5 9-4.1V15h2V8L12 3Zm-7 9.2V17c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8v-4.8l-7 3.2-7-3.2Z',
  briefcase: 'M10 4h4a2 2 0 0 1 2 2v1h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3V6a2 2 0 0 1 2-2Zm0 5h4V6h-4v3Z',
  folder: 'M4 4h5l2 2h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z',
  chart: 'M12 3a9 9 0 1 0 9 9h-9V3Z',
  picture: 'M3 5h18v14H3V5Zm3 2.5A1.5 1.5 0 1 0 7.5 9 1.5 1.5 0 0 0 6 7.5ZM5 17h14l-4.5-6-3.2 4.3-2.1-2.1L5 17Z',
  star: 'M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8L3.6 9.7l5.8-.8L12 3.6Z',
  medal: 'M12 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12Zm-4 11.5L6 22l6-3 6 3-2-8.5-1.6 1.4L12 16.5l-4.4-1.6-1.6-1.4Z',
  paper: 'M6 2h9l4 4v16H6V2Zm8 1.5V7h3.5L14 3.5ZM8 10h8v1.8H8V10Zm0 3.5h8v1.8H8v-1.8Zm0 3.5h5v1.8H8v-1.8Z',
  heart: 'M12 20.7S4 15.9 4 11.1A4.4 4.4 0 0 1 12 8.3a4.4 4.4 0 0 1 8 2.8c0 4.8-8 9.6-8 9.6Z',
  target: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18Zm0 3.2a5.8 5.8 0 1 0 0 11.6 5.8 5.8 0 0 0 0-11.6Zm0 3a2.8 2.8 0 1 1 0 5.6 2.8 2.8 0 0 1 0-5.6Z',
  flag: 'M6 3h2v18H6V3Zm3 1h9l-2.2 3.5L18 11H9V4Z',
  tag: 'M3 3h8l10 10-8 8L3 11V3Zm3.5 2A1.8 1.8 0 1 0 8.3 6.8 1.8 1.8 0 0 0 6.5 5Z',
  phone: 'M6.6 3h2.7l1.4 4.2-2 1.4a12.7 12.7 0 0 0 6.7 6.7l1.4-2L21 14.7v2.7A2.6 2.6 0 0 1 18.4 20C10.4 19.5 4.5 13.6 4 5.6A2.6 2.6 0 0 1 6.6 3Z',
  email: 'M3 5h18v14H3V5Zm2 2v.2l7 4.6 7-4.6V7H5Zm14 3-7 4.6L5 10v7h14v-7Z',
  website: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm0 2c-1.5 0-2.8 3.1-3 6h6c-.2-2.9-1.5-6-3-6Zm-3 8c0 1.4.1 2.8.4 4h5.2c.3-1.2.4-2.6.4-4s-.1-2.8-.4-4H9.4c-.3 1.2-.4 2.6-.4 4Zm-2.9 0c0-1.4.2-2.8.5-4H4.2A8 8 0 0 0 4 12c0 1.4.1 2.7.2 4h2.4a19 19 0 0 1-.5-4Zm1.4 6c.7 1.2 1.6 2.2 2.6 2.8-.4-.9-.7-1.9-.9-2.8H7.5Zm9 0c-.2.9-.5 1.9-.9 2.8 1-.6 1.9-1.6 2.6-2.8h-1.7ZM7.5 8h1.7c.2-.9.5-1.9.9-2.8A8 8 0 0 0 7.5 8Zm7.3 0h1.7a8 8 0 0 0-2.6-2.8c.4.9.7 1.9.9 2.8Z',
  location: 'M12 2a7 7 0 0 1 7 7c0 5.1-7 13-7 13S5 14.1 5 9a7 7 0 0 1 7-7Zm0 4.2A2.6 2.6 0 1 0 12 11.4a2.6 2.6 0 0 0 0-5.2Z',
  gender: 'M12 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10Zm0 2a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm-1 8h2v2h3v2h-3v3h-2v-3H8v-2h3v-2Z',
  cake: 'M12 2.5c.8.9 1.6 1.8 1.6 2.7a1.6 1.6 0 0 1-3.2 0c0-.9.8-1.8 1.6-2.7ZM5 10h14c.6 0 1 .4 1 1v3c0 .6-.4 1-1 1h-.5c-.8 0-1.5-.7-1.5-1.5S16.3 12 15.5 12 14 12.7 14 13.5 13.3 15 12.5 15 11 14.3 11 13.5 10.3 12 9.5 12 8 12.7 8 13.5 7.3 15 6.5 15H5c-.6 0-1-.4-1-1v-3c0-.6.4-1 1-1Zm1 6.5c.8 0 1.4.4 1.8.9.3-.5.9-.9 1.7-.9s1.4.4 1.7.9c.3-.5.9-.9 1.7-.9s1.4.4 1.7.9c.4-.5 1-.9 1.8-.9H18v4c0 .6-.4 1-1 1H5c-.6 0-1-.4-1-1v-4h2Z'
}

const BY_TYPE = {
  basic_info: 'person',
  job_intention: 'target',
  education: 'cap',
  postgraduate_info: 'cap',
  work_experience: 'briefcase',
  campus_experience: 'flag',
  project_experience: 'folder',
  skills_summary: 'chart',
  skill_proficiency: 'chart',
  portfolio: 'picture',
  award: 'star',
  competition: 'star',
  certificate: 'medal',
  journal_paper: 'paper',
  paper_publication: 'paper',
  self_evaluation: 'person',
  hobby: 'heart',
  custom: 'tag',
  phone: 'phone',
  email: 'email',
  website: 'website',
  city: 'location',
  gender: 'gender',
  birthday: 'cake'
}

const path = computed(() => PATHS[BY_TYPE[props.type] || 'tag'])
</script>

<template>
  <svg class="paper-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
    <path :d="path" fill="currentColor" fill-rule="evenodd" />
  </svg>
</template>

<style scoped>
.paper-icon {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
