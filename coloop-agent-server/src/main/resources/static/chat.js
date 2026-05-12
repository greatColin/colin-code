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
    const agentSidebarEl = document.getElementById('agent-sidebar');
    const agentListEl = document.getElementById('current-session-list');  // 修正: 使用正确的ID
    const agentSidebarToggleEl = document.getElementById('agent-sidebar-toggle');
    const taskListContainerEl = document.getElementById('task-list-container');
    const taskCountBadgeEl = document.getElementById('task-count-badge');
    const historyListEl = document.getElementById('history-list');
    const sessionTitleEl = document.getElementById('session-title');

    const wsUrl = 'ws://' + window.location.host + '/ws/agent';
    const STREAM_RENDER_INTERVAL = 80;
    const STREAM_RENDER_MIN_CHARS = 20;
    let ws = null;
    let reconnectTimer = null;
    let availableCommands = [];
    let selectedSuggestionIndex = -1;

    // --- Per-agent state ---
    const agentState = new Map();
    function ensureAgent(name, meta) {
        if (!agentState.has(name)) {
            agentState.set(name, {
                fragment: document.createDocumentFragment(),
                currentAssistantEl: null,
                streamBuffer: '',
                lastRenderTime: 0,
                streamRenderTimer: null,
                contextUsage: null,
                meta: meta || { name: name }
            });
        }
    }
    ensureAgent('main', { name: 'main' });
    let currentAgent = 'main';

    function appendToAgent(agentName, el) {
        var st = agentState.get(agentName);
        if (!st) return;
        if (agentName === currentAgent) {
            chatContainer.appendChild(el);
        } else {
            st.fragment.appendChild(el);
        }
    }

    function insertBeforeAssistant(agentName, el) {
        var st = agentState.get(agentName);
        if (!st) return;
        if (agentName === currentAgent && st.currentAssistantEl && st.currentAssistantEl.parentNode === chatContainer) {
            chatContainer.insertBefore(el, st.currentAssistantEl);
        } else {
            appendToAgent(agentName, el);
        }
    }

    function switchToAgent(name) {
        if (name === currentAgent) return;
        var curSt = agentState.get(currentAgent);
        if (curSt) {
            while (chatContainer.firstChild) {
                curSt.fragment.appendChild(chatContainer.firstChild);
            }
        }
        var targetSt = agentState.get(name);
        if (targetSt) {
            while (targetSt.fragment.firstChild) {
                chatContainer.appendChild(targetSt.fragment.firstChild);
            }
        }
        currentAgent = name;
        updateSidebarActive(name);
        var stCtx = agentState.get(name);
        updateContextBar(stCtx && stCtx.contextUsage);
        scrollToBottom();
    }

    function updateSidebarActive(name) {
        var items = agentListEl.querySelectorAll('.agent-item');
        items.forEach(function(item) {
            if (item.dataset.agent === name) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
    }

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
            // 请求历史记录列表
            ws.send(JSON.stringify({ action: 'list_history' }));
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
        var agent = msg.agentName || 'main';
        ensureAgent(agent);

        switch (msg.type) {
            case 'subagent_created':
                addAgentToSidebar(msg.payload);
                return;
            case 'subagent_cleared':
                removeAgentFromSidebar(msg.payload.name);
                if (currentAgent === msg.payload.name) {
                    switchToAgent('main');
                    renderSystem('Subagent \'' + msg.payload.name + '\' was cleared.');
                }
                agentState.delete(msg.payload.name);
                return;
            case 'user':
                renderUser(agent, msg.payload.content);
                break;
            case 'loop_start':
                renderLoopStart(agent, msg.payload.attempt);
                break;
            case 'thinking':
                renderThinking(agent, msg.payload);
                break;
            case 'tool_call':
                renderToolCall(agent, msg.payload);
                break;
            case 'tool_result':
                renderToolResult(agent, msg.payload);
                break;
            case 'stream_chunk':
                appendStreamChunk(agent, msg.payload.content);
                break;
            case 'assistant':
                finalizeAssistant(agent, msg.payload.content);
                break;
            case 'system':
                renderSystem(msg.payload.message);
                break;
            case 'error':
                renderError(msg.payload.message);
                break;
            case 'context_usage':
                var stCtx = agentState.get(agent);
                if (stCtx) {
                    stCtx.contextUsage = msg.payload;
                }
                if (agent === currentAgent) {
                    updateContextBar(msg.payload);
                }
                break;
            case 'new_session':
                renderNewSession();
                break;
            case 'commands':
                availableCommands = (msg.payload && msg.payload.commands) || [];
                break;
            case 'task_list':
                renderTaskList(msg.payload.tasks || []);
                return;
            case 'task_update':
                renderTaskUpdate(msg.payload);
                return;
            case 'history_list':
                renderHistoryList(msg.payload.sessions || []);
                return;
            case 'session_loaded':
                if (sessionTitleEl && msg.payload.title) {
                    sessionTitleEl.textContent = msg.payload.title;
                }
                return;
            case 'toast':
                showToast(msg.payload.message, msg.payload.durationMs);
                return;
        }
        scrollToBottom();
    }

    function scrollToBottom() {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    function renderUser(agentName, content) {
        var el = document.createElement('div');
        el.className = 'message user';
        el.textContent = content;
        appendToAgent(agentName, el);
    }

    function appendStreamChunk(agentName, chunk) {
        if (!chunk) return;
        var st = agentState.get(agentName);
        if (!st) return;

        if (!st.currentAssistantEl) {
            st.currentAssistantEl = document.createElement('div');
            st.currentAssistantEl.className = 'message assistant';
            st.currentAssistantEl.innerHTML = '<span class="stream-cursor"></span>';
            appendToAgent(agentName, st.currentAssistantEl);
        }

        st.streamBuffer += chunk;

        var now = Date.now();
        if (now - st.lastRenderTime > STREAM_RENDER_INTERVAL || st.streamBuffer.length > STREAM_RENDER_MIN_CHARS) {
            renderStreamBuffer(agentName);
        } else if (!st.streamRenderTimer) {
            var capturedName = agentName;
            st.streamRenderTimer = setTimeout(function() {
                renderStreamBuffer(capturedName);
            }, STREAM_RENDER_INTERVAL);
        }

        scrollToBottom();
    }

    function renderStreamBuffer(agentName) {
        var st = agentState.get(agentName);
        if (!st || !st.currentAssistantEl) return;

        st.lastRenderTime = Date.now();
        st.streamRenderTimer = null;

        var html = renderMarkdown(st.streamBuffer);
        st.currentAssistantEl.innerHTML = html + '<span class="stream-cursor"></span>';
        highlightCodeBlocks(st.currentAssistantEl);
    }

    function finalizeAssistant(agentName, fullContent) {
        var st = agentState.get(agentName);
        if (!st) return;

        if (st.streamRenderTimer) {
            clearTimeout(st.streamRenderTimer);
            st.streamRenderTimer = null;
        }

        var extracted = extractThinkBlocks(fullContent || '');
        if (extracted.thinkContent) {
            renderCardBeforeAssistant(agentName, 'thinking', 'Thinking', extracted.thinkContent);
        }

        if (st.currentAssistantEl) {
            var html = renderMarkdown(extracted.remainingContent);
            st.currentAssistantEl.innerHTML = html;
            highlightCodeBlocks(st.currentAssistantEl);
            st.currentAssistantEl = null;
            st.streamBuffer = '';
        } else {
            var el = document.createElement('div');
            el.className = 'message assistant';
            el.innerHTML = renderMarkdown(extracted.remainingContent);
            appendToAgent(agentName, el);
            highlightCodeBlocks(el);
        }
    }

    function renderNewSession() {
        if (chatContainer.children.length === 0) return;
        var el = document.createElement('div');
        el.className = 'message loop-start';
        el.textContent = '────────── New Session ──────────';
        appendToAgent('main', el);
    }

    function renderLoopStart(agentName, attempt) {
        var st = agentState.get(agentName);
        if (st) {
            if (st.currentAssistantEl) {
                if (st.streamBuffer) renderStreamBuffer(agentName);
                var cursor = st.currentAssistantEl.querySelector('.stream-cursor');
                if (cursor) cursor.remove();
                st.currentAssistantEl = null;
                st.streamBuffer = '';
            }
            if (st.streamRenderTimer) {
                clearTimeout(st.streamRenderTimer);
                st.streamRenderTimer = null;
            }
        }
        var el = document.createElement('div');
        el.className = 'message loop-start';
        el.textContent = '▶ Attempt ' + attempt + '...';
        appendToAgent(agentName, el);
    }

    function renderSystem(message) {
        var el = document.createElement('div');
        el.className = 'message system';
        el.textContent = message;
        chatContainer.appendChild(el);
    }

    function renderError(message) {
        var el = document.createElement('div');
        el.className = 'message error';
        el.textContent = '⚠ ' + message;
        chatContainer.appendChild(el);
    }

    function renderThinking(agentName, payload) {
        var content = '';
        if (payload.reasoning) content += '[REASONING]\n' + payload.reasoning + '\n\n';
        if (payload.content) content += '[THINK]\n' + payload.content;
        renderCardBeforeAssistant(agentName, 'thinking', 'Thinking', content);
    }

    function renderToolCall(agentName, payload) {
        var content = 'Name: ' + payload.name + '\n';
        if (payload.fullArgs) content += 'Args:\n' + payload.fullArgs;
        else if (payload.args) content += 'Args:\n' + payload.args;
        renderCardBeforeAssistant(agentName, 'tool-call', payload.name, content);
    }

    function renderToolResult(agentName, payload) {
        renderCardBeforeAssistant(agentName, 'tool-result', 'Result: ' + payload.name, payload.result || '');
    }

    function renderCardBeforeAssistant(agentName, type, title, bodyContent) {
        var card = document.createElement('div');
        card.className = 'card ' + type;

        var header = document.createElement('div');
        header.className = 'card-header';

        var titleEl = document.createElement('span');
        titleEl.className = 'card-title';
        titleEl.textContent = title;

        var toggle = document.createElement('span');
        toggle.className = 'card-toggle';
        toggle.textContent = '▼';

        header.appendChild(titleEl);
        header.appendChild(toggle);

        var preview = document.createElement('div');
        preview.className = 'card-preview';
        var previewText = bodyContent ? bodyContent.split('\n')[0].substring(0, 80) : '';
        preview.textContent = previewText + (bodyContent && bodyContent.length > 80 ? '...' : '');

        var body = document.createElement('div');
        body.className = 'card-body';
        body.textContent = bodyContent;

        body.classList.add('collapsed');
        toggle.textContent = '▶';

        header.addEventListener('click', function() {
            var isCollapsed = body.classList.contains('collapsed');
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
        insertBeforeAssistant(agentName, card);
    }

    function updateContextBar(payload) {
        const valueEl = document.getElementById('context-value');
        const fillEl = document.getElementById('context-progress-fill');

        if (!payload) {
            if (valueEl) valueEl.textContent = '0 / 100K (0%)';
            if (fillEl) {
                fillEl.style.width = '0%';
                fillEl.classList.remove('low', 'mid', 'high');
                fillEl.classList.add('low');
            }
            return;
        }

        const tokens = payload.tokens || 0;
        const limit = payload.limit || 1;
        const percent = payload.percent || 0;
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

    function addAgentToSidebar(payload) {
        if (!agentListEl) return;
        var existing = agentListEl.querySelector('[data-agent="' + payload.name + '"]');
        if (existing) existing.remove();

        var item = document.createElement('div');
        item.className = 'agent-item';
        item.dataset.agent = payload.name;
        item.innerHTML = '<span class="agent-icon">🤖</span><span class="agent-name">' + escapeHtml(payload.name) + '</span>';
        item.addEventListener('click', function() {
            switchToAgent(payload.name);
        });
        agentListEl.appendChild(item);
    }

    function removeAgentFromSidebar(name) {
        if (!agentListEl) return;
        var item = agentListEl.querySelector('[data-agent="' + name + '"]');
        if (!item) return;
        item.classList.add('removing');
        setTimeout(function() {
            if (item.parentNode) item.parentNode.removeChild(item);
        }, 300);
    }

    function escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    if (agentSidebarToggleEl && agentSidebarEl) {
        var SIDEBAR_COLLAPSED_KEY = 'coloop-agent-sidebar-collapsed';
        // Restore saved state
        if (localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true') {
            agentSidebarEl.classList.add('collapsed');
            agentSidebarToggleEl.textContent = '▶';
            agentSidebarToggleEl.title = '展开';
        }

        agentSidebarToggleEl.addEventListener('click', function() {
            var isCollapsed = agentSidebarEl.classList.contains('collapsed');
            if (isCollapsed) {
                agentSidebarEl.classList.remove('collapsed');
                agentSidebarToggleEl.textContent = '◀';
                agentSidebarToggleEl.title = '收起';
                localStorage.setItem(SIDEBAR_COLLAPSED_KEY, 'false');
            } else {
                agentSidebarEl.classList.add('collapsed');
                agentSidebarToggleEl.textContent = '▶';
                agentSidebarToggleEl.title = '展开';
                localStorage.setItem(SIDEBAR_COLLAPSED_KEY, 'true');
            }
        });
    }

    // Bind click to existing agent items (e.g. 'main')
    if (agentListEl) {
        agentListEl.querySelectorAll('.agent-item').forEach(function(item) {
            item.addEventListener('click', function() {
                switchToAgent(item.dataset.agent);
            });
        });
    }

    // --- Task sidebar rendering ---
    function renderTaskList(tasks) {
        if (!taskListContainerEl) return;
        taskListContainerEl.innerHTML = '';
        if (!tasks.length) {
            taskListContainerEl.innerHTML = '<div class="task-empty">暂无任务</div>';
            if (taskCountBadgeEl) taskCountBadgeEl.textContent = '';
            return;
        }
        if (taskCountBadgeEl) taskCountBadgeEl.textContent = tasks.length;
        tasks.forEach(function(task) {
            taskListContainerEl.appendChild(createTaskItem(task));
        });
    }

    function renderTaskUpdate(payload) {
        if (!taskListContainerEl) return;
        var existing = taskListContainerEl.querySelector('[data-task-id="' + payload.id + '"]');
        if (existing) {
            existing.className = 'task-item task-' + (payload.status || 'pending');
            var descEl = existing.querySelector('.task-subject');
            if (descEl && payload.description) descEl.textContent = payload.description;
            var statusEl = existing.querySelector('.task-status-icon');
            if (statusEl) statusEl.textContent = formatTaskStatus(payload.status);
        } else {
            var emptyEl = taskListContainerEl.querySelector('.task-empty');
            if (emptyEl) emptyEl.remove();
            taskListContainerEl.appendChild(createTaskItem(payload));
            if (taskCountBadgeEl) {
                var count = taskListContainerEl.querySelectorAll('.task-item').length;
                taskCountBadgeEl.textContent = count;
            }
        }
    }

    function createTaskItem(task) {
        var item = document.createElement('div');
        item.className = 'task-item task-' + (task.status || 'pending');
        item.dataset.taskId = task.id;

        var statusSpan = document.createElement('span');
        statusSpan.className = 'task-status-icon';
        statusSpan.textContent = formatTaskStatus(task.status);

        var descSpan = document.createElement('span');
        descSpan.className = 'task-subject';
        descSpan.textContent = task.description || task.subject || '';

        item.appendChild(statusSpan);
        item.appendChild(descSpan);
        return item;
    }

    function formatTaskStatus(status) {
        switch (status) {
            case 'completed': return '✓';
            case 'in_progress': return '●';
            case 'pending': return '○';
            case 'deleted': return '✕';
            default: return '○';
        }
    }

    // --- History sidebar rendering ---
    function renderHistoryList(sessions) {
        if (!historyListEl) return;
        historyListEl.innerHTML = '';
        if (!sessions.length) {
            historyListEl.innerHTML = '<div class="history-empty">暂无历史记录</div>';
            return;
        }
        sessions.forEach(function(session) {
            var item = document.createElement('div');
            item.className = 'history-item';
            item.dataset.sessionId = session.id;

            var titleSpan = document.createElement('span');
            titleSpan.className = 'history-title';
            titleSpan.textContent = session.title || session.id;

            var timeSpan = document.createElement('span');
            timeSpan.className = 'history-time';
            timeSpan.textContent = formatHistoryTime(session.updatedAt || session.createdAt);

            item.appendChild(titleSpan);
            item.appendChild(timeSpan);

            item.addEventListener('click', function() {
                loadHistorySession(session.id);
            });

            historyListEl.appendChild(item);
        });
    }

    function formatHistoryTime(ts) {
        if (!ts) return '';
        try {
            var d = new Date(ts);
            var month = (d.getMonth() + 1).toString().padStart(2, '0');
            var day = d.getDate().toString().padStart(2, '0');
            var hour = d.getHours().toString().padStart(2, '0');
            var min = d.getMinutes().toString().padStart(2, '0');
            return month + '-' + day + ' ' + hour + ':' + min;
        } catch (e) {
            return '';
        }
    }

    function loadHistorySession(sessionId) {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        ws.send(JSON.stringify({ action: 'load_session', sessionId: sessionId }));
    }

    // --- Sidebar section toggle ---
    function initSectionToggles() {
        var headers = document.querySelectorAll('.sidebar-section-header');
        headers.forEach(function(header) {
            header.addEventListener('click', function() {
                var section = header.closest('.sidebar-section');
                if (!section) return;
                var body = section.querySelector('.sidebar-section-body');
                var toggle = header.querySelector('.section-toggle');
                if (!body) return;

                var isCollapsed = body.classList.contains('collapsed');
                if (isCollapsed) {
                    body.classList.remove('collapsed');
                    section.classList.add('expanded');
                    if (toggle) toggle.textContent = '▼';
                } else {
                    body.classList.add('collapsed');
                    section.classList.remove('expanded');
                    if (toggle) toggle.textContent = '▶';
                }
            });
        });
    }

    function showToast(message, durationMs) {
        var toast = document.createElement('div');
        toast.className = 'toast-notification';
        toast.textContent = message;
        toast.style.cssText = 'position:fixed;top:20px;right:20px;background:#333;color:#fff;padding:12px 20px;border-radius:6px;z-index:9999;font-size:14px;opacity:0;transition:opacity 0.3s ease;max-width:400px;word-wrap:break-word;';
        document.body.appendChild(toast);

        requestAnimationFrame(function() {
            toast.style.opacity = '1';
        });

        setTimeout(function() {
            toast.style.opacity = '0';
            setTimeout(function() {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 300);
        }, durationMs);
    }

    // Initialize sidebar section toggles
    initSectionToggles();

    // Start connection
    connect();
})();
