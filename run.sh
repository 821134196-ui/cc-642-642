#!/usr/bin/env bash
# 百货礼盒拆包退货与组件复原系统 —— 一条命令启动
#
# 用法：
#   ./run.sh            # 构建（如需）并启动，访问 http://localhost:8080
#   ./run.sh --dev      # 前后端开发模式（前端热更新 :5173，后端 :8080）
#   ./run.sh --rebuild  # 强制重新构建前后端后启动
#
# 环境：需要 JDK 17+、Maven、Node 18+；若系统未提供 JDK/Maven，
#      会自动使用 $HOME/tools 下的本地安装。
set -euo pipefail
cd "$(dirname "$0")"

# ---- 1) 工具链：优先系统，其次 ~/tools ----
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/tools/jdk-17.0.12+7" ]; then
  export JAVA_HOME="$HOME/tools/jdk-17.0.12+7"
fi
if [ -d "$HOME/tools/apache-maven-3.9.9" ]; then
  MVN="$HOME/tools/apache-maven-3.9.9/bin/mvn"
else
  MVN="mvn"
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$HOME/tools/apache-maven-3.9.9/bin:$PATH"

java -version >/dev/null 2>&1 || { echo "未找到 JDK 17+，请先安装"; exit 1; }

# SQLite/Xerial 不会自动创建数据库父目录，启动前确保数据目录存在
mkdir -p data data/photos

MODE="run"
[ "${1:-}" = "--dev" ] && MODE="dev"
[ "${1:-}" = "--rebuild" ] && MODE="rebuild"

# ---- 2) 前端：安装依赖并构建到后端静态资源目录 ----
FRONTEND_DIR="src/main/resources/META-INF/resources"
need_frontend=0
[ "$MODE" = "rebuild" ] && need_frontend=1
[ ! -f "$FRONTEND_DIR/index.html" ] && need_frontend=1
if [ "$MODE" = "dev" ]; then
  ( cd frontend && [ -d node_modules ] || npm install )
fi
if [ "$need_frontend" = "1" ] && [ "$MODE" != "dev" ]; then
  echo "==> 构建 Svelte 前端"
  ( cd frontend && [ -d node_modules ] || npm install && npm run build )
fi

# ---- 3) 后端：打包可执行 Quarkus 应用 ----
if [ "$MODE" != "dev" ]; then
  if [ "$MODE" = "rebuild" ] || [ ! -f target/quarkus-app/quarkus-run.jar ]; then
    echo "==> 打包 Quarkus 后端"
    "$MVN" -q -B package -DskipTests -Dmaven.artifact.threads=1
  fi

  echo "==> 启动系统：http://localhost:8080 （Ctrl+C 停止）"
  echo "    SQLite 数据库: data/giftbox.db    照片目录: data/photos"
  exec java -jar target/quarkus-app/quarkus-run.jar
else
  echo "==> 开发模式：后端 http://localhost:8080，前端 http://localhost:5173"
  ( cd frontend && npm run dev ) &
  FRONT_PID=$!
  trap 'kill $FRONT_PID 2>/dev/null || true' EXIT
  exec "$MVN" quarkus:dev
fi
