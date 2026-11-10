<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { notifyError } from '../api/client'

const auth = useAuthStore()
const formRef = ref(null)
const saving = ref(false)
const form = reactive({ newPassword: '', confirm: '' })

const visible = computed({
  get: () => auth.passwordChangeSuggested,
  set: (value) => {
    if (!value) auth.dismissPasswordChangeSuggestion()
  }
})

const rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === form.newPassword ? callback() : callback(new Error('两次输入的新密码不一致')),
      trigger: 'blur'
    }
  ]
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await auth.changePasswordAfterCode({ newPassword: form.newPassword })
    ElMessage.success('密码已更新，下次可以用新密码登录')
    visible.value = false
  } catch (error) {
    notifyError(error, '密码修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="420px"
    align-center
    title="建议设置新密码"
    destroy-on-close
    class="password-prompt"
  >
    <p class="password-prompt__tip">
      你刚刚通过邮箱验证码登录，说明密码可能已经泄露。现在可以直接设置新密码，不需要输入原密码。
    </p>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="6-32 位新密码"
          @keyup.enter="save"
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirm">
        <el-input
          v-model="form.confirm"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="再次输入新密码"
          @keyup.enter="save"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">稍后再说</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存新密码</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.password-prompt__tip {
  margin: 0 0 18px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
</style>
