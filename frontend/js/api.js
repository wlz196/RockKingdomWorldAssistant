// 后端地址配置
const BACKEND_HOST = window.location.hostname || 'localhost';
const API_BASE = `http://${BACKEND_HOST}:8081/api/v1/data`;
const MEDIA_BASE = `http://${BACKEND_HOST}:8081/media/`;

// 通用防抖函数
function debounce(func, wait) {
    let timeout;
    return (...args) => {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}
