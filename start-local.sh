#!/bin/bash
# 一键启动本地开发环境（后端 + 前端）
# 用法: ./start-local.sh

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 检查 AI_API_KEY
if [ -z "$AI_API_KEY" ]; then
    echo "⚠  未设置 AI_API_KEY 环境变量，AI 对话功能将不可用"
    echo "   可通过 export AI_API_KEY=your-key 设置"
fi

# 检查数据库文件
if [ ! -f "$PROJECT_DIR/roco_encyclopedia.db" ]; then
    echo "❌ 数据库文件不存在: $PROJECT_DIR/roco_encyclopedia.db"
    exit 1
fi

# 启动后端（Spring Boot，激活 local profile）
echo "🚀 启动后端 (localhost:8081) ..."
cd "$PROJECT_DIR/backend"
mvn spring-boot:run -Dspring-boot.run.profiles=local &
BACKEND_PID=$!

# 等后端端口就绪
echo "⏳ 等待后端启动 ..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8081/api/v1/data/pets/count > /dev/null 2>&1; then
        echo "✅ 后端已就绪"
        break
    fi
    sleep 2
done

# 启动前端
echo "🚀 启动前端 (localhost:5173) ..."
cd "$PROJECT_DIR/frontend"
npm install --silent 2>/dev/null
npm run dev &
FRONTEND_PID=$!

echo ""
echo "========================================="
echo "  后端: http://localhost:8081"
echo "  前端: http://localhost:5173"
echo "  百科: http://localhost:5173/encyclopedia.html"
echo "========================================="
echo "按 Ctrl+C 停止所有服务"

# 捕获退出信号，同时关闭前后端
cleanup() {
    echo ""
    echo "正在停止服务 ..."
    kill $FRONTEND_PID 2>/dev/null
    kill $BACKEND_PID 2>/dev/null
    wait
    echo "已停止"
}
trap cleanup INT TERM

wait
