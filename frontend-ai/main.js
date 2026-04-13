const messagesContainer = document.getElementById('messages');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');

// AI 对话服务端口 8082
const BACKEND_HOST = window.location.hostname || 'localhost';
const API_URL = `http://${BACKEND_HOST}:8082/api/v1/chat`;

function addMessage(content, isUser = false) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${isUser ? 'user' : 'system'}`;

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = isUser ? '👤' : '🤖';

    const contentDiv = document.createElement('div');
    contentDiv.className = 'content';
    contentDiv.textContent = content;

    msgDiv.appendChild(avatar);
    msgDiv.appendChild(contentDiv);
    messagesContainer.appendChild(msgDiv);

    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

async function handleSendMessage() {
    const text = userInput.value.trim();
    if (!text) return;

    addMessage(text, true);
    userInput.value = '';

    const typingIndicator = document.createElement('div');
    typingIndicator.className = 'typing';
    typingIndicator.textContent = '洛克战术大脑思考中...';
    messagesContainer.appendChild(typingIndicator);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.text();
        messagesContainer.removeChild(typingIndicator);
        addMessage(data);
    } catch (error) {
        console.error('Fetch error:', error);
        messagesContainer.removeChild(typingIndicator);
        addMessage('抱歉，连接服务器失败。请检查 AI 服务是否在线（端口 8082）。');
    }
}

sendBtn.addEventListener('click', handleSendMessage);

userInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        handleSendMessage();
    }
});

userInput.focus();
