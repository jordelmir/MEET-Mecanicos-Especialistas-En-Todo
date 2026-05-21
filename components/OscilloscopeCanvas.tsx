import React, { useRef, useEffect, useCallback } from 'react';

interface OscilloscopeCanvasProps {
  data: number[];
  isRunning: boolean;
  timeDiv: number;
  voltsDiv: number;
  triggerLevel: number;
  color: string;
  showGrid: boolean;
  signalUnit: string;
  minNominal: number;
  maxNominal: number;
}

export function OscilloscopeCanvas({
  data, isRunning, timeDiv, voltsDiv, triggerLevel,
  color, showGrid, signalUnit, minNominal, maxNominal
}: OscilloscopeCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const afterglowRef = useRef<ImageData | null>(null);
  const animRef = useRef<number>(0);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const W = canvas.width;
    const H = canvas.height;
    const pad = { top: 30, bottom: 25, left: 55, right: 15 };
    const plotW = W - pad.left - pad.right;
    const plotH = H - pad.top - pad.bottom;

    // Afterglow fade effect
    if (afterglowRef.current && isRunning) {
      ctx.putImageData(afterglowRef.current, 0, 0);
      ctx.fillStyle = 'rgba(0,0,0,0.15)';
      ctx.fillRect(0, 0, W, H);
    } else {
      ctx.fillStyle = '#0a0a0a';
      ctx.fillRect(0, 0, W, H);
    }

    // Subtle vignette
    const vg = ctx.createRadialGradient(W/2, H/2, plotW*0.3, W/2, H/2, W*0.7);
    vg.addColorStop(0, 'rgba(0,0,0,0)');
    vg.addColorStop(1, 'rgba(0,0,0,0.4)');
    ctx.fillStyle = vg;
    ctx.fillRect(0, 0, W, H);

    // Calculate Y range from data or nominal
    let yMin: number, yMax: number;
    if (data.length > 2) {
      const dataMin = Math.min(...data);
      const dataMax = Math.max(...data);
      const margin = (dataMax - dataMin) * 0.15 || 1;
      yMin = Math.min(dataMin - margin, minNominal * 0.8);
      yMax = Math.max(dataMax + margin, maxNominal * 1.2);
    } else {
      yMin = minNominal * 0.5;
      yMax = maxNominal * 1.5;
    }
    const yRange = yMax - yMin || 1;

    // Grid
    if (showGrid) {
      ctx.strokeStyle = 'rgba(0,255,100,0.08)';
      ctx.lineWidth = 1;
      const gridXCount = 10;
      const gridYCount = 8;
      for (let i = 0; i <= gridXCount; i++) {
        const x = pad.left + (plotW / gridXCount) * i;
        ctx.beginPath();
        ctx.moveTo(x, pad.top);
        ctx.lineTo(x, pad.top + plotH);
        ctx.stroke();
        // Dotted center cross
        if (i === gridXCount / 2) {
          ctx.strokeStyle = 'rgba(0,255,100,0.2)';
          ctx.setLineDash([4, 4]);
          ctx.beginPath(); ctx.moveTo(x, pad.top); ctx.lineTo(x, pad.top + plotH); ctx.stroke();
          ctx.setLineDash([]);
          ctx.strokeStyle = 'rgba(0,255,100,0.08)';
        }
      }
      for (let i = 0; i <= gridYCount; i++) {
        const y = pad.top + (plotH / gridYCount) * i;
        ctx.beginPath();
        ctx.moveTo(pad.left, y);
        ctx.lineTo(pad.left + plotW, y);
        ctx.stroke();
        if (i === gridYCount / 2) {
          ctx.strokeStyle = 'rgba(0,255,100,0.2)';
          ctx.setLineDash([4, 4]);
          ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(pad.left + plotW, y); ctx.stroke();
          ctx.setLineDash([]);
          ctx.strokeStyle = 'rgba(0,255,100,0.08)';
        }
      }

      // Y-axis labels
      ctx.fillStyle = 'rgba(0,255,100,0.5)';
      ctx.font = '10px monospace';
      ctx.textAlign = 'right';
      for (let i = 0; i <= gridYCount; i++) {
        const y = pad.top + (plotH / gridYCount) * i;
        const val = yMax - (yRange / gridYCount) * i;
        ctx.fillText(val.toFixed(1), pad.left - 5, y + 3);
      }

      // X-axis time labels
      ctx.textAlign = 'center';
      const totalTimeMs = data.length * timeDiv;
      for (let i = 0; i <= gridXCount; i += 2) {
        const x = pad.left + (plotW / gridXCount) * i;
        const tMs = (totalTimeMs / gridXCount) * i;
        ctx.fillText(tMs < 1000 ? `${tMs.toFixed(0)}ms` : `${(tMs/1000).toFixed(1)}s`, x, pad.top + plotH + 15);
      }
    }

    // Plot border
    ctx.strokeStyle = 'rgba(0,255,100,0.25)';
    ctx.lineWidth = 1;
    ctx.strokeRect(pad.left, pad.top, plotW, plotH);

    // Trigger level line
    if (triggerLevel > yMin && triggerLevel < yMax) {
      const trigY = pad.top + plotH - ((triggerLevel - yMin) / yRange) * plotH;
      ctx.strokeStyle = 'rgba(255,200,0,0.4)';
      ctx.setLineDash([6, 3]);
      ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(pad.left, trigY); ctx.lineTo(pad.left + plotW, trigY); ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = 'rgba(255,200,0,0.7)';
      ctx.font = '9px monospace';
      ctx.textAlign = 'left';
      ctx.fillText(`T: ${triggerLevel.toFixed(1)}`, pad.left + plotW + 2, trigY + 3);
    }

    // Waveform
    if (data.length > 1) {
      const stepX = plotW / (data.length - 1);

      // Glow layer
      ctx.beginPath();
      ctx.strokeStyle = color.replace(')', ',0.2)').replace('rgb', 'rgba');
      ctx.lineWidth = 6;
      ctx.lineJoin = 'round';
      for (let i = 0; i < data.length; i++) {
        const x = pad.left + i * stepX;
        const normY = (data[i] - yMin) / yRange;
        const y = pad.top + plotH - normY * plotH;
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.stroke();

      // Main line
      ctx.beginPath();
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.lineJoin = 'round';
      ctx.shadowColor = color;
      ctx.shadowBlur = 8;
      for (let i = 0; i < data.length; i++) {
        const x = pad.left + i * stepX;
        const normY = (data[i] - yMin) / yRange;
        const y = pad.top + plotH - normY * plotH;
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.stroke();
      ctx.shadowBlur = 0;
    }

    // Scanning beam
    if (isRunning && data.length > 1) {
      const beamX = pad.left + plotW;
      const gradient = ctx.createLinearGradient(beamX - 40, 0, beamX, 0);
      gradient.addColorStop(0, 'rgba(0,255,100,0)');
      gradient.addColorStop(1, 'rgba(0,255,100,0.15)');
      ctx.fillStyle = gradient;
      ctx.fillRect(beamX - 40, pad.top, 40, plotH);
    }

    // Corner HUD info
    ctx.fillStyle = 'rgba(0,255,100,0.6)';
    ctx.font = 'bold 9px monospace';
    ctx.textAlign = 'left';
    ctx.fillText(`TIME/DIV: ${timeDiv}ms`, pad.left + 5, pad.top + 12);
    ctx.fillText(`UNIT: ${signalUnit}`, pad.left + 5, pad.top + 24);
    ctx.textAlign = 'right';
    ctx.fillText(isRunning ? '● REC' : '■ STOP', pad.left + plotW - 5, pad.top + 12);
    if (isRunning) {
      ctx.fillStyle = 'rgba(255,50,50,0.8)';
      ctx.fillText('● REC', pad.left + plotW - 5, pad.top + 12);
    }
    ctx.fillStyle = 'rgba(0,255,100,0.6)';
    ctx.fillText(`${data.length} samples`, pad.left + plotW - 5, pad.top + 24);

    // Save afterglow
    afterglowRef.current = ctx.getImageData(0, 0, W, H);

    if (isRunning) {
      animRef.current = requestAnimationFrame(draw);
    }
  }, [data, isRunning, timeDiv, voltsDiv, triggerLevel, color, showGrid, signalUnit, minNominal, maxNominal]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    const ctx = canvas.getContext('2d');
    if (ctx) ctx.scale(dpr, dpr);
    // Reset canvas internal dimensions for drawing
    canvas.width = rect.width;
    canvas.height = rect.height;
  }, []);

  useEffect(() => {
    cancelAnimationFrame(animRef.current);
    draw();
  }, [draw]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: '100%', height: '100%', display: 'block', borderRadius: '8px' }}
    />
  );
}
