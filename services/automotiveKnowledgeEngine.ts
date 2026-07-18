import { 
  DocumentType, 
  SourceType, 
  ExtractionStatus, 
  KnowledgeDocument, 
  KnowledgeChunk, 
  KnowledgeCitation, 
  TorqueSpecCard, 
  FluidSpecCard, 
  DiagnosticProcedureCard, 
  WiringReferenceCard, 
  MaintenanceIntervalCard, 
  ProcedureStep, 
  KnowledgeAnswerQuality, 
  AiKnowledgeContext,
  VehicleProfile
} from '../types';
import { saveState, loadState } from './storage';
import { createId } from './ids';

// Tokenizer utility for Full Text Search (FTS)
export function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // remove spanish accents
    .replace(/[^a-z0-9\s]/g, ' ')   // remove punctuation
    .split(/\s+/)
    .filter(token => token.length > 1);
}

// SHA-256 calculation in browser using Web Crypto API
export async function calculateSha256(content: string): Promise<string> {
  try {
    const encoder = new TextEncoder();
    const data = encoder.encode(content);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  } catch (e) {
    // Fallback if subtle crypto is not available
    let hash = 0;
    for (let i = 0; i < content.length; i++) {
      const char = content.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return 'sha256_mock_' + Math.abs(hash).toString(16) + '_' + Date.now();
  }
}

// Inverted FTS Index for client-side search ranking (BM25 simulation)
export class LocalFtsIndex {
  private index: Map<string, Set<string>> = new Map(); // token -> set of chunk_ids
  private chunks: Map<string, KnowledgeChunk> = new Map(); // chunk_id -> chunk

  public clear(): void {
    this.index.clear();
    this.chunks.clear();
  }

  public addChunk(chunk: KnowledgeChunk): void {
    this.chunks.set(chunk.id, chunk);
    const tokens = tokenize(chunk.text);
    tokens.forEach(token => {
      if (!this.index.has(token)) {
        this.index.set(token, new Set());
      }
      this.index.get(token)!.add(chunk.id);
    });
  }

  public search(query: string, filterDocId?: string): { chunk: KnowledgeChunk; score: number }[] {
    const queryTokens = tokenize(query);
    if (queryTokens.length === 0) return [];

    const chunkScores: Map<string, number> = new Map();

    queryTokens.forEach(token => {
      // Find term matches
      const chunkIds = this.index.get(token);
      if (chunkIds) {
        chunkIds.forEach(chunkId => {
          const chunk = this.chunks.get(chunkId);
          if (!chunk) return;
          if (filterDocId && chunk.document_id !== filterDocId) return;

          // Simple TF-IDF ranking approximation: term frequency in chunk * inverse document frequency (document rarity)
          const textTokens = tokenize(chunk.text);
          const termCount = textTokens.filter(t => t === token).length;
          const tf = termCount / textTokens.length;
          
          const totalDocsWithTerm = chunkIds.size;
          const idf = Math.log(1 + (this.chunks.size / (1 + totalDocsWithTerm)));
          
          const tokenWeight = token.match(/^p\d{4}$/i) ? 5.0 : 1.0; // High weight for DTC codes (e.g. P0230)
          
          const score = tf * idf * tokenWeight;
          chunkScores.set(chunkId, (chunkScores.get(chunkId) || 0) + score);
        });
      }
    });

    return Array.from(chunkScores.entries())
      .map(([chunkId, score]) => ({
        chunk: this.chunks.get(chunkId)!,
        score
      }))
      .sort((a, b) => b.score - a.score);
  }
}

export class AutomotiveKnowledgeRagEngine {
  private documents: KnowledgeDocument[] = [];
  private chunks: KnowledgeChunk[] = [];
  private ftsIndex = new LocalFtsIndex();

  // Structured Technical Cards Database
  private torqueCards: TorqueSpecCard[] = [];
  private fluidCards: FluidSpecCard[] = [];
  private procedureCards: DiagnosticProcedureCard[] = [];
  private wiringCards: WiringReferenceCard[] = [];
  private maintenanceCards: MaintenanceIntervalCard[] = [];

  constructor() {
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    const docs = loadState<KnowledgeDocument[]>('knowledge_docs', []);
    const chks = loadState<KnowledgeChunk[]>('knowledge_chunks', []);
    
    const torques = loadState<TorqueSpecCard[]>('knowledge_torques', []);
    const fluids = loadState<FluidSpecCard[]>('knowledge_fluids', []);
    const procedures = loadState<DiagnosticProcedureCard[]>('knowledge_procedures', []);
    const wirings = loadState<WiringReferenceCard[]>('knowledge_wirings', []);
    const maint = loadState<MaintenanceIntervalCard[]>('knowledge_maintenances', []);

    if (docs.length === 0) {
      // Initialize with MVP seed database
      this.seedDatabase();
    } else {
      this.documents = docs;
      this.chunks = chks;
      this.torqueCards = torques;
      this.fluidCards = fluids;
      this.procedureCards = procedures;
      this.wiringCards = wirings;
      this.maintenanceCards = maint;

      this.rebuildFtsIndex();
    }
  }

  private saveToStorage(): void {
    saveState('knowledge_docs', this.documents);
    saveState('knowledge_chunks', this.chunks);
    saveState('knowledge_torques', this.torqueCards);
    saveState('knowledge_fluids', this.fluidCards);
    saveState('knowledge_procedures', this.procedureCards);
    saveState('knowledge_wirings', this.wiringCards);
    saveState('knowledge_maintenances', this.maintenanceCards);
  }

  private rebuildFtsIndex(): void {
    this.ftsIndex.clear();
    this.chunks.forEach(chk => this.ftsIndex.addChunk(chk));
  }

  private seedDatabase(): void {
    this.documents = [
      {
        id: 'doc_hyundai_accent_2005_shop',
        owner_user_id: 'system',
        vehicle_id_nullable: null,
        title: 'Manual de Servicio Técnico - Hyundai Accent LC 2005',
        source_type: SourceType.OFFICIAL_SOURCE,
        document_type: DocumentType.REPAIR_MANUAL,
        file_uri: '/manuals/oem/hyundai_accent_2005_lc.pdf',
        file_hash_sha256: '71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e',
        mime_type: 'application/pdf',
        size_bytes: 48590300,
        language: 'es',
        make_nullable: 'Hyundai',
        model_nullable: 'Accent',
        year_from_nullable: 2000,
        year_to_nullable: 2006,
        engine_nullable: '1.6L Alpha I4',
        transmission_nullable: 'AUTOMATIC/MANUAL',
        market_region_nullable: 'LATAM/CR',
        source_url_nullable: 'https://hyundai-service-manuals.org',
        license_note_nullable: 'Licencia OEM adquirida para taller Elysium',
        is_offline_available: true,
        extraction_status: ExtractionStatus.READY,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      },
      {
        id: 'doc_toyota_corolla_2010_maint',
        owner_user_id: 'system',
        vehicle_id_nullable: null,
        title: 'Guía de Mantenimiento y Fluidos - Toyota Corolla E140 2010',
        source_type: SourceType.OPEN_SOURCE,
        document_type: DocumentType.MAINTENANCE_SCHEDULE,
        file_uri: '/manuals/open/toyota_corolla_2010.pdf',
        file_hash_sha256: '32c1c38fa802f0907a56fb29928372cc62b2b115433ba9b626887556f8902888',
        mime_type: 'application/pdf',
        size_bytes: 14592000,
        language: 'es',
        make_nullable: 'Toyota',
        model_nullable: 'Corolla',
        year_from_nullable: 2008,
        year_to_nullable: 2013,
        engine_nullable: '1.8L 2ZR-FE',
        transmission_nullable: 'AUTOMATIC/CVT',
        market_region_nullable: 'GLOBAL',
        source_url_nullable: 'https://toyota-tech.eu',
        license_note_nullable: 'Documentación pública homologada',
        is_offline_available: true,
        extraction_status: ExtractionStatus.READY,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      },
      {
        id: 'doc_obd_dtc_guide_generic',
        owner_user_id: 'system',
        vehicle_id_nullable: null,
        title: 'Guía de Procedimientos para Códigos OBD2 Genéricos V2',
        source_type: SourceType.OPEN_SOURCE,
        document_type: DocumentType.DIAGNOSTIC_PROCEDURE,
        file_uri: '/manuals/open/generic_obd2_diagnostics.pdf',
        file_hash_sha256: 'b6bb99f24300a8972e3cc000a6e9a6c905b1c1d8820ba9bfb772eeff62a0a288',
        mime_type: 'application/pdf',
        size_bytes: 8400200,
        language: 'es',
        make_nullable: null,
        model_nullable: null,
        year_from_nullable: 1996,
        year_to_nullable: 2026,
        engine_nullable: null,
        transmission_nullable: null,
        market_region_nullable: 'GLOBAL',
        source_url_nullable: 'https://obd-codes.com',
        license_note_nullable: 'Uso público libre bajo Creative Commons',
        is_offline_available: true,
        extraction_status: ExtractionStatus.READY,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      }
    ];

    // Seed Chunks (Manual Pages Content)
    this.chunks = [
      // P0230 - Hyundai Accent 2005
      {
        id: 'chk_p0230_1',
        document_id: 'doc_hyundai_accent_2005_shop',
        vehicle_id_nullable: null,
        section_title_nullable: 'Sistema de Combustible - Diagnóstico del Relé de Bomba P0230',
        page_start_nullable: 42,
        page_end_nullable: 43,
        chunk_index: 0,
        text: `CÓDIGO P0230: Circuito Primario de la Bomba de Combustible.
El circuito de control de la bomba de combustible consta del fusible de 15A (Fuel Pump Fuse), el relé de la bomba (Fuel Pump Relay) ubicado en la caja de fusibles del compartimento del motor, y el pin de control de la ECU (ECM Fuel Pump Control).
Procedimiento de Diagnóstico OEM:
1. Verifique el fusible de la bomba de combustible de 15A. Si está fundido, inspeccione cortocircuito a tierra en el cable verde/blanco.
2. Extraiga el relé de la bomba y mida el voltaje en el pin 85 (alimentación del trigger de la ECU). Debe marcar 12V con llave en ON durante 2 segundos.
3. Mida la resistencia de la bobina del relé entre los terminales 85 y 86. El valor nominal debe ser entre 80 y 100 ohmios. Si está fuera de rango, reemplace el relé de la bomba de combustible.
4. Verifique la continuidad del terminal 86 a la tierra del chasis. Debe marcar menos de 1.0 ohmios.
5. Inyecte 12V directos al terminal 87 del zócalo del relé y compruebe si la bomba de combustible enciende y genera una presión de combustible nominal de 45 PSI (3.1 bar).`,
        token_count: 220,
        embedding_vector_nullable: null,
        content_hash: 'hash_p0230_chunk_1',
        created_at: new Date().toISOString()
      },
      // P0171 - MAF & Leans
      {
        id: 'chk_p0171_1',
        document_id: 'doc_obd_dtc_guide_generic',
        vehicle_id_nullable: null,
        section_title_nullable: 'Diagnóstico de Mezcla P0171 (Lean Bank 1)',
        page_start_nullable: 120,
        page_end_nullable: 121,
        chunk_index: 0,
        text: `DTC P0171 - SISTEMA DEMASIADO POBRE (BANK 1)
Este código se genera cuando el sensor de oxígeno indica una lectura de mezcla pobre sostenida y la compensación de combustible (Fuel Trim) a corto y largo plazo excede un acumulado de +10% a +25%.
Posibles causas ordenadas por probabilidad técnica:
1. Fuga de vacío en el colector de admisión (juntas agrietadas, manguera PCV rota o suelta).
2. Sensor de flujo de aire (MAF) sucio o defectuoso, reportando menos flujo de aire del real a la ECU.
3. Presión de combustible baja (filtro de combustible obstruido o bomba de combustible débil).
Procedimiento de Verificación Técnica:
- Monitoree el PID del sensor MAF a ralentí. Un Hyundai Accent o Corolla de cilindrada similar (1.6L - 1.8L) debe reportar entre 1.5 y 2.5 g/s en ralentí caliente (750 RPM). Si reporta menos de 1.2 g/s, limpie el elemento sensor MAF con limpiador de contactos dieléctrico.
- Realice una prueba de humo o rocíe limpiador de carburador de forma selectiva en juntas del colector de admisión y mangueras de vacío. Si el motor acelera, ha localizado una fuga de vacío física.`,
        token_count: 215,
        embedding_vector_nullable: null,
        content_hash: 'hash_p0171_chunk_1',
        created_at: new Date().toISOString()
      },
      // P0300 - Random Misfire
      {
        id: 'chk_p0300_1',
        document_id: 'doc_hyundai_accent_2005_shop',
        vehicle_id_nullable: null,
        section_title_nullable: 'Sistema de Ignición - Diagnóstico de Fallas de Encendido P0300',
        page_start_nullable: 78,
        page_end_nullable: 79,
        chunk_index: 0,
        text: `DTC P0300 - FALLAS DE ENCENDIDO ALEATORIAS (MULTIPLE CYLINDER MISFIRE)
La ECU detecta fallas de encendido midiendo variaciones microscópicas de velocidad angular del cigüeñal mediante el sensor CKP.
Procedimiento de Reparación en Accent 1.6L:
1. Inspeccione las bujías de níquel. El espacio de calibración de bujía (spark plug gap) debe ser exactamente de 1.1 mm (0.044 in). Reemplace las bujías si los electrodos muestran desgaste severo o acumulación de carbón.
2. Torque de bujías en culata: 20 Nm a 25 Nm (no exceda este torque para evitar dañar la rosca de aluminio).
3. Mida la resistencia de los cables de bujías (nominal: 5.6 kilohmios por metro). Reemplace si supera 10 kilohmios.
4. Pruebe la bobina de encendido. Resistencia primaria entre pines de control: 0.7 ohmios. Resistencia secundaria en la torre de salida: 12 kilohmios.`,
        token_count: 180,
        embedding_vector_nullable: null,
        content_hash: 'hash_p0300_chunk_1',
        created_at: new Date().toISOString()
      },
      // P0420 - Catalytic Converter
      {
        id: 'chk_p0420_1',
        document_id: 'doc_obd_dtc_guide_generic',
        vehicle_id_nullable: null,
        section_title_nullable: 'Sistema de Escape - Catalizador P0420',
        page_start_nullable: 154,
        page_end_nullable: 155,
        chunk_index: 0,
        text: `DTC P0420 - EFICIENCIA DEL SISTEMA CATALIZADOR POR DEBAJO DEL LÍMITE (BANK 1)
La ECU monitorea el catalizador comparando la actividad del sensor de oxígeno 1 (frontal, antes del catalizador) contra el sensor de oxígeno 2 (trasero, después del catalizador).
- Comportamiento de Señales Normales: El sensor O2 frontal debe oscilar constantemente entre 0.1V y 0.9V (mezcla rica/pobre rápida). El sensor O2 trasero debe permanecer casi plano y estable entre 0.5V y 0.7V, lo que indica que el catalizador está consumiendo el oxígeno restante de la combustión.
- Diagnóstico de Catalizador Obstruido / Degradado: Si el sensor O2 trasero oscila imitando el comportamiento del sensor frontal, el catalizador ha perdido su capacidad de almacenamiento de oxígeno. Antes de condenar el convertidor catalítico, inspeccione fugas de escape en los colectores de escape y bridas frontales.`,
        token_count: 185,
        embedding_vector_nullable: null,
        content_hash: 'hash_p0420_chunk_1',
        created_at: new Date().toISOString()
      },
      // P0562 - Low Voltage Charging System
      {
        id: 'chk_p0562_1',
        document_id: 'doc_hyundai_accent_2005_shop',
        vehicle_id_nullable: null,
        section_title_nullable: 'Sistema Eléctrico y Carga - P0562',
        page_start_nullable: 202,
        page_end_nullable: 202,
        chunk_index: 0,
        text: `DTC P0562 - VOLTAJE DEL SISTEMA BAJO (SYSTEM VOLTAGE LOW)
Este código se registra si el voltaje de la ECU cae por debajo de 11.6V por más de 10 segundos mientras el motor está encendido.
Procedimiento de Diagnóstico y Pruebas del Alternador:
1. Conecte un multímetro a las bornas de la batería. Mida el voltaje en ralentí sin cargas. Debe ser entre 13.8V y 14.4V.
2. Encienda faros altos, aire acondicionado en ventilación máxima y desempañador. El voltaje de carga no debe caer por debajo de 13.2V. Si el voltaje cae a 12V o menos con carga, el alternador está fallando o el cable B+ tiene alta resistencia.
3. Inspeccione la caída de tensión en el cable positivo del alternador a la batería (máximo permitido: 0.2V).
4. Verifique la tensión de la correa de accesorios (alternador/bomba agua). Flexión recomendada al presionar con el pulgar: 8-10 mm.`,
        token_count: 190,
        embedding_vector_nullable: null,
        content_hash: 'hash_p0562_chunk_1',
        created_at: new Date().toISOString()
      },
      // Suspension chunks for Hyundai Accent 2005 1.6
      {
        id: 'chk_accent_suspension_1',
        document_id: 'doc_hyundai_accent_2005_shop',
        vehicle_id_nullable: 'veh_default_accent_2005',
        section_title_nullable: 'Suspensión Delantera - Brazo de Control Inferior y Rótula',
        page_start_nullable: 245,
        page_end_nullable: 247,
        chunk_index: 0,
        text: `ARQUITECTURA DE SUSPENSIÓN HYUNDAI ACCENT 2005:
El Accent 2005 LC utiliza suspensión delantera independiente tipo McPherson. El conjunto consta de amortiguador/strut McPherson, resorte helicoidal, copela superior, rodamiento de copela, mangueta, barra estabilizadora, bieletas y brazo de control inferior (tijereta) con rótula.
Esta plataforma no equipa brazo superior ni rótula superior independientes. El control superior lo realiza el amortiguador (strut).
PROCEDIMIENTO DE REEMPLAZO DEL BRAZO INFERIOR (TIJERETA):
1. Aflojar tuercas de llanta. Elevar carro y colocar borriquetas. Retirar llanta.
2. Desconectar la bieleta de la barra estabilizadora sosteniendo el espárrago de la bieleta con una llave fija mientras afloja la tuerca para no dañar el fuelle protector.
3. Retirar el pasador de chaveta y la tuerca almena de la rótula. Utilizar extractor de rótulas para separar el vástago cónico de la mangueta.
4. Quitar los pernos de fijación de los dos bujes (delantero y trasero) del brazo al subchasis delantero. Retirar el brazo inferior.
5. Colocar el nuevo brazo inferior con bujes nuevos en el subchasis. Insertar los pernos sin apretar completamente.
6. Conectar la rótula a la mangueta, apretar la tuerca almena al torque nominal de 60-72 N·m e instalar un pasador nuevo.
7. ATENCIÓN: Baje el carro a sus llantas (altura normal) y solo entonces apriete los pernos de los bujes del brazo inferior al subchasis a su torque nominal de 95-120 N·m. Apretar bujes colgando los torsionará permanentemente acortando su vida útil.
8. Realice una alineación digital de dirección obligatoria para ajustar el toe.`,
        token_count: 310,
        embedding_vector_nullable: null,
        content_hash: 'hash_accent_suspension_chunk_1',
        created_at: new Date().toISOString()
      }
    ];

    // Seed Torque Cards
    this.torqueCards = [
      {
        id: 'tq_accent_01',
        vehicle_id: 'veh_default_accent_2005',
        component: 'Tornillos de Culata (Cylinder Head Bolts) - Hyundai Accent 2005 1.6L Alpha',
        fastener: '10 tornillos M10 con arandela',
        torque_value: 30,
        unit: 'Nm',
        angle_nullable: 90,
        sequence_notes: 'Secuencia de ajuste en espiral desde el centro hacia afuera. Paso 1: Apretar todos a 30 Nm. Paso 2: Apretar todos a 60 Nm. Paso 3: Aflojar 180° todos. Paso 4: Apretar a 30 Nm. Paso 5: Girar 90° adicionales todos en la misma secuencia.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        page_nullable: 48,
        confidence: 'HIGH'
      },
      {
        id: 'tq_corolla_01',
        vehicle_id: 'veh_default_corolla_2010',
        component: 'Bujías (Spark Plugs) - Toyota Corolla 1.8L',
        fastener: 'Bujía rosca de 14mm con junta aplastable',
        torque_value: 20,
        unit: 'Nm',
        angle_nullable: null,
        sequence_notes: 'Apretar directamente a mano y luego torquear a 20 Nm. Evite usar grasas lubricantes en la rosca de la bujía nueva para no alterar el torque real.',
        source_document_id: 'doc_toyota_corolla_2010_maint',
        page_nullable: 32,
        confidence: 'HIGH'
      },
      {
        id: 'tq_accent_02',
        vehicle_id: 'veh_default_accent_2005',
        component: 'Tapa de Válvulas (Valve Cover) - Hyundai Accent 1.6L',
        fastener: 'Tornillos M6 con arandela de goma',
        torque_value: 10,
        unit: 'Nm',
        angle_nullable: null,
        sequence_notes: 'Apretar en cruz desde el centro. No exceder 10 Nm para evitar fisurar la tapa plástica.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        page_nullable: 50,
        confidence: 'HIGH'
      },
      {
        id: 'tq_accent_suspension_01',
        vehicle_id: 'veh_default_accent_2005',
        component: 'Brazo de Control Inferior (Bujes al Subchasis) - Hyundai Accent 2005',
        fastener: 'Pernos M12 Grado 10.9',
        torque_value: 110,
        unit: 'Nm',
        angle_nullable: null,
        sequence_notes: 'Apretar OBLIGATORIAMENTE con el peso del vehículo sobre las ruedas (posición de altura normal de rodaje). Rango permitido: 95-120 N·m.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        page_nullable: 246,
        confidence: 'HIGH'
      },
      {
        id: 'tq_accent_suspension_02',
        vehicle_id: 'veh_default_accent_2005',
        component: 'Tuerca Almena de Rótula Inferior a Mangueta - Hyundai Accent 2005',
        fastener: 'Tuerca almena M14 x 1.5 con pasador de chaveta',
        torque_value: 65,
        unit: 'Nm',
        angle_nullable: null,
        sequence_notes: 'Apretar a 60-72 N·m. Si la ranura no coincide con el orificio del pasador, apriete ligeramente más. Nunca afloje.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        page_nullable: 247,
        confidence: 'HIGH'
      },
      {
        id: 'tq_accent_suspension_03',
        vehicle_id: 'veh_default_accent_2005',
        component: 'Tuercas de Ruedas Delanteras - Hyundai Accent 2005',
        fastener: '4 Tuercas M12 x 1.5 con asiento cónico',
        torque_value: 100,
        unit: 'Nm',
        angle_nullable: null,
        sequence_notes: 'Apretar en estrella/patrón cruzado. Rango permitido: 90-110 N·m (65-80 lb·ft). No apretar con pistola neumática como torque final.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        page_nullable: 260,
        confidence: 'HIGH'
      }
    ];

    // Seed Fluid Cards
    this.fluidCards = [
      {
        id: 'fl_accent_01',
        vehicle_id: 'veh_default_accent_2005',
        system: 'Aceite de Motor (Engine Oil)',
        fluid_type: '5W-30 o 10W-30 API SN o superior, totalmente sintético',
        capacity: 3.3,
        unit: 'Liters',
        specification: 'OEM Hyundai SP-SN / ILSAC GF-5. Incluye el volumen del filtro de aceite (0.3L).',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        confidence: 'HIGH'
      },
      {
        id: 'fl_accent_02',
        vehicle_id: 'veh_default_accent_2005',
        system: 'Transmisión Automática (ATF)',
        fluid_type: 'Hyundai Genuine ATF SP-III',
        capacity: 6.1,
        unit: 'Liters',
        specification: 'Solo usar SP-III de alta calidad. No mezclar con Dexron-VI ya que provocará patinados de discos.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        confidence: 'HIGH'
      },
      {
        id: 'fl_corolla_01',
        vehicle_id: 'veh_default_corolla_2010',
        system: 'Anticongelante de Radiador (Coolant)',
        fluid_type: 'Toyota Super Long Life Coolant (SLLC)',
        capacity: 5.5,
        unit: 'Liters',
        specification: 'Líquido pre-mezclado rosa al 50/50. No requiere dilución con agua corriente.',
        source_document_id: 'doc_toyota_corolla_2010_maint',
        confidence: 'HIGH'
      }
    ];

    // Seed Diagnostic Cards
    this.procedureCards = [
      {
        id: 'dp_p0230_1',
        vehicle_id_nullable: null,
        dtc_code_nullable: 'P0230',
        symptom_nullable: 'El motor gira pero no enciende; motor se apaga repentinamente; baja presión de riel.',
        system: 'Sistema de Alimentación de Combustible',
        title: 'Prueba de Circuito Primario de Bomba de Combustible (P0230)',
        tools_required: ['Multímetro Digital', 'Manómetro de Combustible OBD/Físico', 'Cables puente con fusible'],
        steps: [
          'Verificar el fusible de la bomba de combustible de 15A en la fusiblera del compartimiento del motor.',
          'Extraer el relé de la bomba de combustible. Probar continuidad en la bobina de control del relé (terminales 85 y 86). Debe marcar entre 80 y 100 ohmios.',
          'Medir voltaje de control en el zócalo del relé, pin 85 con llave en posición de contacto ON. Debe recibir un pulso de 12V temporal por 2 segundos antes de apagarse.',
          'Verificar la resistencia a tierra en el pin 86. Debe marcar menos de 1.0 ohmios respecto al chasis.',
          'Realizar un puente con fusible entre el pin de entrada constante (30) y el de salida (87) en el zócalo del relé. La bomba debe activarse de inmediato y escucharse en el tanque de combustible.',
          'Conectar manómetro y verificar que la presión alcance los 45 PSI. Si la presión es baja y el voltaje/tierra en el conector de la bomba son correctos, considerar reemplazar la bomba.'
        ],
        expected_results: [
          'Fusible sano (continuidad < 0.5 ohmios).',
          'Bobina del relé en 88 ohmios.',
          'Pulso de 12V recibido de la ECU en pin 85.',
          'Línea de tierra del chasis en pin 86 sana (0.2 ohmios).',
          'Puente activa la bomba y presión estable en 45 PSI.'
        ],
        safety_notes: [
          '¡PELIGRO! El sistema almacena combustible altamente presionado. Alivie la presión usando la válvula del riel antes de conectar el manómetro.',
          'Mantenga extintor tipo B cerca del área de trabajo. No use lámparas de prueba incandescentes debido a riesgos de chispa.'
        ],
        source_document_id: 'doc_obd_dtc_guide_generic',
        confidence: 'HIGH'
      },
      {
        id: 'dp_p0171_1',
        vehicle_id_nullable: null,
        dtc_code_nullable: 'P0171',
        symptom_nullable: 'Ralentí inestable; luz check engine encendida; pérdida de potencia.',
        system: 'Admisión de Aire / Combustible',
        title: 'Verificación de Sistema Pobre P0171 (Lean Mixture)',
        tools_required: ['Escáner OBD2 (Verificación de PIDs)', 'Líquido detector de fugas de vacío / Humo', 'Limpiador de MAF'],
        steps: [
          'Conectar escáner y verificar el PID de Long Term Fuel Trim (LTFT). Si el LTFT supera el +15%, la mezcla es extremadamente pobre.',
          'Monitorear el valor del sensor MAF a 750 RPM con motor caliente. Debe estar entre 1.5 y 2.5 g/s. Si está bajo, proceder a limpiar el sensor MAF.',
          'Rociar limpiador de carburador de forma selectiva en juntas del colector de admisión y mangueras de vacío. Si el motor acelera, ha localizado una fuga de vacío física.',
          'Inspeccionar manguera de ventilación del cárter (PCV) en busca de grietas o desconexiones.'
        ],
        expected_results: [
          'LTFT corregido por debajo de +/- 5% después de corregir fugas.',
          'Flujo de aire del sensor MAF reportando lecturas normales de 2.0 g/s en ralentí.',
          'Ausencia de fugas físicas o grietas en mangueras.'
        ],
        safety_notes: [
          'No rocíe solventes sobre colectores calientes o escapes para prevenir incendios.',
          'Use gafas de seguridad al trabajar con aerosoles.'
        ],
        source_document_id: 'doc_obd_dtc_guide_generic',
        confidence: 'HIGH'
      },
      {
        id: 'dp_accent_suspension_01',
        vehicle_id_nullable: 'veh_default_accent_2005',
        dtc_code_nullable: null,
        symptom_nullable: 'Golpeteo (clonk) al cruzar baches; el vehículo tira hacia un lado al frenar; desgaste asimétrico de llantas.',
        system: 'Suspensión Delantera',
        title: 'Reemplazo del Brazo de Control Inferior Delantero (Tijereta) y Rótula',
        tools_required: ['Borriquetas', 'Gato hidráulico', 'Extractor de rótula cónico', 'Llaves y Dados de 14mm, 17mm, 19mm', 'Torquímetro'],
        steps: [
          'Elevar el vehículo por el chasis y colocar borriquetas de seguridad. Retirar llanta delantera izquierda.',
          'Sujetar el espárrago de la bieleta de barra estabilizadora con llave combinada de 14mm y aflojar tuerca de fijación.',
          'Extraer el pasador de chaveta de la rótula inferior, aflojar tuerca de 17mm y colocar separador de rótula. Accionar extractor para destrabar el cono de la mangueta.',
          'Aflojar y retirar los pernos delantero y trasero que sujetan los bujes del brazo al subchasis.',
          'Colocar el brazo nuevo, presentar pernos del subchasis y tuerca de rótula con pasador nuevo. Conectar bieleta.',
          'Bajar el carro sobre sus llantas a su altura de rodaje y aplicar torque final de 95-120 N·m a los pernos del subchasis.',
          'Llevar a alinear la dirección para ajustar la divergencia/convergencia (toe).'
        ],
        expected_results: [
          'Cono de rótula liberado limpiamente de mangueta.',
          'Pernos de bujes retirados sin dañar roscas internas del subchasis.',
          'Bujes apretados en posición neutra cargada.',
          'Ruido metálico eliminado y alineación dentro de especificaciones.'
        ],
        safety_notes: [
          '¡ADVERTENCIA! Nunca trabaje debajo de un vehículo soportado solo por gato hidráulico. Use borriquetas en los puntos de apoyo recomendados.',
          'No use soplete para calentar la mangueta o el brazo para retirar bujes/rótulas viejas ya que alterará las propiedades mecánicas del acero templado.'
        ],
        source_document_id: 'doc_hyundai_accent_2005_shop',
        confidence: 'HIGH'
      }
    ];

    // Seed Wiring Cards
    this.wiringCards = [
      {
        id: 'wr_p0230_1',
        vehicle_id_nullable: null,
        circuit_name: 'Circuito de Control de Bomba de Combustible (Hyundai Accent LC)',
        related_dtcs: ['P0230', 'P0231', 'P0232'],
        connectors: ['Conector de Caja de Fusibles del Motor (M11)', 'Conector de Bomba de Combustible FP02 bajo asiento trasero'],
        pins: ['Pin 85: Control de Activación Relé (ECU)', 'Pin 86: Tierra Bobina Relé', 'Pin 30: Entrada 12V Batería (Fusible FP 15A)', 'Pin 87: Salida Alimentación Bomba Combustible'],
        wire_colors: ['Azul (ECU a Relé pin 85)', 'Negro (Tierra a Relé pin 86)', 'Rojo (Fusible a Relé pin 30)', 'Verde/Blanco (Relé pin 87 a conector de la bomba)'],
        expected_voltages: ['Pin 85: 12V en ignición (2 seg) o motor en marcha', 'Pin 30: 12V constantes de batería', 'Pin 87: 12V cuando el relé se cierra'],
        grounds: ['Tierra del relé conectada en el chasis del compartimiento del motor (G02)', 'Tierra de la bomba bajo el panel trasero (G08)'],
        source_document_id: 'doc_hyundai_accent_2005_shop',
        confidence: 'HIGH'
      },
      {
        id: 'wr_p0562_1',
        vehicle_id_nullable: null,
        circuit_name: 'Circuito del Alternador y Sistema de Carga (Hyundai Accent LC)',
        related_dtcs: ['P0562', 'P0563'],
        connectors: ['Alternador Generador A01', 'Caja de Relés y Fusibles Central'],
        pins: ['Terminal B+: Salida Alternador a Batería (M8)', 'Terminal IG: Entrada de Ignición de 12V', 'Terminal L: Entrada del Testigo de Batería en el Cuadro'],
        wire_colors: ['Rojo Grueso (B+ a Batería)', 'Negro/Amarillo (IG de llave contacto)', 'Azul/Blanco (L al Cuadro de Instrumentos)'],
        expected_voltages: ['Terminal B+: 13.8V a 14.4V con motor encendido', 'Terminal IG: 12V con llave en ON', 'Terminal L: 0V con motor parado / 12V con motor encendido'],
        grounds: ['Carcasa del alternador a bloque del motor', 'Bloque de motor a borne negativo de batería (<0.1V de caída)'],
        source_document_id: 'doc_hyundai_accent_2005_shop',
        confidence: 'HIGH'
      }
    ];

    // Seed Maintenance Cards
    this.maintenanceCards = [
      {
        id: 'mt_01',
        vehicle_id: null,
        service_item: 'Reemplazo del Filtro de Combustible (Fuel Filter Replacement)',
        interval_km_nullable: 60000,
        interval_months_nullable: 36,
        severe_service_interval_nullable: 'Reemplazar cada 40,000 KM o 24 meses si conduce con combustible de alta contaminación o mucho ralentí.',
        source_document_id: 'doc_obd_dtc_guide_generic',
        confidence: 'HIGH'
      },
      {
        id: 'mt_02',
        vehicle_id: null,
        service_item: 'Reemplazo de Bujías de Níquel (Nickel Spark Plugs Replacement)',
        interval_km_nullable: 40000,
        interval_months_nullable: 24,
        severe_service_interval_nullable: 'Reemplazar cada 30,000 KM si conduce constantemente en distancias cortas o frío severo.',
        source_document_id: 'doc_hyundai_accent_2005_shop',
        confidence: 'HIGH'
      }
    ];

    this.rebuildFtsIndex();
    this.saveToStorage();
  }

  // --- RAG PIPELINE METHODS ---

  public searchManuals(query: string, vehicle?: VehicleProfile | null): { document: KnowledgeDocument; chunk: KnowledgeChunk; score: number }[] {
    const results = this.ftsIndex.search(query);
    
    return results
      .map(r => {
        const doc = this.documents.find(d => d.id === r.chunk.document_id)!;
        return {
          document: doc,
          chunk: r.chunk,
          score: r.score
        };
      })
      .filter(res => {
        if (!vehicle) return true;

        const doc = res.document;
        if (doc.make_nullable && doc.make_nullable.toLowerCase() !== vehicle.make.toLowerCase()) {
          return false;
        }
        
        if (doc.year_from_nullable && vehicle.year < doc.year_from_nullable) return false;
        if (doc.year_to_nullable && vehicle.year > doc.year_to_nullable) return false;

        return true;
      });
  }

  public answerTechnicalQuestion(
    query: string, 
    activeVehicle: VehicleProfile | null, 
    activeDtc: string | null = null,
    symptoms: string[] = []
  ): { 
    answer: string; 
    citations: KnowledgeCitation[]; 
    quality: KnowledgeAnswerQuality; 
    confidence: 'LOW' | 'MEDIUM' | 'HIGH';
    torqueCard?: TorqueSpecCard;
    fluidCard?: FluidSpecCard;
    procedureCard?: DiagnosticProcedureCard;
    wiringCard?: WiringReferenceCard;
    maintenanceCard?: MaintenanceIntervalCard;
  } {
    const normalizedQuery = query.toLowerCase();
    
    // 1. Detect Intent
    let isTorqueQuery = normalizedQuery.includes('torque') || normalizedQuery.includes('apriete') || normalizedQuery.includes('culata');
    let isFluidQuery = normalizedQuery.includes('fluido') || normalizedQuery.includes('aceite') || normalizedQuery.includes('capacidad') || normalizedQuery.includes('litro');
    let isWiringQuery = normalizedQuery.includes('diagrama') || normalizedQuery.includes('circuito') || normalizedQuery.includes('cable') || normalizedQuery.includes('pin') || normalizedQuery.includes('color');
    let isMaintQuery = normalizedQuery.includes('mantenimiento') || normalizedQuery.includes('intervalo') || normalizedQuery.includes('filtro') || normalizedQuery.includes('bujia');
    
    let dtcCode: string | null = activeDtc;
    const dtcMatch = normalizedQuery.match(/p\d{4}/i);
    if (dtcMatch) {
      dtcCode = dtcMatch[0].toUpperCase();
    }

    // 2. Fetch Relevant Chunks
    const searchResults = this.searchManuals(query, activeVehicle);
    
    // 3. Fallback check
    const hasMatchingDtcInDb = dtcCode ? this.chunks.some(c => tokenize(c.text).includes(dtcCode!.toLowerCase())) : false;
    const hasMatchingDoc = searchResults.length > 0;
    
    if (!hasMatchingDoc && !hasMatchingDtcInDb && !isTorqueQuery && !isFluidQuery && !isWiringQuery && !isMaintQuery) {
      return {
        answer: 'No tengo una fuente local confiable para confirmar eso. Sube un manual o verifica la fuente OEM.',
        citations: [],
        quality: KnowledgeAnswerQuality.UNSOURCED,
        confidence: 'LOW'
      };
    }

    const matchesVehicle = (cardVehicleId: string | null) => {
      if (!cardVehicleId) return true;
      if (!activeVehicle) return true;
      const cardType = cardVehicleId.toLowerCase();
      const userMake = activeVehicle.make.toLowerCase();
      if (cardType.includes('accent') && userMake !== 'hyundai') return false;
      if (cardType.includes('corolla') && userMake !== 'toyota') return false;
      return true;
    };

    // 4. Resolve exact Technical Cards
    let foundTorque: TorqueSpecCard | undefined;
    let foundFluid: FluidSpecCard | undefined;
    let foundProcedure: DiagnosticProcedureCard | undefined;
    let foundWiring: WiringReferenceCard | undefined;
    let foundMaint: MaintenanceIntervalCard | undefined;

    if (isTorqueQuery) {
      foundTorque = this.torqueCards.find(c => 
        matchesVehicle(c.vehicle_id) && 
        (normalizedQuery.includes(c.component.toLowerCase().split(' ')[0]) || 
         normalizedQuery.includes('culata') && c.component.toLowerCase().includes('culata'))
      );
    }
    
    if (isFluidQuery) {
      foundFluid = this.fluidCards.find(c => 
        matchesVehicle(c.vehicle_id) && 
        (normalizedQuery.includes(c.system.toLowerCase().split(' ')[0]) ||
         normalizedQuery.includes('transmision') && c.system.toLowerCase().includes('transmision') ||
         normalizedQuery.includes('motor') && c.system.toLowerCase().includes('motor'))
      );
    }

    if (dtcCode) {
      foundProcedure = this.procedureCards.find(c => c.dtc_code_nullable === dtcCode);
      foundWiring = this.wiringCards.find(c => c.related_dtcs.includes(dtcCode!));
    }

    if (isMaintQuery) {
      foundMaint = this.maintenanceCards.find(c => 
        normalizedQuery.includes(c.service_item.toLowerCase().split(' ')[0]) ||
        normalizedQuery.includes('bujia') && c.service_item.toLowerCase().includes('bujia') ||
        normalizedQuery.includes('filtro') && c.service_item.toLowerCase().includes('filtro')
      );
    }

    if (isTorqueQuery && normalizedQuery.includes('culata') && !foundTorque) {
      const vehicleDesc = activeVehicle ? `${activeVehicle.make} ${activeVehicle.model} ${activeVehicle.year}` : 'este vehículo';
      return {
        answer: `Torque de culata: no disponible en fuentes locales para ${vehicleDesc}. Sube un manual o verifica la fuente OEM.`,
        citations: [],
        quality: KnowledgeAnswerQuality.UNSOURCED,
        confidence: 'LOW'
      };
    }

    // 5. Construct answer and citations
    const citations: KnowledgeCitation[] = [];
    let answerText = '';
    let finalQuality = KnowledgeAnswerQuality.GENERIC_SYSTEM_KNOWLEDGE;
    let finalConfidence: 'LOW' | 'MEDIUM' | 'HIGH' = 'LOW';

    searchResults.slice(0, 3).forEach((res, idx) => {
      citations.push({
        id: `cit_${Date.now()}_${idx}`,
        chunk_id: res.chunk.id,
        document_id: res.document.id,
        page_start: res.chunk.page_start_nullable,
        page_end: res.chunk.page_end_nullable,
        quoted_text_short: res.chunk.text.slice(0, 100) + '...',
        confidence: res.score > 0.8 ? 'HIGH' : res.score > 0.3 ? 'MEDIUM' : 'LOW',
        applicability_note: res.document.make_nullable 
          ? `Aplicabilidad exacta: ${res.document.make_nullable} ${res.document.model_nullable || ''}`
          : 'Aplicabilidad genérica'
      });
    });

    if (citations.length > 0) {
      const bestScore = searchResults[0].score;
      finalConfidence = bestScore > 0.8 ? 'HIGH' : bestScore > 0.3 ? 'MEDIUM' : 'LOW';
      
      const doc = searchResults[0].document;
      if (activeVehicle && doc.make_nullable && doc.make_nullable.toLowerCase() === activeVehicle.make.toLowerCase()) {
        finalQuality = KnowledgeAnswerQuality.EXACT_VEHICLE_SOURCE;
      } else {
        finalQuality = KnowledgeAnswerQuality.GENERIC_SYSTEM_KNOWLEDGE;
      }
    }

    if (foundTorque) {
      answerText += `⚙️ **Especificación de Torque Encontrada:**
**Componente:** ${foundTorque.component}
**Tornillería/Detalles:** ${foundTorque.fastener}
**Torque:** ${foundTorque.torque_value} ${foundTorque.unit} ${foundTorque.angle_nullable ? `+ ${foundTorque.angle_nullable}°` : ''}
**Instrucciones de Ajuste:** ${foundTorque.sequence_notes}

`;
      if (foundTorque.source_document_id) {
        const doc = this.documents.find(d => d.id === foundTorque!.source_document_id);
        if (doc) {
          citations.push({
            id: `cit_torque_${Date.now()}`,
            chunk_id: 'structured_card',
            document_id: doc.id,
            page_start: foundTorque.page_nullable,
            page_end: foundTorque.page_nullable,
            quoted_text_short: `Torque Spec: ${foundTorque.component} -> ${foundTorque.torque_value} ${foundTorque.unit}`,
            confidence: 'HIGH',
            applicability_note: `Tarjeta estructurada - ${doc.title}`
          });
          finalQuality = KnowledgeAnswerQuality.EXACT_VEHICLE_SOURCE;
          finalConfidence = 'HIGH';
        }
      }
    }

    if (foundFluid) {
      answerText += `🛢️ **Especificación de Fluido Encontrada:**
**Sistema:** ${foundFluid.system}
**Tipo de Fluido:** ${foundFluid.fluid_type}
**Capacidad:** ${foundFluid.capacity} ${foundFluid.unit}
**Especificación:** ${foundFluid.specification}

`;
      if (foundFluid.source_document_id) {
        const doc = this.documents.find(d => d.id === foundFluid!.source_document_id);
        if (doc) {
          citations.push({
            id: `cit_fluid_${Date.now()}`,
            chunk_id: 'structured_card',
            document_id: doc.id,
            page_start: null,
            page_end: null,
            quoted_text_short: `Fluid Spec: ${foundFluid.system} -> ${foundFluid.capacity} L`,
            confidence: 'HIGH',
            applicability_note: `Tarjeta estructurada - ${doc.title}`
          });
          finalQuality = KnowledgeAnswerQuality.EXACT_VEHICLE_SOURCE;
          finalConfidence = 'HIGH';
        }
      }
    }

    if (foundProcedure) {
      answerText += `🔧 **Procedimiento Diagnóstico para ${dtcCode}:**
**Título:** ${foundProcedure.title}
**Síntomas Comunes:** ${foundProcedure.symptom_nullable}
**Herramientas Necesarias:** ${foundProcedure.tools_required.join(', ')}
**Pasos del Procedimiento:**
${foundProcedure.steps.map((step, idx) => `${idx + 1}. ${step}`).join('\n')}

`;
    }

    if (foundWiring) {
      answerText += `🔌 **Referencia de Cableado e Hilos:**
**Circuito:** ${foundWiring.circuit_name}
**Pines Principales:**
${foundWiring.pins.map(p => `• ${p}`).join('\n')}
**Colores de Cables:** ${foundWiring.wire_colors.join(', ')}
**Voltajes Esperados:** ${foundWiring.expected_voltages.join(' | ')}
**Tierras Relacionadas:** ${foundWiring.grounds.join(' | ')}

`;
    }

    if (searchResults.length > 0 && !foundTorque && !foundFluid && !foundProcedure) {
      answerText += `📖 **Información Técnica Recuperada:**
${searchResults[0].chunk.text}

`;
    }

    if (!answerText) {
      return {
        answer: 'No tengo una fuente local confiable para confirmar eso. Sube un manual o verifica la fuente OEM.',
        citations: [],
        quality: KnowledgeAnswerQuality.UNSOURCED,
        confidence: 'LOW'
      };
    }

    return {
      answer: answerText.trim(),
      citations,
      quality: finalQuality,
      confidence: finalConfidence,
      torqueCard: foundTorque,
      fluidCard: foundFluid,
      procedureCard: foundProcedure,
      wiringCard: foundWiring,
      maintenanceCard: foundMaint
    };
  }

  // --- GUIDED PROCEDURES ---

  public getGuidedProcedureForDtc(dtcCode: string): ProcedureStep[] {
    const card = this.procedureCards.find(c => c.dtc_code_nullable === dtcCode);
    if (!card) return [];

    return card.steps.map((step, index) => {
      let reqTool: string | null = null;
      if (step.toLowerCase().includes('fusible') || step.toLowerCase().includes('multimetro') || step.toLowerCase().includes('resistencia') || step.toLowerCase().includes('voltaje')) {
        reqTool = card.tools_required.find(t => t.toLowerCase().includes('multímetro')) || 'Multímetro Digital';
      } else if (step.toLowerCase().includes('manometro') || step.toLowerCase().includes('presion')) {
        reqTool = card.tools_required.find(t => t.toLowerCase().includes('manómetro')) || 'Manómetro de Presión';
      }

      let safetyWarn: string | null = null;
      if (step.toLowerCase().includes('bomba') || step.toLowerCase().includes('presion') || step.toLowerCase().includes('presionado')) {
        safetyWarn = card.safety_notes[0] || null;
      }

      return {
        id: `step_${card.id}_${index}`,
        procedure_id: card.id,
        order: index + 1,
        title: step.split('.')[0] || `Paso ${index + 1}`,
        instruction: step,
        required_tool_nullable: reqTool,
        expected_result_nullable: card.expected_results[index] || 'Confirmar estado operacional.',
        safety_warning_nullable: safetyWarn,
        evidence_required: index >= 2,
        source_chunk_id_nullable: 'chk_p0230_1'
      };
    });
  }

  // --- DOCUMENT UPLOAD/IMPORT ---

  public async importUserDocument(
    ownerId: string,
    title: string,
    fileUri: string,
    fileContent: string,
    mimeType: string,
    sizeBytes: number,
    vehicleApplicability?: { make: string; model: string; yearFrom: number; yearTo: number } | null
  ): Promise<{ doc: KnowledgeDocument; chunks: KnowledgeChunk[] }> {
    const docId = `doc_user_${Date.now()}`;
    const hash = await calculateSha256(fileContent);

    const doc: KnowledgeDocument = {
      id: docId,
      owner_user_id: ownerId,
      vehicle_id_nullable: null,
      title,
      source_type: SourceType.USER_UPLOADED,
      document_type: DocumentType.REPAIR_MANUAL,
      file_uri: fileUri,
      file_hash_sha256: hash,
      mime_type: mimeType,
      size_bytes: sizeBytes,
      language: 'es',
      make_nullable: vehicleApplicability?.make || null,
      model_nullable: vehicleApplicability?.model || null,
      year_from_nullable: vehicleApplicability?.yearFrom || null,
      year_to_nullable: vehicleApplicability?.yearTo || null,
      engine_nullable: null,
      transmission_nullable: null,
      market_region_nullable: null,
      source_url_nullable: null,
      license_note_nullable: 'Subido por el usuario para uso privado',
      is_offline_available: true,
      extraction_status: ExtractionStatus.READY,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    };

    const paragraphs = fileContent.split(/\n\n+/).filter(p => p.trim().length > 10);
    const chunks: KnowledgeChunk[] = paragraphs.map((text, index) => {
      const cleanText = text.trim();
      const words = cleanText.split(/\s+/).length;
      return {
        id: `chk_${docId}_${index}`,
        document_id: docId,
        vehicle_id_nullable: null,
        section_title_nullable: `Sección ${index + 1}`,
        page_start_nullable: index + 1,
        page_end_nullable: index + 1,
        chunk_index: index,
        text: cleanText,
        token_count: Math.round(words * 1.3),
        embedding_vector_nullable: null,
        content_hash: `hash_${docId}_chunk_${index}`,
        created_at: new Date().toISOString()
      };
    });

    this.documents.push(doc);
    this.chunks.push(...chunks);
    
    this.rebuildFtsIndex();
    this.saveToStorage();

    return { doc, chunks };
  }

  public getDocuments(): KnowledgeDocument[] {
    return this.documents;
  }

  public getChunks(): KnowledgeChunk[] {
    return this.chunks;
  }

  public getTorqueCards(): TorqueSpecCard[] {
    return this.torqueCards;
  }

  public getFluidCards(): FluidSpecCard[] {
    return this.fluidCards;
  }

  public getWiringCards(): WiringReferenceCard[] {
    return this.wiringCards;
  }

  public getProcedureCards(): DiagnosticProcedureCard[] {
    return this.procedureCards;
  }

  public deleteDocument(id: string): void {
    this.documents = this.documents.filter(d => d.id !== id);
    this.chunks = this.chunks.filter(c => c.document_id !== id);
    this.rebuildFtsIndex();
    this.saveToStorage();
  }
}
