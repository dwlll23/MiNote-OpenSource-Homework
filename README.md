# 小米便签 - 开源代码改动项目

基于小米便签开源项目的功能扩展与代码注释优化。

## 项目概述

本项目对小米便签开源代码进行了深入分析，添加了完整的代码注释，并实现了三个新增功能。项目采用团队协作模式，通过 Gitee 进行代码管理和版本控制。

---

## 新增功能

### 1. 排序功能

用户可以在便签列表界面选择排序方式，支持按创建时间或修改时间的升序/降序排列，提升便签管理效率。

#### 功能特性

- 四种排序方式：按创建时间升序/降序、按修改时间升序/降序
- 排序偏好自动保存，下次打开应用时沿用
- 实时生效，选择后立即刷新列表

#### 实现原理

1. **用户交互**：在便签列表菜单中添加"排序"选项，点击弹出对话框选择排序方式
2. **偏好存储**：使用 `SharedPreferences` 保存用户选择的排序方式（`sort_order`）
3. **动态查询**：在数据库查询时读取偏好值，作为 `ORDER BY` 子句传递给 `AsyncQueryHandler`
4. **界面刷新**：调用 `startAsyncNotesListQuery()` 重新查询并刷新列表

#### 核心代码

```java
// 排序对话框
private void showSortDialog() {
    final String[] sorts = {"按创建时间升序", "按创建时间降序", 
                           "按修改时间升序", "按修改时间降序"};
    final String[] orderByValues = {
        NoteColumns.CREATED_DATE + " ASC",
        NoteColumns.CREATED_DATE + " DESC",
        NoteColumns.MODIFIED_DATE + " ASC",
        NoteColumns.MODIFIED_DATE + " DESC"
    };
    
    new AlertDialog.Builder(this)
        .setTitle("选择排序方式")
        .setItems(sorts, (dialog, which) -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString("sort_order", orderByValues[which]).apply();
            startAsyncNotesListQuery();
        })
        .show();
}
```

#### 涉及文件

| 文件路径 | 修改内容 |
|---------|---------|
| `res/menu/note_list.xml` | 添加排序菜单项 |
| `NotesListActivity.java` | 添加排序对话框和查询逻辑 |

---

### 2. 模糊搜索功能

支持模糊匹配搜索便签内容，不区分大小写，帮助用户快速找到目标便签。

#### 功能特性

- 支持模糊匹配，输入部分关键词即可搜索
- 不区分大小写
- 实时展示搜索结果

#### 效果示例

用户输入 "tha" → 返回所有包含 "tha" 的便签（如 "Thanks"、"thanks"、"thanksgiving" 等）

---

### 3. 字体颜色修改功能

用户可以为便签文字设置不同颜色，增强便签的可读性和个性化。

#### 功能特性

- 多种字体颜色可选
- 修改后立即生效
- 保存便签后颜色保持

#### 使用方式

1. 进入便签编辑界面
2. 点击字体颜色按钮
3. 选择目标颜色
4. 当前文字立即变为所选颜色
5. 保存便签后，重新打开颜色仍保持

---

## 环境准备

### 必装（全员）

| 工具 | 说明 |
|------|------|
| Git | 版本控制工具，用于代码协作 |
| GitHub / Gitee 账号 | 代码托管平台，用于仓库管理 |

### 必装（开发人员）

| 工具 | 说明 |
|------|------|
| Android Studio | Android 开发 IDE |
| Android SDK | Android 开发工具包 |
| 模拟器或真机 | 调试环境，推荐 Android 6.0+ |

### 建议安装（代码阅读）

| 工具 | 说明 |
|------|------|
| Android Studio | 方便阅读和跳转代码，支持语法高亮 |

### 环境配置步骤

1. **安装 Git**
   ```bash
   # Windows: 下载 Git 安装包
   # macOS
   brew install git
   # Linux
   sudo apt-get install git
   ```

2. **克隆项目**
   ```bash
   git clone https://gitee.com/xxx/mi-note.git
   cd mi-note
   ```

3. **配置 Android Studio**
   - 打开 Android Studio
   - 选择 `Open an existing Android Studio project`
   - 选择项目目录
   - 等待 Gradle 同步完成

4. **运行项目**
   - 连接真机或启动模拟器
   - 点击 Run 按钮（绿色三角形）
   - 等待应用安装并启动

---

## 代码注释说明

团队对以下模块进行了详细的代码注释：

| 模块 | 主要文件 | 说明 |
|------|---------|------|
| Data | `NotesDatabaseHelper.java` | 数据库帮助类，负责创建和管理 SQLite 数据库 |
| Gtask | `TaskList.java` | 任务列表节点，用于 Google Tasks 同步 |
| Model | `Note.java` | 便签数据模型，封装数据库操作 |
| Tool | `DataUtils.java` | 数据操作工具类，提供批量操作方法 |
| Ui | `NotesListActivity.java` | 便签列表主界面，核心 Activity |
| Widget | `NoteWidgetProvider.java` | 桌面小部件基类 |

---

## 项目结构

```
MINOTE-OPENSOURCE-HOMEWORK-MAIN/
├── .idea/                              # IDE配置
│   ├── .gitignore
│   ├── .name
│   └── *.xml                           # 各种配置文件
├── app/                                # 应用模块
│   ├── src/
│   │   └── main/
│   │       └── java/net/micode/notes/
│   │           ├── data/               # 数据层
│   │           │   └── NotesDatabaseHelper.java
│   │           ├── gtask/              # Google Tasks 同步
│   │           │   ├── data/
│   │           │   ├── exception/
│   │           │   └── remote/
│   │           ├── model/              # 数据模型
│   │           │   └── Note.java
│   │           ├── tool/               # 工具类
│   │           │   └── DataUtils.java
│   │           ├── ui/                 # 用户界面
│   │           │   └── NotesListActivity.java
│   │           └── widget/             # 桌面小部件
│   │               └── NoteWidgetProvider.java
│   ├── .gitignore
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/                             # Gradle包装器
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .gitignore
├── # Notes 便签应用.md
├── 小米便签分工.docx
├── build.gradle
└── settings.gradle
```
## 项目结构
---

## 团队成员

| 成员 | 主要任务 |
|------|---------|
| 周欣彤（组长） | 项目管理、维护主分支（main）、审核并合并 Pull Request、解决代码冲突、添加字体颜色修改功能 |
| 李晓烁 | 实现便签模糊搜索功能、使用 SearchView 监听输入、实现 SQL 模糊匹配 |
| 王文渊 | 添加排序入口（菜单/按钮）、实现按创建时间、修改时间排序逻辑、 实现升序 |
| 颜伟斌 | 阅读并理解核心代码、注释关键类和方法、注释关键函数、整理类之间调用关系  |
| 谢超 | 编写 README 文档、制作 PPT 汇报材料、整理运行截图与功能截图、汇总小组分工与功能说明  |

---

## 技术栈

- **开发语言**：Java
- **开发平台**：Android
- **最低 SDK**：Android 6.0 (API 23)
- **数据库**：SQLite
- **版本控制**：Git + Gitee
- **代码质量分析**：SonarQube

---

## 许可证

本项目基于小米便签开源项目进行二次开发，仅供学习交流使用。
