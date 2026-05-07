(function() {
    const messagesEl = document.getElementById('group-chat-messages');
    const membersEl = document.getElementById('group-chat-members');
    const targetSelect = document.getElementById('group-target-select');
    const inputEl = document.getElementById('group-message-input');
    const sendBtn = document.getElementById('group-send-btn');

    if (!messagesEl) return;

    function render() {
        const gs = window.GraphState;
        if (!gs) return;
        renderMessages(gs.groupMessages);
        renderMembers(gs.getAgentList());
        updateTargetSelect(gs.getAgentList());
    }

    function renderMessages(messages) {
        if (messages.length === 0) {
            messagesEl.innerHTML = '<div class="group-system-msg">等待消息...</div>';
            return;
        }
        var html = '';
        messages.forEach(function(m) {
            if (m.type === 'system') {
                html += '<div class="group-system-msg">' + escapeHtml(m.content) + '</div>';
                return;
            }
            if (m.type === 'user') {
                var label = m.targetAgent && m.targetAgent !== 'main'
                    ? '我 → ' + m.targetAgent
                    : '我';
                html += '<div class="group-msg group-msg-self">' +
                    '<div class="group-msg-label">' + escapeHtml(label) + '</div>' +
                    '<div class="group-msg-bubble">' + escapeHtml(m.content) + '</div>' +
                    '</div>';
                return;
            }
            // assistant
            html += '<div class="group-msg group-msg-other">' +
                '<div class="group-msg-avatar">' + (m.agent === 'main' ? '⭐' : '🤖') + '</div>' +
                '<div class="group-msg-content">' +
                '<div class="group-msg-name">' + escapeHtml(m.agent) + '</div>' +
                '<div class="group-msg-bubble">' + escapeHtml(m.content) + '</div>' +
                '</div>' +
                '</div>';
        });
        messagesEl.innerHTML = html;
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function renderMembers(agents) {
        var count = agents.length + 1; // +1 for main
        var html = '<div class="group-members-header">群成员 (' + count + ')</div>';
        html += '<div class="group-member">' +
            '<span class="group-member-avatar">⭐</span>' +
            '<span class="group-member-name">main</span>' +
            '<span class="group-member-role">群主</span>' +
            '</div>';
        agents.forEach(function(a) {
            if (a.name === 'main') return;
            var statusText = a.status === 'working' ? '工作中' : '空闲';
            html += '<div class="group-member">' +
                '<span class="group-member-avatar">🤖</span>' +
                '<span class="group-member-name">' + escapeHtml(a.name) + '</span>' +
                '<span class="group-member-status">' + statusText + '</span>' +
                '</div>';
        });
        membersEl.innerHTML = html;
    }

    function updateTargetSelect(agents) {
        var currentValue = targetSelect.value;
        var options = ['<option>main</option>'];
        agents.forEach(function(a) {
            if (a.name !== 'main') {
                options.push('<option value="' + a.name + '">' + a.name + '</option>');
            }
        });
        targetSelect.innerHTML = options.join('');
        if (currentValue) targetSelect.value = currentValue;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function sendGroupMessage() {
        var text = inputEl.value.trim();
        if (!text || !window.ws || window.ws.readyState !== WebSocket.OPEN) return;
        var target = targetSelect.value || 'main';
        window.ws.send(JSON.stringify({ action: 'chat', message: text, targetAgent: target }));
        inputEl.value = '';
    }

    sendBtn.addEventListener('click', sendGroupMessage);
    inputEl.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            sendGroupMessage();
        }
    });

    window.GraphState.onChange(render);
    render();
})();
