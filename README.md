# Notes 便签应用

## 项目描述

**Notes** 是一款功能丰富的 Android 便签管理应用，提供完整的便签创建、编辑、分类管理功能，旨在帮助用户高效记录和管理日常信息。应用集成了模糊搜索、字体调整等高级特性，让便签管理更加便捷和智能。

---
## 团队成员
周新彤、李晓烁、王文渊、颜伟斌、谢超

## 安装步骤

### 1. 克隆仓库

```bash
git clone <repository-url>
cd notes
```

### 2. 使用 Android Studio 打开项目

- 启动 Android Studio
- 选择 `File` → `Open`，选择项目根目录
- 等待项目加载完成

### 3. 同步项目依赖

点击工具栏中的 **`Sync Project with Gradle Files`** 按钮，或使用菜单：

```
File → Sync Project with Gradle Files
```

### 4. 编译项目

点击菜单：

```
Build → Rebuild Project
```

或使用快捷键：

| 操作系统 | 快捷键 |
|:---------|:-------|
| Windows / Linux | `Ctrl + Shift + F9` |
| macOS | `Cmd + Shift + F9` |

### 5. 运行应用

1. 创建模拟器：Pixel 4，API 29（Android 10）
2. 点击 `Run` → `Run 'app'` 或点击绿色运行按钮 ▶️
3. 选择目标设备并等待安装完成

---

## 使用示例

### 创建便签

```kotlin
// 创建一条新便签
val note = Note(
    title = "购物清单",
    content = "牛奶、面包、鸡蛋",
    folderId = folder.id,
    backgroundColor = NoteColor.YELLOW
)
noteRepository.insert(note)
```

### 设置提醒

```kotlin
// 为便签设置定时提醒
val reminderTime = Calendar.getInstance().apply {
    set(2026, Calendar.MAY, 1, 9, 0)
}.timeInMillis

alarmManager.setAlarm(
    noteId = note.id,
    triggerTime = reminderTime,
    title = note.title
)
```

### 切换清单模式

```kotlin
// 在普通文本模式和待办清单模式之间切换
workingNote.setChecklistMode(true)
val checkItems = listOf(
    CheckItem("完成项目文档", false),
    CheckItem("代码审查", true),
    CheckItem("提交 Pull Request", false)
)
workingNote.setChecklistItems(checkItems)
```

### 导出便签到 SD 卡

```kotlin
// 将便签内容导出为 .txt 文件
val exportResult = exportManager.exportToFile(
    note = currentNote,
    format = ExportFormat.TEXT
)
if (exportResult.isSuccess) {
    showToast("已导出至: ${exportResult.filePath}")
}
```

---

## 贡献指南

我们欢迎任何形式的贡献！请遵循以下流程：

1. **Fork 项目**：点击页面右上角的 Fork 按钮
2. **创建新分支**：
   ```bash
   git checkout -b feature/AmazingFeature
   ```
3. **提交代码**：
   ```bash
   git commit -m 'feat: 添加了某个很棒的功能'
   ```
4. **推送分支**：
   ```bash
   git push origin feature/AmazingFeature
   ```
5. **提交 Pull Request**：在 GitHub 上创建 Pull Request

### 提交规范

| 类型 | 说明 |
|:-----|:-----|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `docs` | 文档更新 |
| `style` | 代码格式调整 |
| `refactor` | 代码重构 |
| `test` | 测试相关 |
| `chore` | 构建或辅助工具变动 |

---



## 开发环境要求

| 组件 | 版本要求 |
|:-----|:---------|
| Android Studio | Ladybug / Koala / Iguana |
| JDK | 17（使用 Android Studio 自带 Embedded JDK） |
| Gradle | 使用项目自带 Wrapper |
| `compileSdk` | 36 |
| `minSdk` | 21 |
| `targetSdk` | 36 |
| 模拟器 | Pixel 4 + API 29（Android 10） |

---

## 项目结构

```text
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/notes/
│   │   │   ├── ui/              # 界面相关类
│   │   │   ├── data/            # 数据模型和数据库
│   │   │   ├── sync/            # Google Tasks 同步
│   │   │   ├── widget/          # 桌面小组件
│   │   │   └── utils/           # 工具类
│   │   ├── res/                 # 资源文件
│   │   └── AndroidManifest.xml  # 应用清单
│   └── ...
└── build.gradle                 # 应用级 Gradle 配置
```

---

## 常见问题

### Q: Gradle 同步失败怎么办？

**A:** 请确保使用项目自带的 Gradle Wrapper，尝试执行 `Build` → `Clean Project` 后重新同步。

### Q: 模拟器无法启动？

**A:** 检查是否已安装 API 29 系统镜像，并在 AVD Manager 中创建 Pixel 4 模拟器。

### Q: Google 登录失败？

**A:** 请确认 `google-services.json` 已正确配置，且 SHA-1 指纹已添加到 Firebase Console。
