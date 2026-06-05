#!/usr/bin/env sh
# 一次性启用本仓库的 git 钩子(克隆后运行一次即可)。
# 之后切换 dev / hotfix / release 分支会自动选择对应 Spring profile。
set -e
repo_root=$(git rev-parse --show-toplevel)
git -C "$repo_root" config core.hooksPath .githooks
chmod +x "$repo_root/.githooks/"* 2>/dev/null || true
# 立即为当前分支生成一次 profile 覆盖文件
sh "$repo_root/.githooks/post-checkout" "" "$(git -C "$repo_root" rev-parse HEAD)" 1
echo "已启用 .githooks(core.hooksPath=.githooks)。"
