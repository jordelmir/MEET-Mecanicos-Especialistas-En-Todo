import { createClient } from '@supabase/supabase-js';
import fs from 'fs';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

// Load environment variables
const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(__dirname, '.env.local') });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

if (!supabaseUrl || !supabaseKey) {
  console.error("Missing Supabase credentials in .env.local");
  process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

async function main() {
  console.log("Starting data ingestion...");
  
  const codesMap = new Map();

  // 1. Read dtc_database.json
  try {
    const jsonPath = path.resolve(__dirname, 'dtc_database.json');
    if (fs.existsSync(jsonPath)) {
      const rawJson = fs.readFileSync(jsonPath, 'utf-8');
      const jsonData = JSON.parse(rawJson);
      for (const item of jsonData) {
        codesMap.set(item.code, {
          code: item.code,
          description_es: item.descriptionEs || '',
          description_en: item.descriptionEn || '',
          system: item.system || 'ENGINE',
          severity: item.severity || 'MODERATE',
          possible_causes: item.possibleCauses || 'No especificado',
          urgency: item.urgency || 'CAUTION'
        });
      }
      console.log(`Parsed ${jsonData.length} codes from dtc_database.json`);
    } else {
      console.log("dtc_database.json not found, skipping.");
    }
  } catch (error) {
    console.error("Error reading JSON file:", error);
  }

  // 2. Read SQL migration file
  try {
    const sqlPath = path.resolve(__dirname, 'supabase', 'migrations', '20260427050909_seed_5000_codes.sql');
    if (fs.existsSync(sqlPath)) {
      const rawSql = fs.readFileSync(sqlPath, 'utf-8');
      const regex = /\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\)/g;
      let match;
      let count = 0;
      while ((match = regex.exec(rawSql)) !== null) {
        const code = match[1];
        if (!codesMap.has(code)) {
          codesMap.set(code, {
            code: match[1],
            description_es: match[2],
            description_en: match[3],
            system: match[4],
            severity: match[5],
            possible_causes: match[6],
            urgency: match[7]
          });
          count++;
        }
      }
      console.log(`Parsed ${count} new codes from SQL file`);
    } else {
      console.log("SQL file not found, skipping.");
    }
  } catch (error) {
    console.error("Error reading SQL file:", error);
  }

  const allCodes = Array.from(codesMap.values());
  console.log(`Total unique codes to upload: ${allCodes.length}`);

  // 3. Upload in batches of 100
  const BATCH_SIZE = 100;
  for (let i = 0; i < allCodes.length; i += BATCH_SIZE) {
    const chunk = allCodes.slice(i, i + BATCH_SIZE);
    console.log(`Uploading batch ${Math.floor(i / BATCH_SIZE) + 1} of ${Math.ceil(allCodes.length / BATCH_SIZE)}...`);
    
    const { data, error } = await supabase
      .from('dtc_codes')
      .upsert(chunk, { onConflict: 'code', ignoreDuplicates: true });
      
    if (error) {
      console.error(`Error uploading batch at index ${i}:`, error.message);
    }
    
    // Add a small delay to avoid hitting rate limits
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  
  console.log("Upload complete!");
}

main();
