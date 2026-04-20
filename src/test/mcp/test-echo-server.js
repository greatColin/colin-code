#!/usr/bin/env node

const { Server } = require('@modelcontextprotocol/sdk/server/index.js');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio.js');
const { CallToolRequestSchema, ListToolsRequestSchema } = require('@modelcontextprotocol/sdk/types.js');

const server = new Server({
    name: 'test-echo-server',
    version: '1.0.0'
}, {
    capabilities: {
        tools: {}
    }
});

server.setRequestHandler(ListToolsRequestSchema, async () => {
    return {
        tools: [
            {
                name: 'echo',
                description: 'Echoes back the input text',
                inputSchema: {
                    type: 'object',
                    properties: {
                        text: {
                            type: 'string',
                            description: 'Text to echo back'
                        }
                    },
                    required: ['text']
                }
            }
        ]
    };
});

server.setRequestHandler(CallToolRequestSchema, async ({ params }) => {
    const { name, arguments: args } = params;
    if (name === 'echo') {
        return {
            content: [
                { type: 'text', text: `Echo: ${args.text}` }
            ]
        };
    }
    throw new Error(`Unknown tool: ${name}`);
});

async function main() {
    const transport = new StdioServerTransport();
    await server.connect(transport);
}

main().catch(console.error);