# 把剪藏分享给别人 / 上传 GitHub

## 先说清楚：现在这个链接不能直接发人

`http://127.0.0.1:43865/...` 里的 `127.0.0.1` 是**本机地址**。别人手机上的 `127.0.0.1` 指的是别人自己的手机，所以这个链接只有你这台手机、且 DSH 还开着的时候能下载。

要发给别人，需要把 APK 放到一个**公网可访问**的地方。最推荐 **GitHub Releases**，其次是 Gitee Releases 或网盘。**不要**把 `127.0.0.1` 链接发出去。

另外：**先确认 2.1 诊断版不再闪退，再发布**。2.0 在你的 ColorOS 17 上会秒退，不要把它发给别人。

---

## 方式 A：只分享 APK（最快，不需要上传源码）

1. 在 GitHub 新建一个仓库（例如 `jiacang`），**Public**（私有仓库的 Release 附件别人也下不到）。
2. 进入仓库的 **Releases → Draft a new release**。
3. Tag 填 `v2.1`，标题填「剪藏 2.1」。
4. 把 `dist/jiacang-2.1.apk` 拖进附件区域；如果手机 GitHub App 不支持传附件，就用手机浏览器打开 GitHub 网页上传。
5. Publish release。得到的公网直链形如：

   ```text
   https://github.com/<你的用户名>/jiacang/releases/download/v2.1/jiacang-2.1.apk
   ```

6. 把这个链接发给别人即可。

Release 附件是永久地址（只要不删 release/仓库），比网盘链接稳定。Release notes 里建议写清楚：

- Android 10+
- 需要悬浮窗权限；剪切识别还需要无障碍权限
- 数据只存本机；DSH 同步只连 `127.0.0.1:3080`，没有 DSH 时不会发外网
- 安装时允许「未知来源」

---

## 方式 B：同时把源码放 GitHub

在 `global-clip/` 目录里：

```sh
cd global-clip
git init
git add .
git commit -m "feat: 剪藏 2.1"
git branch -M main
git remote add origin https://github.com/<你的用户名>/jiacang.git
git push -u origin main
```

首次 push 需要 GitHub 认证（PAT 或 SSH），不要把 token 写进命令历史/聊天。

**不要提交：**

- `dist/`（已在 `.gitignore`）
- `keys/`、`*.keystore`、`*.jks`（签名私钥，已在 `.gitignore`）
- `local.properties`、`.idea/`、`build/`

源码进仓库，安装包进 **Releases**，这样仓库不会因为几十 KB 的二进制变大，也方便版本管理。

### 签名 key 一定要备份

当前 APK 用的是项目里 `keys/debug.keystore`（从 `/tmp/abuild/keys/` 备份过来的）。**这个文件决定以后能不能覆盖升级**：

- 把它复制到安全的地方（电脑/加密盘），不要上传 GitHub；
- 签名密码在本地构建命令/`keys` 备份里，**不要写进仓库或 Release 说明**；
- 如果换 key，别人必须卸载旧版才能装新版。

要更正式一点，可以生成独立的 release key：

```sh
keytool -genkeypair -keystore release.keystore -alias jiacang \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass '<自己定的密码>' -keypass '<自己定的密码>'
```

然后 `KEYSTORE=release.keystore KS_PASS=... ./build.sh` 出包。**release.keystore 和密码只放本地，绝不进 Git。**

---

## 方式 C：GitHub Actions 自动发版（可选）

仓库里已经有 `.github/workflows/build-apk.yml`，它现在只构建 **debug APK** 并作为 Actions artifact 上传。注意：

- artifact 需要登录 GitHub 才能下载，**不是公开分享链接**，而且默认 90 天过期；
- 每次 CI 用新生成的 debug key，签名会变，用户无法覆盖升级。

要自动化公开发版，需要给 CI 一把**固定的签名 key**：

1. 把 `keys/debug.keystore`（或 `release.keystore`）做 base64：

   ```sh
   base64 -w0 keys/debug.keystore > keystore.b64
   ```

2. 在 GitHub 仓库 **Settings → Secrets and variables → Actions** 添加：
   - `KEYSTORE_BASE64`：上面那串 base64
   - `KEYSTORE_PASSWORD`：本地 keystore 的密码（只在 Secrets 里，不要写进文件）
   - `KEY_ALIAS`：`androiddebugkey`（或你的 release alias）
   - `KEY_PASSWORD`：同上（key 密码）

3. 在 `app/build.gradle.kts` 里加 signingConfig（从环境变量读），再加一个 `release.yml`：tag 触发构建、签名、用 `softprops/action-gh-release` 上传 APK。

这部分我可以帮你写好；你只需要在 GitHub 后台配 secrets。不要在 workflow 里打印密码或 base64。

---

## 方式 D：Gitee / 网盘 / 局域网

- **Gitee Releases**：国内访问通常更稳，流程和 GitHub Releases 类似；附件同样是公网链接。
- **123 云盘 / 夸克 / 百度网盘**：最快，但可能提示「文件违规/需要会员/链接过期」，不适合长期分发；APK 可能被扫描拦截。
- **局域网**：在手机上起一个绑定 `0.0.0.0` 的服务，同一 Wi-Fi 的人用 `http://<手机局域网IP>:端口/` 下载；只适合当面/同一网络，不能发公网。

---

## 公开发布前的检查清单

- [ ] 在 ColorOS 17 上实际安装、打开、复制、分类、重启后恢复都正常；
- [ ] 2.1 诊断版的临时改动已恢复：自动启动服务、主题、图标、崩溃兜底（兜底可保留，崩溃写文件建议只在调试版）；
- [ ] 给普通用户把 **DSH 同步默认关掉**，或至少把设置里的 DSH 文案写清楚（没有 DSH 的用户不需要它）；
- [ ] README 里去掉只有你这台设备才有的 `/tmp/abuild` 路径说明，保留 Gradle / GitHub Actions 构建；
- [ ] Release notes 说明悬浮窗、通知、电池、可选无障碍权限的用途；
- [ ] 决定许可证（MIT / Apache-2.0 / 不公开授权），仓库里放 `LICENSE`；
- [ ] 确认 `git status` 里没有 `keys/`、`*.keystore`、`dist/`；
- [ ] 先用一个朋友/备用手机试装一次 Release 附件，确认下载链接真的能打开。

---

## 一句话建议

**只分享安装包**：GitHub Releases 上传 APK 最快，不需要动代码。  
**想长期维护/开源**：源码推 GitHub，APK 放 Releases，固定签名 key 放本地/Secrets，CI 发版再加 release workflow。  
**现在先做的事**：装 2.1 诊断版确认不闪退；能用后我帮你把发布流程和 CI 配置整理好。
