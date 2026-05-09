(function() {
    const canvas = document.getElementById('topology-canvas');
    const nodesContainer = document.getElementById('topology-nodes');
    const svg = document.getElementById('topology-svg');
    const layoutSelect = document.getElementById('topology-layout-select');
    const toggleBtn = document.getElementById('topology-toggle');
    const panel = document.getElementById('topology-panel');

    if (!canvas) return;

    const layouts = {
        flow: {
            compute(agents, w, h) {
                const positions = {};
                const main = agents.find(function(a) { return a.name === 'main'; });
                if (main) {
                    positions[main.name] = { x: 80, y: h / 2 };
                }
                var depth = 1;
                var prev = main ? [main] : [];
                while (prev.length > 0) {
                    var next = [];
                    prev.forEach(function(p) {
                        p.subagents.forEach(function(name) {
                            var a = agents.find(function(x) { return x.name === name; });
                            if (a) next.push(a);
                        });
                    });
                    if (next.length === 0) break;
                    var spacing = Math.min(120, h / (next.length + 1));
                    next.forEach(function(a, i) {
                        positions[a.name] = {
                            x: 80 + depth * 200,
                            y: (h - (next.length - 1) * spacing) / 2 + i * spacing
                        };
                    });
                    prev = next;
                    depth++;
                }
                return positions;
            }
        },
        roundtable: {
            compute(agents, w, h) {
                const positions = {};
                const subs = agents.filter(function(a) { return a.name !== 'main'; });
                const cx = w / 2, cy = h / 2;
                const radius = Math.min(w, h) * 0.35;

                if (agents.find(function(a) { return a.name === 'main'; })) {
                    positions['main'] = { x: w * 0.5, y: 60 };
                }
                subs.forEach(function(a, i) {
                    var angle = (i / Math.max(subs.length, 1)) * 2 * Math.PI - Math.PI / 2;
                    positions[a.name] = {
                        x: cx + radius * Math.cos(angle),
                        y: cy + radius * Math.sin(angle)
                    };
                });
                return positions;
            }
        },
        radial: {
            compute(agents, w, h) {
                const positions = {};
                const subs = agents.filter(function(a) { return a.name !== 'main'; });
                const cx = w / 2, cy = h / 2;
                const radius = Math.min(w, h) * 0.38;

                if (agents.find(function(a) { return a.name === 'main'; })) {
                    positions['main'] = { x: cx, y: cy };
                }
                subs.forEach(function(a, i) {
                    var angle = (i / Math.max(subs.length, 1)) * 2 * Math.PI - Math.PI / 2;
                    positions[a.name] = {
                        x: cx + radius * Math.cos(angle),
                        y: cy + radius * Math.sin(angle)
                    };
                });
                return positions;
            }
        }
    };

    function render() {
        const gs = window.GraphState;
        if (!gs) return;
        const agents = gs.getAgentList();

        const layout = layouts[gs.topologyLayout] || layouts.flow;
        const positions = layout.compute(agents, canvas.clientWidth, canvas.clientHeight);

        agents.forEach(function(a) {
            if (positions[a.name]) {
                a.position = positions[a.name];
            }
        });

        renderNodes(agents);
        renderConnections(agents, positions);
    }

    function renderNodes(agents) {
        var html = '';
        agents.forEach(function(a) {
            var isMain = a.name === 'main';
            var statusClass = a.status === 'working' ? 'agent-node-working' : (a.status === 'idle' ? 'agent-node-idle' : '');
            var statusText = '';
            if (a.status === 'working') {
                if (a.currentActivity === 'thinking') statusText = '💭 思考中...';
                else if (a.currentActivity && a.currentActivity.startsWith('tool_call:')) statusText = '🔧 ' + a.currentActivity.split(':')[1];
                else if (a.currentActivity === 'stream') statusText = '✍️ 输出中...';
                else statusText = '🚀 运行中...';
            }
            var bubbleHtml = '';
            if (a.status === 'answering' && a.lastMessage) {
                bubbleHtml = '<div class="agent-node-bubble">' + escapeHtml(a.lastMessage) + '</div>';
            }

            html += '<div class="agent-node ' + (isMain ? 'agent-node-main ' : '') + statusClass + '"' +
                ' style="left:' + a.position.x + 'px;top:' + a.position.y + 'px;transform:translate(-50%,-50%)"' +
                ' data-agent="' + a.name + '">' +
                '<div class="agent-node-avatar">' + (isMain ? '⭐' : '🤖') + '</div>' +
                (statusText ? '<div class="agent-node-status">' + statusText + '</div>' : '') +
                bubbleHtml +
                '<div class="agent-node-actions">' +
                '<button onclick="jumpToAgent(\'' + a.name + '\')">跳转</button>' +
                '<button onclick="communicateWithAgent(\'' + a.name + '\')">沟通</button>' +
                '</div>' +
                '</div>';
        });
        nodesContainer.innerHTML = html;
    }

    function renderConnections(agents, positions) {
        var paths = '';
        agents.forEach(function(a) {
            if (!a.createdBy || !positions[a.createdBy]) return;
            var from = positions[a.createdBy];
            var to = positions[a.name];
            if (!from || !to) return;
            var isActive = a.status === 'working';
            var d = 'M' + from.x + ' ' + from.y + ' L' + to.x + ' ' + to.y;
            paths += '<path d="' + d + '" class="topo-line' + (isActive ? ' active' : '') + '" />';
        });
        svg.innerHTML = paths;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    window.jumpToAgent = function(name) {
        var singleBtn = document.querySelector('.mode-btn[data-mode="single"]');
        if (singleBtn) singleBtn.click();
        var items = document.querySelectorAll('.agent-item');
        items.forEach(function(item) {
            if (item.dataset.agent === name) item.click();
        });
    };

    window.communicateWithAgent = function(name) {
        var targetSelect = document.getElementById('group-target-select');
        var input = document.getElementById('group-message-input');
        if (targetSelect) targetSelect.value = name;
        if (input) input.focus();
    };

    layoutSelect.addEventListener('change', function() {
        window.GraphState.setTopologyLayout(layoutSelect.value);
    });

    toggleBtn.addEventListener('click', function() {
        var collapsed = panel.classList.toggle('collapsed');
        toggleBtn.innerHTML = collapsed ? '&#9654;' : '&#9664;';
        window.GraphState.setTopologyCollapsed(collapsed);
    });

    window.GraphState.onChange(render);
    setTimeout(render, 200);
})();
