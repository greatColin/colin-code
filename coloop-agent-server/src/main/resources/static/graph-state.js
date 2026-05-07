(function() {
    window.GraphState = {
        agents: new Map(),
        groupMessages: [],
        currentMode: 'single',
        topologyLayout: 'flow',
        topologyCollapsed: false,

        updateFromMessage(msg) {
            const agent = msg.agentName || 'main';
            const type = msg.type;
            const payload = msg.payload || {};

            let state = this.agents.get(agent);
            if (!state && type !== 'subagent_created') {
                state = {
                    name: agent,
                    status: 'idle',
                    currentActivity: '',
                    lastMessage: '',
                    lastMessageTime: 0,
                    createdBy: 'main',
                    subagents: [],
                    position: { x: 0, y: 0 }
                };
                this.agents.set(agent, state);
            }

            switch (type) {
                case 'subagent_created':
                    this.agents.set(payload.name, {
                        name: payload.name,
                        status: 'idle',
                        currentActivity: '',
                        lastMessage: '',
                        lastMessageTime: 0,
                        createdBy: agent,
                        subagents: [],
                        position: { x: 0, y: 0 }
                    });
                    const parent = this.agents.get(agent);
                    if (parent) parent.subagents.push(payload.name);
                    break;

                case 'subagent_cleared':
                    const cleared = this.agents.get(payload.name);
                    if (cleared) {
                        cleared.status = 'removed';
                        setTimeout(() => this.agents.delete(payload.name), 3000);
                    }
                    const p = this.agents.get(agent);
                    if (p) {
                        p.subagents = p.subagents.filter(n => n !== payload.name);
                    }
                    break;

                case 'loop_start':
                    if (state) { state.status = 'working'; state.currentActivity = 'loop_start'; }
                    break;

                case 'thinking':
                    if (state) { state.status = 'working'; state.currentActivity = 'thinking'; }
                    break;

                case 'tool_call':
                    if (state) { state.status = 'working'; state.currentActivity = 'tool_call:' + payload.name; }
                    break;

                case 'stream_chunk':
                    if (state) { state.status = 'working'; state.currentActivity = 'stream'; }
                    break;

                case 'assistant':
                    if (state) {
                        state.status = 'answering';
                        state.currentActivity = '';
                        const content = payload.content || '';
                        state.lastMessage = content.substring(0, 80) + (content.length > 80 ? '...' : '');
                        state.lastMessageTime = Date.now();
                        setTimeout(() => { if (state.status === 'answering') state.status = 'idle'; }, 5000);
                    }
                    this.addGroupMessage({
                        type: 'assistant',
                        agent: agent,
                        content: payload.content || ''
                    });
                    break;

                case 'user':
                    this.addGroupMessage({
                        type: 'user',
                        agent: 'user',
                        targetAgent: agent,
                        content: payload.content || ''
                    });
                    break;

                case 'system':
                    this.addGroupMessage({
                        type: 'system',
                        agent: 'system',
                        content: payload.message || ''
                    });
                    break;
            }

            this.notifyListeners();
        },

        addGroupMessage(msg) {
            const last = this.groupMessages[this.groupMessages.length - 1];
            if (last && last.type === msg.type && last.agent === msg.agent &&
                Date.now() - last.timestamp < 5000) {
                last.content += '\n' + msg.content;
                last.timestamp = Date.now();
            } else {
                this.groupMessages.push({
                    id: Date.now() + '-' + (msg.agent || 'sys') + '-' + this.groupMessages.length,
                    ...msg,
                    timestamp: Date.now()
                });
            }
        },

        listeners: [],
        onChange(fn) { this.listeners.push(fn); },
        notifyListeners() {
            this.listeners.forEach(fn => fn(this));
        },

        getAgentList() {
            return Array.from(this.agents.values()).filter(a => a.status !== 'removed');
        },

        setTopologyLayout(layout) {
            this.topologyLayout = layout;
            this.notifyListeners();
        },

        setTopologyCollapsed(collapsed) {
            this.topologyCollapsed = collapsed;
            this.notifyListeners();
        }
    };
})();
