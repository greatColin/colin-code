(function() {
    const chatContainer = document.getElementById('chat-container');
    const messageInput = document.getElementById('message-input');
    const sendBtn = document.getElementById('send-btn');
    const statusEl = document.getElementById('connection-status');
    const commandSuggestionsEl = document.getElementById('command-suggestions');

    const wsUrl = 'ws://' + window.location.host + '/ws/agent';
    let ws = null;
    let reconnectTimer = null;
    let availableCommands = [];
    let selectedSuggestionIndex = -1;

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
            case 'commands':
                availableCommands = msg.payload.commands || [];
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
        hideSuggestions();
    }

    sendBtn.addEventListener('click', sendMessage);

    messageInput.addEventListener('keydown', function(e) {
        if (commandSuggestionsEl.classList.contains('active')) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                selectedSuggestionIndex++;
                updateSelection();
                return;
            }
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                selectedSuggestionIndex--;
                updateSelection();
                return;
            }
            if (e.key === 'Enter') {
                e.preventDefault();
                applySelectedSuggestion();
                return;
            }
            if (e.key === 'Escape') {
                hideSuggestions();
                return;
            }
        }

        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    messageInput.addEventListener('input', function() {
        const text = messageInput.value;
        if (text.startsWith('/')) {
            const filter = text.substring(1).toLowerCase();
            showSuggestions(filter);
        } else {
            hideSuggestions();
        }
    });

    messageInput.addEventListener('blur', function() {
        // Delay hiding to allow click on suggestion
        setTimeout(hideSuggestions, 200);
    });

    function showSuggestions(filter) {
        if (!availableCommands.length) return;

        const filtered = availableCommands.filter(function(cmd) {
            return cmd.name.toLowerCase().indexOf(filter) !== -1 ||
                   (cmd.description || '').toLowerCase().indexOf(filter) !== -1;
        });

        if (!filtered.length) {
            hideSuggestions();
            return;
        }

        commandSuggestionsEl.innerHTML = '';
        selectedSuggestionIndex = 0;

        filtered.forEach(function(cmd, index) {
            const item = document.createElement('div');
            item.className = 'command-suggestion-item';
            item.dataset.index = index;
            item.dataset.name = cmd.name;

            const nameSpan = document.createElement('span');
            nameSpan.className = 'command-suggestion-name';
            nameSpan.textContent = '/' + cmd.name;

            const descSpan = document.createElement('span');
            descSpan.className = 'command-suggestion-desc';
            descSpan.textContent = cmd.description || '';

            item.appendChild(nameSpan);
            item.appendChild(descSpan);

            item.addEventListener('mousedown', function(e) {
                e.preventDefault();
                messageInput.value = '/' + cmd.name + ' ';
                messageInput.focus();
                hideSuggestions();
            });

            commandSuggestionsEl.appendChild(item);
        });

        commandSuggestionsEl.classList.add('active');
        updateSelection();
    }

    function hideSuggestions() {
        commandSuggestionsEl.classList.remove('active');
        commandSuggestionsEl.innerHTML = '';
        selectedSuggestionIndex = -1;
    }

    function updateSelection() {
        const items = commandSuggestionsEl.querySelectorAll('.command-suggestion-item');
        if (!items.length) return;

        if (selectedSuggestionIndex < 0) {
            selectedSuggestionIndex = items.length - 1;
        }
        if (selectedSuggestionIndex >= items.length) {
            selectedSuggestionIndex = 0;
        }

        items.forEach(function(item, idx) {
            if (idx === selectedSuggestionIndex) {
                item.classList.add('selected');
                item.scrollIntoView({ block: 'nearest' });
            } else {
                item.classList.remove('selected');
            }
        });
    }

    function applySelectedSuggestion() {
        const items = commandSuggestionsEl.querySelectorAll('.command-suggestion-item');
        if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < items.length) {
            const name = items[selectedSuggestionIndex].dataset.name;
            messageInput.value = '/' + name + ' ';
        }
        hideSuggestions();
        messageInput.focus();
    }

    // Start connection
    connect();
})();
