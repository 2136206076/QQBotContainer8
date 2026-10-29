#!/data/data/com.termux/files/usr/bin/bash
# 全新仓库一键部署：本地重新 init → 建 GitHub 新仓库 → 推送 → 触发云端编译
#
# 为什么要新仓库：旧仓库里混着根目录的 build-apk.yml（GitHub 不会执行它，但容易混淆），
# 而且 .git 丢过一次。新仓库从零开始，最干净。
#
# 用法（Termux 里，工程已解压到 ~/ 下任意位置）：
#   bash tools/new-repo.sh              # 仓库名默认 QQBotContainer2
#   bash tools/new-repo.sh MyBotName    # 自定义仓库名
set -e

REPO=${1:-QQBotContainer2}
EMAIL=${EMAIL:-2136206076@users.noreply.github.com}

say() { printf '\n\033[36m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[31m✗ %s\033[0m\n' "$*"; exit 1; }

say "1/5 定位工程目录"
CFG=$(find "$HOME" -maxdepth 5 -path "*/.github/workflows/build-apk.yml" 2>/dev/null | head -1)
[ -n "$CFG" ] || die "没找到工程（应在 ~/ 下某个含 .github/workflows/build-apk.yml 的目录）"
D=${CFG%/.github/workflows/build-apk.yml}
echo "  · $D"
cd "$D"

say "2/5 转义修复（幂等，多跑几次不会坏）"
python3 - <<'PY'
p = 'app/src/main/java/com/yuanbao/qqbot/Installer.kt'
s = open(p).read()
# 把 bash 变量 $SRC/$PKG 转义成 Kotlin 能原样输出的 ${'$'}SRC
# 已修复过的话字符串里不再含裸 $SRC，所以重复执行是安全的
s = s.replace('$SRC', "${'$'}SRC").replace('$PKG', "${'$'}PKG")
open(p, 'w').write(s)
print('  ✓ Installer.kt 已修复')
PY

say "3/5 全新 git 仓库（不复用旧的 .git）"
rm -rf .git
git init -q
git add -A
git -c user.email="$EMAIL" -c user.name="$REPO" commit -q -m "QQBotContainer: 手机端 QQ 机器人容器 App"
echo "  ✓ 提交完成：$(git log --oneline -1)"

say "4/5 建 GitHub 仓库并推送"
gh repo create "$REPO" --public --source . --remote origin --push \
  || die "创建/推送失败。仓库名可能已被占用，换一个：bash tools/new-repo.sh $REPO-v2"

say "5/5 触发云端编译"
gh workflow run build-apk.yml 2>/dev/null || echo "  （稍后在网页手动 Run workflow 也行）"

USER=$(gh api user --jq .login 2>/dev/null || echo "你的用户名")
printf '\n\033[32m✓ 完成\033[0m\n'
echo
echo "  仓库   https://github.com/$USER/$REPO"
echo "  编译进度 https://github.com/$USER/$REPO/actions"
echo
echo "  等 5~8 分钟，变绿后：进那次运行 → 拉到底部 Artifacts → QQBotContainer-debug"
echo "  下载解压得到 app-debug.apk，点它安装。"
