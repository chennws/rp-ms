<template>
  <div class="dashboard-container">
    <!-- 学生端首页 -->
    <student-dashboard v-if="isStudent" />

    <!-- 教师端首页 -->
    <teacher-dashboard v-else-if="isTeacher" />

    <!-- 管理员端首页 -->
    <admin-dashboard v-else />
  </div>
</template>

<script>
import StudentDashboard from './dashboard/student'
import TeacherDashboard from './dashboard/teacher'
import AdminDashboard from './dashboard/admin'

export default {
  name: 'Index',
  components: {
    StudentDashboard,
    TeacherDashboard,
    AdminDashboard
  },
  computed: {
    // 判断是否为学生
    isStudent() {
      const roles = this.$store.getters.roles || []
      return roles.includes('student') || (!roles.includes('teacher') && !roles.includes('admin'))
    },
    // 判断是否为教师
    isTeacher() {
      const roles = this.$store.getters.roles || []
      const permissions = this.$store.getters.permissions || []
      return roles.includes('teacher') || permissions.includes('task:task:add')
    }
  }
}
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: calc(100vh - 84px);
}
</style>
