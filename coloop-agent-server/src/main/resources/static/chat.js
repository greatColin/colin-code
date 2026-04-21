(function() {
    const chatContainer = document.getElementById('chat-container');
    const messageInput = document.getElementById('message-input');
    const sendBtn = document.getElementById('send-btn');
    const statusEl = document.getElementById('connection-status');

    const wsUrl = 'ws://' + window.location.host + '/ws/agent';
    let ws = null;
    let reconnectTimer = null;

    function connect() {
        updateStatus('connecting', '连接中...');
        ws = new WebSocket(wsUrl);

        ws.onopen = function() {
            updateStatus('connected', '已连接');
            enableInput(true);
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
        };

        ws.onmessage = function(event) {
            try {
                const msg = JSON.parse(event.data);
                handleMessage(msg);
            } catch (e) {
                console.error('Failed to parse message:', e);
            }
        };

        ws.onclose = function() {
            updateStatus('disconnected', '已断开');
            enableInput(false);
            reconnectTimer = setTimeout(connect, 3000);
        };

        ws.onerror = function(err) {
            console.error('WebSocket error:', err);
        };
    }

    function updateStatus(clazz, text) {
        statusEl.className = 'status ' + clazz;
        statusEl.textContent = text;
    }

    function enableInput(enabled) {
        messageInput.disabled = !enabled;
        sendBtn.disabled = !enabled;
    }

    function handleMessage(msg) {
        switch (msg.type) {
            case 'user':
                renderUser(msg.payload.content);
                break;
            case 'loop_start':
                renderLoopStart(msg.payload.attempt);
                break;
            case 'thinking':
                renderThinking(msg.payload);
                break;
            case 'tool_call':
                renderToolCall(msg.payload);
                break;
            case 'tool_result':
                renderToolResult(msg.payload);
                break;
            case 'assistant':
                renderAssistant(msg.payload.content);
                break;
            case 'system':
                renderSystem(msg.payload.message);
                break;
            case 'error':
                renderError(msg.payload.message);
                break;
        }
        scrollToBottom();
    }

    function appendElement(el) {
        chatContainer.appendChild(el);
    }

    function scrollToBottom() {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    function renderUser(content) {
        const el = document.createElement('div');
        el.className = 'message user';
        el.textContent = content;
        appendElement(el);
    }

    function renderAssistant(content) {
        const el = document.createElement('div');
        el.className = 'message assistant';
        el.textContent = content;
        appendElement(el);
    }

    function renderLoopStart(attempt) {
        const el = document.createElement('div');
        el.className = 'message loop-start';
        el.textContent = '▶ Attempt ' + attempt + '...';
        appendElement(el);
    }

    function renderSystem(message) {
        const el = document.createElement('div');
        el.className = 'message system';
        el.textContent = message;
        appendElement(el);
    }

    function renderError(message) {
        const el = document.createElement('div');
        el.className = 'message error';
        el.textContent = '⚠ ' + message;
        appendElement(el);
    }

    function renderThinking(payload) {
        let content = '';
        if (payload.reasoning) {
            content += '[REASONING]\n' + payload.reasoning + '\n\n';
        }
        if (payload.content) {
            content += '[THINK]\n' + payload.content;
        }
        renderCard('thinking', '💭 Thinking', content);
    }

    function renderToolCall(payload) {
        let content = 'Name: ' + payload.name + '\n';
        if (payload.fullArgs) {
            content += 'Args:\n' + payload.fullArgs;
        } else if (payload.args) {
            content += 'Args:\n' + payload.args;
        }
        renderCard('tool-call', '🔧 ' + payload.name, content);
    }

    function renderToolResult(payload) {
        renderCard('tool-result', '✅ Result: ' + payload.name, payload.result || '');
    }

    function renderCard(type, title, bodyContent) {
        const card = document.createElement('div');
        card.className = 'card ' + type;

        const header = document.createElement('div');
        header.className = 'card-header';

        const titleEl = document.createElement('span');
        titleEl.className = 'card-title';
        titleEl.textContent = title;

        const toggle = document.createElement('span');
        toggle.className = 'card-toggle';
        toggle.textContent = '▼';

        header.appendChild(titleEl);
        header.appendChild(toggle);

        // Preview: first line or first 80 chars
        const preview = document.createElement('div');
        preview.className = 'card-preview';
        const previewText = bodyContent ? bodyContent.split('\n')[0].substring(0, 80) : '';
        preview.textContent = previewText + (bodyContent && bodyContent.length > 80 ? '...' : '');

        const body = document.createElement('div');
        body.className = 'card-body';
        body.textContent = bodyContent;

        // Default collapsed: show preview, hide body
        body.classList.add('collapsed');
        toggle.textContent = '▶';

        header.addEventListener('click', function() {
            const isCollapsed = body.classList.contains('collapsed');
            if (isCollapsed) {
                body.classList.remove('collapsed');
                preview.classList.add('collapsed');
                toggle.textContent = '▼';
            } else {
                body.classList.add('collapsed');
                preview.classList.remove('collapsed');
                toggle.textContent = '▶';
            }
        });

        card.appendChild(header);
        card.appendChild(preview);
        card.appendChild(body);
        appendElement(card);
    }

    function sendMessage() {
        const text = messageInput.value.trim();
        if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;

        ws.send(JSON.stringify({ action: 'chat', message: text }));
        messageInput.value = '';
    }

    sendBtn.addEventListener('click', sendMessage);

    messageInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // Start connection
    connect();
})();
