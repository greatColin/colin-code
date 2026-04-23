(function() {
    // --- Think tag extraction ---
    function extractThinkBlocks(content) {
        if (!content) return { thinkContent: '', remainingContent: '' };
        var thinkRegex = /<think>([\s\S]*?)<\/think>/gi;
        var thinkParts = [];
        var match;
        while ((match = thinkRegex.exec(content)) !== null) {
            thinkParts.push(match[1].trim());
        }
        var remainingContent = content.replace(thinkRegex, '').trim();
        return {
            thinkContent: thinkParts.join('\n\n'),
            remainingContent: remainingContent
        };
    }

    // --- Markdown rendering with XSS protection ---
    function renderMarkdown(content) {
        if (!content) return '';
        // Check if libraries are loaded (fallback for offline)
        if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
            // Fallback: plain text with BR for newlines
            return content.replace(/&/g, '&amp;')
                          .replace(/</g, '&lt;')
                          .replace(/>/g, '&gt;')
                          .replace(/\n/g, '<br>');
        }
        // Configure marked
        marked.setOptions({
            breaks: true,
            gfm: true,
            headerIds: false,
            mangle: false
        });
        var rawHtml = marked.parse(content);
        var safeHtml = DOMPurify.sanitize(rawHtml, {
            ALLOWED_TAGS: [
                'p', 'br', 'hr',
                'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
                'ul', 'ol', 'li',
                'strong', 'em', 'del', 'code', 'pre',
                'a', 'img',
                'blockquote',
                'table', 'thead', 'tbody', 'tr', 'th', 'td',
                'div', 'span'
            ],
            ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'target']
        });
        return safeHtml;
    }

    // --- Apply syntax highlighting to code blocks within an element ---
    function highlightCodeBlocks(container) {
        if (typeof hljs === 'undefined') return;
        var codeBlocks = container.querySelectorAll('pre code');
        codeBlocks.forEach(function(block) {
            hljs.highlightElement(block);
        });
    }

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

    // --- Streaming state ---
    let currentAssistantEl = null;
    let streamBuffer = '';
    let lastRenderTime = 0;
    const STREAM_RENDER_INTERVAL = 100;   // ms
    const STREAM_RENDER_MIN_CHARS = 50;   // chars
    let streamRenderTimer = null;

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
            case 'stream_chunk':
                appendStreamChunk(msg.payload.content);
                break;
            case 'assistant':
                finalizeAssistant(msg.payload.content);
                break;
            case 'system':
                renderSystem(msg.payload.message);
                break;
            case 'error':
                renderError(msg.payload.message);
                break;
            case 'context_usage':
                updateContextBar(msg.payload);
                break;
            case 'commands':
                availableCommands = (msg.payload && msg.payload.commands) || [];
                console.log('[Commands] Loaded', availableCommands.length, 'commands:', availableCommands.map(function(c) { return c.name; }));
                break;
        }
        scrollToBottom();
    }

    function appendElement(el) {
        chatContainer.appendChild(el);
    }

    function insertBeforeAssistant(el) {
        if (currentAssistantEl && currentAssistantEl.parentNode === chatContainer) {
            chatContainer.insertBefore(el, currentAssistantEl);
        } else {
            chatContainer.appendChild(el);
        }
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

    function appendStreamChunk(chunk) {
        if (!chunk) return;

        if (!currentAssistantEl) {
            currentAssistantEl = document.createElement('div');
            currentAssistantEl.className = 'message assistant';
            currentAssistantEl.innerHTML = '<span class="stream-cursor"></span>';
            appendElement(currentAssistantEl);
        }

        streamBuffer += chunk;

        var now = Date.now();
        if (now - lastRenderTime > STREAM_RENDER_INTERVAL || streamBuffer.length > STREAM_RENDER_MIN_CHARS) {
            renderStreamBuffer();
        } else if (!streamRenderTimer) {
            streamRenderTimer = setTimeout(function() {
                renderStreamBuffer();
            }, STREAM_RENDER_INTERVAL);
        }

        scrollToBottom();
    }

    function renderStreamBuffer() {
        if (!currentAssistantEl) return;

        lastRenderTime = Date.now();
        streamRenderTimer = null;

        var html = renderMarkdown(streamBuffer);
        currentAssistantEl.innerHTML = html + '<span class="stream-cursor"></span>';
        highlightCodeBlocks(currentAssistantEl);
    }

    function finalizeAssistant(fullContent) {
        // Clear any pending render timer
        if (streamRenderTimer) {
            clearTimeout(streamRenderTimer);
            streamRenderTimer = null;
        }

        // Extract think blocks from the full content
        var extracted = extractThinkBlocks(fullContent || '');

        // Render think content as a thinking card (if any)
        if (extracted.thinkContent) {
            renderCardBeforeAssistant('thinking', '💭 Thinking', extracted.thinkContent);
        }

        if (currentAssistantEl) {
            // Update with final rendered content (no cursor)
            var html = renderMarkdown(extracted.remainingContent);
            currentAssistantEl.innerHTML = html;
            highlightCodeBlocks(currentAssistantEl);
            currentAssistantEl = null;
            streamBuffer = '';
        } else {
            // Fallback: no stream chunks received, render as before
            var el = document.createElement('div');
            el.className = 'message assistant';
            el.innerHTML = renderMarkdown(extracted.remainingContent);
            appendElement(el);
            highlightCodeBlocks(el);
        }
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
        renderCardBeforeAssistant('thinking', '💭 Thinking', content);
    }

    function renderToolCall(payload) {
        let content = 'Name: ' + payload.name + '\n';
        if (payload.fullArgs) {
            content += 'Args:\n' + payload.fullArgs;
        } else if (payload.args) {
            content += 'Args:\n' + payload.args;
        }
        renderCardBeforeAssistant('tool-call', '🔧 ' + payload.name, content);
    }

    function renderToolResult(payload) {
        renderCardBeforeAssistant('tool-result', '✅ Result: ' + payload.name, payload.result || '');
    }

    function renderCardBeforeAssistant(type, title, bodyContent) {
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
        insertBeforeAssistant(card);
    }

    function updateContextBar(payload) {
        const tokens = payload.tokens || 0;
        const limit = payload.limit || 1;
        const percent = payload.percent || 0;

        const valueEl = document.getElementById('context-value');
        const fillEl = document.getElementById('context-progress-fill');
        if (!valueEl || !fillEl) return;

        function formatNum(n) {
            if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
            if (n >= 1000) return (n / 1000).toFixed(0) + 'K';
            return String(n);
        }

        valueEl.textContent = formatNum(tokens) + ' / ' + formatNum(limit) + ' (' + percent + '%)';
        fillEl.style.width = percent + '%';

        fillEl.classList.remove('low', 'mid', 'high');
        if (percent > 80) {
            fillEl.classList.add('high');
        } else if (percent >= 50) {
            fillEl.classList.add('mid');
        } else {
            fillEl.classList.add('low');
        }
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
        console.log('[Suggestions] showSuggestions called, filter="' + filter + '", availableCommands=' + availableCommands.length);
        if (!availableCommands.length) {
            console.log('[Suggestions] No commands available yet');
            return;
        }

        const filtered = availableCommands.filter(function(cmd) {
            if (!cmd || !cmd.name) return false;
            return cmd.name.toLowerCase().indexOf(filter) !== -1 ||
                   (cmd.description || '').toLowerCase().indexOf(filter) !== -1;
        });

        console.log('[Suggestions] Filtered', filtered.length, 'commands');

        if (!filtered.length) {
            hideSuggestions();
            return;
        }

        if (!commandSuggestionsEl) {
            console.error('[Suggestions] command-suggestions element not found');
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
