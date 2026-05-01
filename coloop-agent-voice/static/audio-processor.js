class PCMProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.bufferSize = 4800; // 300ms @ 16kHz
        this.buffer = new Float32Array(this.bufferSize);
        this.offset = 0;
    }

    process(inputs, outputs, parameters) {
        const input = inputs[0];
        if (!input || !input[0]) return true;

        const samples = input[0];

        for (let i = 0; i < samples.length; i++) {
            this.buffer[this.offset++] = samples[i];

            if (this.offset >= this.bufferSize) {
                const int16Array = new Int16Array(this.bufferSize);
                for (let j = 0; j < this.bufferSize; j++) {
                    const val = this.buffer[j] * 32767;
                    int16Array[j] = val > 32767 ? 32767 : (val < -32768 ? -32768 : val);
                }
                this.port.postMessage(int16Array.buffer, [int16Array.buffer]);
                this.offset = 0;
            }
        }
        return true;
    }
}

registerProcessor("pcm-processor", PCMProcessor);
