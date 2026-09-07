# Arquitectura Pedagógica Socrática & Motor FSRS

Esta referencia detalla el funcionamiento del motor de tutoría maéutica y el sistema neuro-adaptativo de repetición espaciada en MEET / Elysium OS.

---

## 1. Fundamentos del Tutor Socrático (Bloom 2-Sigma)

En 1984, Benjamin Bloom demostró que los estudiantes instruidos mediante tutoría personalizada uno a uno alcanzaban un rendimiento dos desviaciones estándar por encima del promedio de un aula tradicional (efecto 2-Sigma).

El `SocraticTutorEngine.kt` reproduce este efecto guiando al estudiante a través de la duda metódica y el descubrimiento activo:
1. **Nunca regala la respuesta directa:** Si el estudiante comete un error, el tutor no dice "la respuesta es B". En su lugar, detecta el concepto erróneo (`misconception`) y formula una pregunta que evidencia la contradicción.
2. **Andamiaje Progresivo de 3 Niveles (`SocraticHintTier`):**
   - **Nivel 1 (Pista Leve / Orientativa):** "¿Qué datos te proporciona el problema y qué incógnita debes despejar?"
   - **Nivel 2 (Principio Fundamental):** Recuerda la regla o ley rectora (ej. Segunda Ley de Newton $F = m \cdot a$, o ecuación canónica $(x-h)^2 + (y-k)^2 = r^2$).
   - **Nivel 3 (Analogía Isomorfa de Taller):** Puente conceptual con una situación práctica física (ej. comparar el frenado con neumáticos mojados vs secos).

---

## 2. Motor de Repetición Espaciada FSRS (Free Spaced Repetition Scheduler)

Basado en el modelo DSR (Dificultad, Estabilidad y Retención):

### Ecuación de Probabilidad de Retención
$$R(t, S) = \left(1 + \text{FACTOR} \cdot \frac{t}{S}\right)^{-\alpha}$$

Donde:
- $t$: Días transcurridos desde la última revisión.
- $S$: Estabilidad de la memoria (días que tarda la probabilidad de retención en caer al 90%).
- $D$: Dificultad intrínseca del concepto (escala 1.0 a 10.0).
- $\text{FACTOR} = 19.0 / 81.0 \approx 0.2345679$.
- $\alpha = 0.5$.

### Agendamiento Óptimo
Cuando $R(t, S)$ desciende por debajo de **0.90 (90%)**, el concepto entra automáticamente en la bandeja de repaso prioritario (`isDue = true`). Si el estudiante acierta, la estabilidad se multiplica según la dificultad lograda; si falla, la estabilidad entra en periodo de enfriamiento y reaprendizaje.

---

## 3. Sandboxes Explorables Nativos (Compose)

Los sandboxes permiten al estudiante manipular parámetros físicos y matemáticos en tiempo real:
- **`AnalyticalGeometrySandbox.kt`:**
  - Manipulación táctil del centro $(h, k)$ y radio $r$.
  - Ecuación canónica renderizada en tiempo real.
  - Clasificación geométrica de rectas (secantes, tangentes o exteriores) mediante el discriminante $\Delta = b^2 - 4ac$.
- **`ElectricalCircuitSandbox.kt`:**
  - Circuito DC con fuente de 12V, interruptor, fusible térmico y resistencia variable.
  - Multímetro digital interactivo con puntas de prueba (roja y negra).
  - Simulación de la Ley de Ohm ($I = V/R$) y quema de fusibles ante sobrecorrientes.
