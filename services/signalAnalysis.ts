// ═══════════════════════════════════════════════════════════════════════════════
// MEET — Motor de Análisis de Señales Profesional
// Signal Analysis Engine v1.0
// ═══════════════════════════════════════════════════════════════════════════════

// ── Signal Type Definitions ──────────────────────────────────────────────────

export interface SignalDefinition {
  id: string;
  pidCode: string;
  name: string;
  nameEs: string;
  unit: string;
  minNominal: number;
  maxNominal: number;
  nominalFrequencyHz?: number;
  waveformType: 'sine' | 'square' | 'triangle' | 'sawtooth' | 'pulse' | 'irregular';
  category: 'engine' | 'fuel' | 'electrical' | 'emissions' | 'transmission';
  description: string;
}

export const SIGNAL_LIBRARY: SignalDefinition[] = [
  {
    id: 'rpm', pidCode: '010C', name: 'Engine RPM', nameEs: 'RPM Motor',
    unit: 'RPM', minNominal: 600, maxNominal: 7000,
    nominalFrequencyHz: 14, waveformType: 'sine', category: 'engine',
    description: 'Revoluciones del cigüeñal. Patrón sinusoidal con armónicos del número de cilindros.'
  },
  {
    id: 'voltage', pidCode: '0142', name: 'System Voltage', nameEs: 'Voltaje del Sistema',
    unit: 'V', minNominal: 12.0, maxNominal: 14.8,
    waveformType: 'sine', category: 'electrical',
    description: 'Voltaje del sistema eléctrico. Señal DC con rizado del alternador.'
  },
  {
    id: 'o2_b1s1', pidCode: '0114', name: 'O2 Sensor B1S1', nameEs: 'Sensor O2 Banco 1',
    unit: 'V', minNominal: 0.1, maxNominal: 0.9,
    nominalFrequencyHz: 1.5, waveformType: 'sine', category: 'emissions',
    description: 'Sensor de oxígeno upstream. Debe oscilar entre 0.1V y 0.9V a ~1-2 Hz en lazo cerrado.'
  },
  {
    id: 'map', pidCode: '010B', name: 'MAP Sensor', nameEs: 'Sensor MAP',
    unit: 'kPa', minNominal: 20, maxNominal: 105,
    waveformType: 'irregular', category: 'engine',
    description: 'Presión absoluta del múltiple de admisión. Varía con la carga del motor.'
  },
  {
    id: 'maf', pidCode: '0110', name: 'MAF Sensor', nameEs: 'Sensor MAF',
    unit: 'g/s', minNominal: 2, maxNominal: 250,
    waveformType: 'irregular', category: 'fuel',
    description: 'Flujo de masa de aire. Señal proporcional a RPM y apertura de acelerador.'
  },
  {
    id: 'tps', pidCode: '0111', name: 'Throttle Position', nameEs: 'Posición del Acelerador',
    unit: '%', minNominal: 0, maxNominal: 100,
    waveformType: 'sawtooth', category: 'engine',
    description: 'Posición del cuerpo de aceleración. Señal lineal 0-100%.'
  },
  {
    id: 'injector', pidCode: 'INJ1', name: 'Injector Pulse', nameEs: 'Pulso de Inyector',
    unit: 'ms', minNominal: 1.5, maxNominal: 15,
    nominalFrequencyHz: 14, waveformType: 'pulse', category: 'fuel',
    description: 'Ancho de pulso del inyector. Señal cuadrada proporcional a la carga.'
  },
  {
    id: 'coolant', pidCode: '0105', name: 'Coolant Temp', nameEs: 'Temp. Refrigerante',
    unit: '°C', minNominal: 80, maxNominal: 105,
    waveformType: 'sine', category: 'engine',
    description: 'Temperatura del refrigerante. Señal lenta que debe estabilizarse en ~90°C.'
  },
  {
    id: 'ignition', pidCode: 'IGN1', name: 'Ignition Coil', nameEs: 'Bobina de Encendido',
    unit: 'kV', minNominal: 8, maxNominal: 45,
    nominalFrequencyHz: 14, waveformType: 'pulse', category: 'electrical',
    description: 'Señal primaria de la bobina. Pico de voltaje seguido de oscilación de quemado.'
  },
  {
    id: 'fuel_trim', pidCode: '0106', name: 'Short Term Fuel Trim', nameEs: 'Ajuste Combustible Corto',
    unit: '%', minNominal: -10, maxNominal: 10,
    waveformType: 'sine', category: 'fuel',
    description: 'Corrección instantánea de mezcla. Debe oscilar cerca de 0% en condiciones normales.'
  },
];

// ── Signal Metrics ───────────────────────────────────────────────────────────

export interface SignalMetrics {
  frequency: number;      // Hz
  amplitude: number;      // Peak-to-peak in signal units
  vpp: number;            // Voltage peak-to-peak
  rms: number;            // Root Mean Square
  thd: number;            // Total Harmonic Distortion (0-1)
  dutyCycle: number;      // 0-100%
  mean: number;           // DC offset
  min: number;
  max: number;
  sampleCount: number;
  durationMs: number;
  stability: number;      // 0-100, how stable the signal is
  noiseLevel: number;     // 0-1, estimated noise ratio
}

export interface AnomalyReport {
  type: 'spike' | 'dropout' | 'noise' | 'drift' | 'flatline' | 'overvoltage' | 'undervoltage' | 'irregular_period';
  severity: 'normal' | 'warning' | 'critical';
  description: string;
  timestamp?: number;
  value?: number;
}

export interface SignalDiagnosis {
  overallSeverity: 'normal' | 'warning' | 'critical';
  metrics: SignalMetrics;
  anomalies: AnomalyReport[];
  diagnosisText: string;
  recommendationText: string;
  confidenceScore: number; // 0-100
}

// ── Signal Generator ─────────────────────────────────────────────────────────
// Generates realistic automotive signal patterns for demo/simulation mode

export class SignalGenerator {
  private phase = 0;
  private noiseLevel = 0.02;
  private anomalyProbability = 0.003; // 0.3% chance per sample

  constructor(private signalDef: SignalDefinition) {}

  setNoiseLevel(level: number) { this.noiseLevel = Math.max(0, Math.min(1, level)); }
  setAnomalyProbability(prob: number) { this.anomalyProbability = prob; }

  /** Generate a single sample point at the given elapsed time (seconds) */
  generate(timeSeconds: number): number {
    const def = this.signalDef;
    const range = def.maxNominal - def.minNominal;
    const mid = (def.maxNominal + def.minNominal) / 2;
    const freq = def.nominalFrequencyHz || 1;

    let value: number;

    switch (def.waveformType) {
      case 'sine':
        value = mid + (range / 2) * 0.6 * Math.sin(2 * Math.PI * freq * timeSeconds + this.phase);
        break;

      case 'square': {
        const t = (timeSeconds * freq) % 1;
        value = t < 0.5 ? def.maxNominal * 0.8 : def.minNominal * 1.2;
        break;
      }

      case 'triangle': {
        const t = (timeSeconds * freq) % 1;
        value = t < 0.5
          ? def.minNominal + range * (t * 2)
          : def.maxNominal - range * ((t - 0.5) * 2);
        break;
      }

      case 'sawtooth': {
        const t = (timeSeconds * freq) % 1;
        value = def.minNominal + range * t;
        break;
      }

      case 'pulse': {
        const t = (timeSeconds * freq) % 1;
        const dutyCycle = 0.3;
        if (t < dutyCycle) {
          // Rising edge + peak
          const riseTime = dutyCycle * 0.1;
          if (t < riseTime) {
            value = def.minNominal + (def.maxNominal - def.minNominal) * (t / riseTime);
          } else {
            value = def.maxNominal * 0.95;
          }
        } else {
          // Fall + ringing
          const fallProgress = (t - dutyCycle) / (1 - dutyCycle);
          const ringing = Math.exp(-fallProgress * 8) * Math.sin(fallProgress * 20) * range * 0.1;
          value = def.minNominal + ringing;
        }
        break;
      }

      case 'irregular':
      default: {
        // Multi-harmonic irregular signal
        value = mid
          + range * 0.25 * Math.sin(2 * Math.PI * freq * timeSeconds)
          + range * 0.10 * Math.sin(2 * Math.PI * freq * 2.3 * timeSeconds + 1.2)
          + range * 0.05 * Math.sin(2 * Math.PI * freq * 5.7 * timeSeconds + 2.8);
        break;
      }
    }

    // Add gaussian-ish noise
    const noise = (Math.random() - 0.5) * 2 * this.noiseLevel * range;
    value += noise;

    // Inject anomalies rarely
    if (Math.random() < this.anomalyProbability) {
      const anomalyType = Math.random();
      if (anomalyType < 0.3) {
        // Spike
        value += range * (0.3 + Math.random() * 0.5) * (Math.random() > 0.5 ? 1 : -1);
      } else if (anomalyType < 0.6) {
        // Dropout
        value = def.minNominal * 0.5;
      } else {
        // Glitch (high frequency burst)
        value += Math.sin(timeSeconds * 500) * range * 0.3;
      }
    }

    return value;
  }

  /** Generate a buffer of N samples over durationMs */
  generateBuffer(durationMs: number, sampleCount: number): { timestamps: number[]; values: number[] } {
    const timestamps: number[] = [];
    const values: number[] = [];
    const dt = durationMs / sampleCount;

    for (let i = 0; i < sampleCount; i++) {
      const t = (i * dt) / 1000; // Convert to seconds
      timestamps.push(i * dt);
      values.push(this.generate(t));
    }

    return { timestamps, values };
  }
}

// ── Signal Analyzer ──────────────────────────────────────────────────────────

export class SignalAnalyzer {

  /** Analyze a signal buffer and produce a full diagnosis */
  analyze(
    values: number[],
    durationMs: number,
    signalDef: SignalDefinition
  ): SignalDiagnosis {
    if (values.length < 10) {
      return this.emptyDiagnosis();
    }

    const metrics = this.calculateMetrics(values, durationMs);
    const anomalies = this.detectAnomalies(values, durationMs, signalDef, metrics);
    const overallSeverity = this.determineOverallSeverity(anomalies, metrics, signalDef);
    const diagnosisText = this.generateDiagnosisText(signalDef, metrics, anomalies, overallSeverity);
    const recommendationText = this.generateRecommendation(signalDef, anomalies, overallSeverity);
    const confidenceScore = Math.min(100, Math.max(20, values.length / 5));

    return {
      overallSeverity,
      metrics,
      anomalies,
      diagnosisText,
      recommendationText,
      confidenceScore,
    };
  }

  private calculateMetrics(values: number[], durationMs: number): SignalMetrics {
    const n = values.length;
    const min = Math.min(...values);
    const max = Math.max(...values);
    const mean = values.reduce((a, b) => a + b, 0) / n;
    const vpp = max - min;

    // RMS
    const rms = Math.sqrt(values.reduce((acc, v) => acc + v * v, 0) / n);

    // Estimate frequency via zero-crossing rate
    let zeroCrossings = 0;
    const centered = values.map(v => v - mean);
    for (let i = 1; i < n; i++) {
      if ((centered[i] >= 0 && centered[i - 1] < 0) || (centered[i] < 0 && centered[i - 1] >= 0)) {
        zeroCrossings++;
      }
    }
    const durationSec = durationMs / 1000;
    const frequency = zeroCrossings / (2 * durationSec);

    // Estimate duty cycle (time above mean / total time)
    const aboveMean = values.filter(v => v > mean).length;
    const dutyCycle = (aboveMean / n) * 100;

    // Simplified THD estimate via variance of derivative
    const derivatives: number[] = [];
    for (let i = 1; i < n; i++) {
      derivatives.push(values[i] - values[i - 1]);
    }
    const derivMean = derivatives.reduce((a, b) => a + b, 0) / derivatives.length;
    const derivVariance = derivatives.reduce((acc, d) => acc + (d - derivMean) ** 2, 0) / derivatives.length;
    const normalizedDerivVar = derivVariance / (vpp * vpp + 0.001);
    const thd = Math.min(1, normalizedDerivVar * 10);

    // Stability — coefficient of variation from a rolling window
    const windowSize = Math.max(10, Math.floor(n / 10));
    const windowMeans: number[] = [];
    for (let i = 0; i <= n - windowSize; i += windowSize) {
      const windowSlice = values.slice(i, i + windowSize);
      windowMeans.push(windowSlice.reduce((a, b) => a + b, 0) / windowSlice.length);
    }
    const windowMeanAvg = windowMeans.reduce((a, b) => a + b, 0) / windowMeans.length;
    const windowMeanStd = Math.sqrt(windowMeans.reduce((acc, m) => acc + (m - windowMeanAvg) ** 2, 0) / windowMeans.length);
    const cv = windowMeanStd / (Math.abs(windowMeanAvg) + 0.001);
    const stability = Math.max(0, Math.min(100, 100 - cv * 500));

    // Noise estimation
    const noiseLevel = thd > 0.3 ? Math.min(1, thd) : Math.min(1, normalizedDerivVar * 5);

    return {
      frequency,
      amplitude: vpp / 2,
      vpp,
      rms,
      thd,
      dutyCycle,
      mean,
      min,
      max,
      sampleCount: n,
      durationMs,
      stability,
      noiseLevel,
    };
  }

  private detectAnomalies(
    values: number[],
    durationMs: number,
    def: SignalDefinition,
    metrics: SignalMetrics
  ): AnomalyReport[] {
    const anomalies: AnomalyReport[] = [];
    const range = def.maxNominal - def.minNominal;
    const tolerance = range * 0.25;

    // Check for values outside nominal range
    const overCount = values.filter(v => v > def.maxNominal + tolerance).length;
    const underCount = values.filter(v => v < def.minNominal - tolerance).length;

    if (overCount > values.length * 0.05) {
      anomalies.push({
        type: 'overvoltage',
        severity: overCount > values.length * 0.2 ? 'critical' : 'warning',
        description: `Señal excede el rango nominal superior (${overCount} muestras > ${(def.maxNominal + tolerance).toFixed(1)} ${def.unit})`,
      });
    }

    if (underCount > values.length * 0.05) {
      anomalies.push({
        type: 'undervoltage',
        severity: underCount > values.length * 0.2 ? 'critical' : 'warning',
        description: `Señal por debajo del rango nominal inferior (${underCount} muestras < ${(def.minNominal - tolerance).toFixed(1)} ${def.unit})`,
      });
    }

    // Spike detection
    const spikeThreshold = range * 0.6;
    let spikeCount = 0;
    for (let i = 1; i < values.length - 1; i++) {
      const diff1 = Math.abs(values[i] - values[i - 1]);
      const diff2 = Math.abs(values[i + 1] - values[i]);
      if (diff1 > spikeThreshold && diff2 > spikeThreshold) {
        spikeCount++;
      }
    }
    if (spikeCount > 0) {
      anomalies.push({
        type: 'spike',
        severity: spikeCount > 5 ? 'critical' : 'warning',
        description: `${spikeCount} pico(s) transitorio(s) detectado(s) (amplitud > ${spikeThreshold.toFixed(1)} ${def.unit})`,
      });
    }

    // Dropout detection (sudden drop to near-zero)
    let dropoutCount = 0;
    for (let i = 1; i < values.length; i++) {
      if (values[i] < def.minNominal * 0.3 && values[i - 1] > def.minNominal) {
        dropoutCount++;
      }
    }
    if (dropoutCount > 0) {
      anomalies.push({
        type: 'dropout',
        severity: dropoutCount > 3 ? 'critical' : 'warning',
        description: `${dropoutCount} caída(s) abrupta(s) de señal detectada(s). Posible fallo de conexión o componente.`,
      });
    }

    // Flatline detection
    const flatlineThreshold = range * 0.01;
    let maxFlat = 0;
    let currentFlat = 0;
    for (let i = 1; i < values.length; i++) {
      if (Math.abs(values[i] - values[i - 1]) < flatlineThreshold) {
        currentFlat++;
        maxFlat = Math.max(maxFlat, currentFlat);
      } else {
        currentFlat = 0;
      }
    }
    if (maxFlat > values.length * 0.3) {
      anomalies.push({
        type: 'flatline',
        severity: 'warning',
        description: `Señal plana detectada por ${maxFlat} muestras consecutivas. Sensor puede estar desconectado o saturado.`,
      });
    }

    // Noise level
    if (metrics.noiseLevel > 0.4) {
      anomalies.push({
        type: 'noise',
        severity: metrics.noiseLevel > 0.7 ? 'critical' : 'warning',
        description: `Nivel de ruido excesivo (${(metrics.noiseLevel * 100).toFixed(0)}%). Verificar blindaje del cableado y masa del sensor.`,
      });
    }

    // Drift detection
    const firstQuarter = values.slice(0, Math.floor(values.length / 4));
    const lastQuarter = values.slice(Math.floor(values.length * 3 / 4));
    const firstMean = firstQuarter.reduce((a, b) => a + b, 0) / firstQuarter.length;
    const lastMean = lastQuarter.reduce((a, b) => a + b, 0) / lastQuarter.length;
    const driftAmount = Math.abs(lastMean - firstMean);
    if (driftAmount > range * 0.15) {
      anomalies.push({
        type: 'drift',
        severity: driftAmount > range * 0.3 ? 'critical' : 'warning',
        description: `Deriva de señal detectada: ${driftAmount.toFixed(2)} ${def.unit} de cambio progresivo. Posible degradación del sensor.`,
      });
    }

    return anomalies;
  }

  private determineOverallSeverity(
    anomalies: AnomalyReport[],
    metrics: SignalMetrics,
    def: SignalDefinition
  ): 'normal' | 'warning' | 'critical' {
    if (anomalies.some(a => a.severity === 'critical')) return 'critical';
    if (anomalies.some(a => a.severity === 'warning')) return 'warning';
    if (metrics.stability < 40) return 'warning';
    if (metrics.mean < def.minNominal || metrics.mean > def.maxNominal) return 'warning';
    return 'normal';
  }

  private generateDiagnosisText(
    def: SignalDefinition,
    metrics: SignalMetrics,
    anomalies: AnomalyReport[],
    severity: 'normal' | 'warning' | 'critical'
  ): string {
    const lines: string[] = [];

    if (severity === 'normal') {
      lines.push(`✅ SEÑAL NOMINAL — ${def.nameEs}`);
      lines.push(`La señal de ${def.nameEs} se encuentra dentro de los parámetros operativos normales.`);
      lines.push(`Frecuencia: ${metrics.frequency.toFixed(1)} Hz | Amplitud: ${metrics.vpp.toFixed(2)} ${def.unit} | Estabilidad: ${metrics.stability.toFixed(0)}%`);
    } else if (severity === 'warning') {
      lines.push(`⚠️ ATENCIÓN — ${def.nameEs}`);
      lines.push(`Se detectaron anomalías menores en la señal de ${def.nameEs}.`);
      anomalies.filter(a => a.severity === 'warning').forEach(a => {
        lines.push(`  → ${a.description}`);
      });
    } else {
      lines.push(`🔴 ALERTA CRÍTICA — ${def.nameEs}`);
      lines.push(`La señal de ${def.nameEs} presenta anomalías severas que requieren atención inmediata.`);
      anomalies.filter(a => a.severity === 'critical').forEach(a => {
        lines.push(`  ⛔ ${a.description}`);
      });
      anomalies.filter(a => a.severity === 'warning').forEach(a => {
        lines.push(`  → ${a.description}`);
      });
    }

    lines.push('');
    lines.push(`📊 Métricas: Media=${metrics.mean.toFixed(2)} | Min=${metrics.min.toFixed(2)} | Max=${metrics.max.toFixed(2)} | RMS=${metrics.rms.toFixed(2)} | THD=${(metrics.thd * 100).toFixed(1)}%`);

    return lines.join('\n');
  }

  private generateRecommendation(
    def: SignalDefinition,
    anomalies: AnomalyReport[],
    severity: 'normal' | 'warning' | 'critical'
  ): string {
    if (severity === 'normal') {
      return 'No se requiere acción. Continúe con el mantenimiento preventivo según el programa del fabricante.';
    }

    const recs: string[] = [];

    for (const a of anomalies) {
      switch (a.type) {
        case 'spike':
          recs.push('Inspeccionar conexiones eléctricas y terminales del sensor. Verificar tierra del motor.');
          break;
        case 'dropout':
          recs.push('Verificar continuidad del cableado. Inspeccionar conector del sensor y ECU.');
          break;
        case 'noise':
          recs.push('Inspeccionar blindaje del arnés. Verificar generador/alternador. Revisar masa del chasis.');
          break;
        case 'drift':
          recs.push('Sensor posiblemente degradado. Comparar lectura con sensor de referencia calibrado.');
          break;
        case 'flatline':
          recs.push('Verificar alimentación del sensor. Posible circuito abierto o cortocircuito a masa/voltaje.');
          break;
        case 'overvoltage':
          recs.push('Verificar regulador de voltaje del alternador. Inspeccionar diodos del puente rectificador.');
          break;
        case 'undervoltage':
          recs.push('Revisar estado de la batería. Verificar caídas de voltaje en el circuito de alimentación.');
          break;
        case 'irregular_period':
          recs.push('Posible fallo mecánico (compresión desigual, inyector obstruido). Realizar prueba de compresión.');
          break;
      }
    }

    return [...new Set(recs)].join(' ');
  }

  private emptyDiagnosis(): SignalDiagnosis {
    return {
      overallSeverity: 'normal',
      metrics: {
        frequency: 0, amplitude: 0, vpp: 0, rms: 0, thd: 0,
        dutyCycle: 50, mean: 0, min: 0, max: 0,
        sampleCount: 0, durationMs: 0, stability: 100, noiseLevel: 0,
      },
      anomalies: [],
      diagnosisText: 'Captura insuficiente. Se requieren al menos 10 muestras para el análisis.',
      recommendationText: 'Inicie una captura de al menos 2 segundos.',
      confidenceScore: 0,
    };
  }
}
