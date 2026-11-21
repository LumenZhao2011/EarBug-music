# 🐌 耳虫音乐（Earbug Music）

> 一个专注于 **网易云音乐** 的 Android 下载 / 播放客户端：解析直链、写入完整标签（含封面/歌词）、保存到系统 Download 目录，并带一个像样的播放页。

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-34-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

> ⚠️ **本项目仅用于个人学习与技术研究**，请勿用于侵犯版权或任何商业用途。请支持正版音乐。

---

## 目录

- [功能特性](#功能特性)
- [界面与交互](#界面与交互)
- [音质与解析原理](#音质与解析原理)
- [下载与文件标签](#下载与文件标签)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [构建与运行](#构建与运行)
- [使用说明](#使用说明)
- [已知限制](#已知限制)
- [更新日志](#更新日志)
- [免责声明](#免责声明)
- [License](#license)

---

## 功能特性

### 1. 多模式输入（wyapi 式用法）

| 模式 | 输入 | 行为 |
| --- | --- | --- |
| **歌曲** | 歌曲 ID / 歌曲链接 / 关键词 | 解析单曲；关键词则搜索 |
| **专辑** | 专辑 ID / 专辑链接 | 解析整张专辑，**自动批量加入下载** |
| **歌单** | 歌单 ID / 歌单链接 | 解析整个歌单，**自动批量加入下载** |
| **热歌榜** | 无需输入（右上角 📊 按钮） | 拉取网易云官方热歌榜（歌单 3778678） |

- 支持粘贴 music.163.com 的歌曲 / 专辑 / 歌单链接，也支持纯数字 ID
- 搜索使用网易云 cloudsearch 接口

### 2. 六档音质（官方叫法）

```
标准 → 较高 → 极高 → 无损 → Hi-Res → 超清母带
```

- 只请求所选档位，**不做静默降级**；服务端档次不足时会明确报错
- 记住上次选择；首次安装默认「标准」
- 音质判断按等级比较（支持「高清环绕声 / 沉浸环绕声 / 杜比全景声」等特殊档位）

### 3. 下载管理

- **串行队列**，适合歌单批量下载
- 实时进度：限流刷新（每 500ms）+ 指数平滑速度，数字稳定不乱跳
- 状态机：等待 → 解析 → 下载 → 完成 / 失败
- 下载完成前只显示 ✖（可取消），完成后才出现 ▶ 播放与 🗑 删除
- 删除前检查本地文件（含未完成的临时文件），有文件则**二次确认**
- **历史持久化**：冷启动 / 升级后仍保留任务列表
- 始终显示所选 / 实际音质

### 4. 播放器

- 封面、歌名、歌手、实际音质
- **逐行高亮滚动歌词**（自动居中、手动拖动时暂停跟随）
- 播放模式：**顺序播放 / 单曲循环 / 仅单次 / 随机播放**（记忆选择）
- 上一首 / 播放暂停 / 下一首 / 进度拖动
- **本地优先**：已下载的歌曲直接读取本地文件，不再联网
- 从下载页播放时，播放队列自动切换为「已下载完成」的曲目

### 5. 系统通知栏控制

- 前台服务 + MediaSessionCompat
- 通知栏 / 锁屏：⏮ 上一首 · ⏯ 播放暂停 · ⏭ 下一首 · ■ 停止
- 耳机线控 / 蓝牙设备可控制

### 6. 评论区

- 播放页右上角进入
- 热门评论 + 最新评论，显示昵称 / 内容 / 点赞 / 时间
- 浅灰底 + 深色字（与暗色主题形成自然过渡）

### 7. 界面

- 暗色主题 + 网易红 #E8352C
- 圆角卡片、自定义选择弹窗（缩放淡入动画）
- 底部三 Tab：搜索 / 播放 / 下载，带切换动画

---

## 界面与交互

```
┌────────────────────────────┐
│ 耳虫音乐            📊      │   ← 热歌榜入口
│ ┌────────────────────────┐ │
│ │ ♪ 输入歌曲 ID 或链接…  🔍│ │   ← 胶囊搜索框
│ └────────────────────────┘ │
│  模式   歌曲              ▾ │   ← 点击弹出选择
│  音质   极高              ▾ │
│                            │
│  ┌──────────────────────┐  │
│  │ 封面 │ 歌名           │  │
│  │      │ 歌手 · 网易云   │  │
│  │      │ [播放]  [下载]  │  │
│  └──────────────────────┘  │
│                            │
│   🔍 搜索   ▶ 播放   ⬇ 下载 │   ← 悬浮导航
└────────────────────────────┘
```

---

## 音质与解析原理

解析走两条链路，按音质分流：

### ① 官方接口（标准 / 较高 / 极高）

```
GET https://music.163.com/api/song/enhance/player/url?ids=[<id>]&br=<128000|192000|320000>
```

- 匿名可用，**最高 320K MP3**
- 返回体包含 level / br / size / type，据此显示真实音质
- -110 / 404 等错误码会转成明确提示

### ② 无损解析服务（无损 / Hi-Res / 超清母带）

```
POST https://nextmusic.toubiec.cn/api/ip
POST https://nextmusic.toubiec.cn/api/getSongUrl   { id, level, timestamp, ip }
POST https://nextmusic.toubiec.cn/api/getMusicUrl  { id, level, timestamp, ip }
POST https://nextmusic.toubiec.cn/api/getSongInfo  { id, timestamp, ip }
```

- 服务端持有会员账号，可返回 **FLAC 无损直链**（20~45MB）
- 该服务由**两个账号轮换**，偶发返回 url: null → 客户端**自动重试 4 次**（间隔 700ms）
- 音质等级采用**等级比较**：返回档次 ≥ 所选档次才接受，避免「选无损给 320K」却无提示

> 该解析服务为第三方公共服务，稳定性不受本项目控制；失效时无损档会报错，标准档仍可用。

---

## 下载与文件标签

### 保存位置

```
/storage/emulated/0/Download/耳虫音乐/<歌名 - 歌手>.<flac|mp3>
```

| 系统版本 | 写入方式 |
| --- | --- |
| Android 10+ | **MediaStore Downloads**（IS_PENDING 原子写入，无需存储权限） |
| Android 9 及以下 | 直接写入公共目录 + MediaScanner 扫描入库（需存储权限） |

### 标签写入

**FLAC**：与 MusicParser 工具一致的管线

```
源文件：[STREAMINFO, SEEKTABLE, VORBIS_COMMENT, PADDING]
输出：  [STREAMINFO, SEEKTABLE, PADDING, VORBIS_COMMENT, PICTURE]
```

- vendor = "MusicParser"
- TITLE / ARTIST / ALBUM / LYRICS
- PICTURE：type=3、mime=image/jpg、desc=Cover、w/h=0、depth=24、colors=0

**MP3**：ID3v2.4（TIT2 / TPE1 / TALB / USLT / APIC）

**封面处理**：网易云 CDN 的 .jpg 实际常返回 **PNG**，因此会先解码再统一转码为 JPEG 后写入（避免部分文件缺封面）。

---

## 项目结构

```
app/src/main/java/com/zhaoguanghao/earbugmusic/
├── MainActivity.kt            # 单 Activity + 三个页面 + 模式/音质选择
├── PlayerCore.kt              # 播放核心单例（StateFlow 状态）
├── PlaybackController.kt      # 播放页/歌词页渲染与手势
├── PlaybackService.kt         # 前台服务 + 通知栏/锁屏控制
├── model/                     # Song / Quality / ParseMode / Comment …
├── parser/
│   ├── NeteaseParser.kt       # 官方接口 + 解析服务 + 歌词 + 评论
│   ├── LinkParser.kt          # 链接/ID 识别与分模式解析
│   └── ParserRegistry.kt
├── download/
│   ├── DownloadManager.kt     # 串行队列 / 限流进度 / 持久化
│   ├── DownloadHelper.kt      # 直链下载 + 标签写入 + 落盘
│   └── DownloadTask.kt
├── tag/                       # FlacTagger / Id3Tagger / MetadataWriter
├── ui/                        # 适配器 / 评论弹窗 / 选择弹窗 / 圆角绘制
├── util/                      # 网络、歌词、图片、存储、本地索引
└── net/Http.kt                # OkHttp 封装（GET/POST/下载/重试）
```

---

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 1.9.24 |
| 构建 | Gradle 8.7 + AGP 8.5.2，JDK 17 |
| SDK | compileSdk / targetSdk 34，minSdk 24 |
| UI | Material 3、View 体系、RecyclerView |
| 异步 | Kotlin Coroutines + StateFlow |
| 网络 | OkHttp 4.12 |
| 媒体 | MediaPlayer + MediaSessionCompat |
| 存储 | MediaStore（Q+）/ 直接文件（≤P） |

---

## 构建与运行

### 前置要求

- JDK 17
- Android SDK Platform 34 + Build-Tools 34.0.0
- Gradle 8.7

### 命令

```bash
git clone <your-repo-url>
cd EarBug_music

# 设置 SDK 路径
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 构建 Debug APK
./gradlew assembleDebug

# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 使用 Gradle Wrapper

项目已内置 Wrapper（distribution 走腾讯云镜像，国内下载更快）：

```bash
./gradlew assembleDebug        # Linux / macOS
gradlew.bat assembleDebug      # Windows
```

### 版本号规则

- versionName = 1.1.<buildNumber>
- versionCode = 10000 + buildNumber
- buildNumber 记录在 version.properties，**每次构建自动 +1**

---

## 使用说明

1. 选择**模式**（歌曲 / 专辑 / 歌单），或点右上角 📊 加载热歌榜
2. 选择**音质**（默认「标准」，可切换「极高 / 无损」等）
3. 输入 ID / 链接 / 关键词 → 点搜索
   - 专辑、歌单会自动把全部歌曲加入下载队列
   - 若本地已有同名歌曲，会弹窗确认是否重新下载
4. 在「下载」页查看进度；点 ✖ 可取消，完成后点 ▶ 播放
5. 播放页下滑进入歌词页；右上角可看评论；通知栏可控制播放

---

## 已知限制

- **无损依赖第三方解析服务**：该服务偶发返回空，客户端已做重试；仍失败时无损档不可用
- **匿名官方接口最高 320K**：Hi-Res / 母带通常需要会员账号
- **部分版权曲目无法获取**：会返回 -110 / 404 等错误码
- 仅支持网易云，QQ音乐 / 酷狗已在重构中移除
- 评论、热歌榜来自网易云公开接口，接口变动可能导致功能失效

---

## 更新日志

完整记录见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

**近期重点**

- v1.1.35 包名修正为 com.zhaoguanghao.earbugmusic；弹窗/页面切换动画改用硬件层 + 更短时长
- v1.1.30 修复弹窗透明底（圆角绘制改为系统实现）；去掉下拉三角
- v1.1.27 下滑打开歌词；重复下载确认；淡灰配色
- v1.1.24 歌词滚动稳定（固定行高 + 精确居中 + 拖动暂停）
- v1.1.21 逐行高亮滚动歌词
- v1.1.17 无损请求自动重试，解决偶发解析失败
- v1.1.14 通知栏停止按钮 + 四种播放模式
- v1.1.7 系统通知栏 / 锁屏媒体控制
- v1.0.0 首个版本

---

## 发布到 GitHub

```bash
cd EarBug_music
git init
git add .
git commit -m "feat: 耳虫音乐 v1.1.35"

# 注意：local.properties（本地 SDK 路径）已在 .gitignore 中排除
git remote add origin https://github.com/<your-name>/earbug-music.git
git branch -M main
git push -u origin main
```

- 仓库已包含 GitHub Actions 配置（`.github/workflows/android.yml`），推送后会自动构建 Debug APK 并上传为构建产物
- 若想显示构建徽章，可在 README 顶部加入：
  `![Build](https://github.com/<your-name>/earbug-music/actions/workflows/android.yml/badge.svg)`

---

## 免责声明

- 本项目**仅供学习交流**，不得用于商业用途。
- 所有音乐内容版权归网易云音乐及版权方所有，请通过官方渠道支持正版。
- 本项目不存储任何音乐文件，仅进行链接解析与本地下载。
- 使用本工具产生的任何后果由使用者自行承担。

---

## License

[MIT](LICENSE)
