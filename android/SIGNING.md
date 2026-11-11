# GoJapan 固定签名

从 `v1.0-fixes-20261111` 起，发布必须沿用证书 SHA-256：

`a47f4395e10d8aae0e8442d54c5150a5c60ebb538c5537d13a9d04be3e111add`

这是已发布新版 APK 的原有密钥，重新封装为随机密码保护的 PKCS12；没有生成新密钥对。不要替换它，也不要用另一台机器自动生成的 debug.keystore 发布。后续更新必须提高 versionCode。

## 当前工作区保存位置

- 私钥：`/workspace/.secrets/gojapan-signing/gojapan-fixed.p12`
- 私密配置：`/workspace/.secrets/gojapan-signing/credentials.json`
- 目录权限 0700，文件权限 0600。均位于仓库之外，不得提交、上传 release 或打印内容。
- 工作区存储不等于永久备份。当前 GitHub 连接的 Secrets API 返回 403，尚未保存至 GitHub Secrets。

本地构建：

```bash
source /workspace/android-env.sh
python3 android/tools/build-signed.py --config /workspace/.secrets/gojapan-signing/credentials.json testDebugUnitTest lintDebug assembleDebug assembleRelease
```

脚本在构建前核对证书指纹，构建后核对 APK 签名。release 构建不允许缺少固定签名凭据；普通开发 debug 包不得直接作为 release 上传。

## GitHub Actions 持久保存

授予 GitHub 连接本仓库的 Secrets 读写权限后，将相同密钥加密保存为以下 Actions Secrets（不要将值粘贴到 issue 或仓库文件）：

- `GOJAPAN_SIGNING_KEYSTORE_BASE64`：PKCS12 文件的单行 base64。
- `GOJAPAN_SIGNING_STORE_PASSWORD`：私密配置中的 storePassword。
- `GOJAPAN_SIGNING_KEY_ALIAS`：私密配置中的 keyAlias。
- `GOJAPAN_SIGNING_KEY_PASSWORD`：私密配置中的 keyPassword。

发布 workflow 只使用这些 secrets，缺失即失败，不自动创建替代密钥。`android/signing-certificate.sha256` 保存可公开的证书指纹，不能据此恢复私钥。
