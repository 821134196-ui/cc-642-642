#!/usr/bin/env bash
# 百货礼盒拆包退货与组件复原系统 —— 一键启动
# 用法：./start.sh   （首次运行会下载依赖，耗时较长）
set -euo pipefail
cd "$(dirname "$0")"

# ---- 工具链：优先系统 Java/Maven，否则使用本地 .toolchain ----
if [ -z "${JAVA_HOME:-}" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  else
    JAVA_HOME="$PWD/.toolchain/jdk"
  fi
  export JAVA_HOME
fi
export PATH="$JAVA_HOME/bin:$PATH"
if ! command -v mvn >/dev/null 2>&1; then
  export PATH="$PWD/.toolchain/maven/bin:$PATH"
fi

echo "==> Java: $(java -version 2>&1 | head -1)"

# ---- 数据目录（SQLite 库与检查照片） ----
mkdir -p backend/data/photos

# ---- 构建前端并交给后端托管 ----
if [ ! -d frontend/node_modules ]; then
  echo "==> 安装前端依赖"
  (cd frontend && npm install --no-audit --no-fund)
fi
echo "==> 构建前端"
(cd frontend && npm run build)
rm -rf backend/src/main/resources/META-INF/resources
mkdir -p backend/src/main/resources/META-INF/resources
cp -r frontend/dist/. backend/src/main/resources/META-INF/resources/

# ---- 打包并启动后端（前后端同源，端口 8080） ----
echo "==> 打包后端"
(cd backend && mvn -q -B -DskipTests package)

echo "==> 启动：http://localhost:8080  （Ctrl+C 停止）"
cd backend
exec java -jar target/quarkus-app/quarkus-run.jar
