-- Versioned Costa Rica baseline. Source review date: 2026-08-26.
-- Legal: Colegio de Abogados commission blocks + operational Poder Judicial matters.
-- Property: Registro Inmobiliario / market-operational categories.

with inserted as (
  insert into public.market_taxonomy_versions(vertical,version,jurisdiction,source_url,source_checked_at,published_at,content_hash)
  values('LEGAL',1,'CR','https://www.abogados.or.cr/comisiones/','2026-08-26T00:00:00Z',now(),
    encode(extensions.digest('CR-LEGAL-V1-2026-08-26','sha256'),'hex'))
  returning taxonomy_version_id
)
insert into public.market_service_categories(taxonomy_version_id,code,parent_code,display_name_es,sort_order)
select taxonomy_version_id,v.code,v.parent_code,v.name,v.sort_order from inserted cross join (values
  ('public_administrative',null,'Público, Administrativo y Gobernanza',10),
  ('constitutional','public_administrative','Constitucional',11),('administrative','public_administrative','Administrativo',12),
  ('public_procurement','public_administrative','Contratación Pública',13),('municipal','public_administrative','Municipal',14),
  ('tax','public_administrative','Tributario',15),('regulatory','public_administrative','Legislativo e Innovación Regulatoria',16),
  ('criminal_security',null,'Penal, Seguridad y Crimen Organizado',20),('criminal','criminal_security','Penal y Procesal Penal',21),
  ('organized_crime','criminal_security','Crimen Organizado y Seguridad',22),('police_law','criminal_security','Derecho Policial',23),
  ('traffic','criminal_security','Tránsito',24),('juvenile_criminal','criminal_security','Penal Juvenil',25),
  ('sentence_execution','criminal_security','Ejecución de la Pena',26),('contraventions','criminal_security','Contravenciones',27),
  ('private_commercial',null,'Privado y Comercial',30),('civil','private_commercial','Civil y Comercial',31),
  ('banking_securities','private_commercial','Bancario y Bursátil',32),('construction','private_commercial','Construcción',33),
  ('consumer','private_commercial','Consumidor',34),('compliance','private_commercial','Compliance',35),
  ('agrarian','private_commercial','Agrario',36),('judicial_collections','private_commercial','Cobro Judicial',37),
  ('insolvency','private_commercial','Insolvencia / Concursal',38),('corporate','private_commercial','Societario / Corporativo',39),
  ('ma','private_commercial','Fusiones y Adquisiciones',40),('real_estate_law','private_commercial','Derecho Inmobiliario',41),
  ('labor_economic',null,'Laboral y Económico',50),('labor_private','labor_economic','Laboral Privado',51),
  ('labor_public','labor_economic','Laboral Público',52),('collective_labor','labor_economic','Laboral Colectivo',53),
  ('cooperative_solidarity','labor_economic','Cooperativo y Solidarista',54),('independent_worker','labor_economic','Trabajador Independiente',55),
  ('social_security','labor_economic','Seguridad Social',56),('occupational_health','labor_economic','Salud Ocupacional',57),
  ('international_digital',null,'Internacional y Digital',60),('international','international_digital','Internacional',61),
  ('digital_technology','international_digital','Digital y Tecnología',62),('maritime','international_digital','Marítimo',63),
  ('environmental','international_digital','Ambiental',64),('privacy_data','international_digital','Protección de Datos y Privacidad',65),
  ('cybercrime','international_digital','Ciberdelincuencia',66),('intellectual_property','international_digital','Propiedad Intelectual',67),
  ('competition','international_digital','Competencia',68),('insurance','international_digital','Seguros',69),
  ('fintech','international_digital','Fintech',70),('telecommunications','international_digital','Telecomunicaciones',71),
  ('energy','international_digital','Energía y Servicios Públicos',72),
  ('social_human',null,'Social y Humano',80),('human_rights','social_human','Derechos Humanos',81),
  ('family','social_human','Familia',82),('gender','social_human','Género',83),('children','social_human','Niñez y Adolescencia',84),
  ('older_adults','social_human','Adulto Mayor',85),('indigenous_rights','social_human','Pueblos Indígenas',86),
  ('migration','social_human','Migratorio',87),('disability','social_human','Discapacidad',88),('health','social_human','Salud',89),
  ('alimony','social_human','Pensiones Alimentarias',90),('domestic_violence','social_human','Violencia Doméstica y Medidas de Protección',91),
  ('succession','social_human','Sucesiones y Herencias',92),
  ('notarial_registry',null,'Notarial y Registral',100),('notarial','notarial_registry','Derecho Notarial',101),
  ('registry','notarial_registry','Derecho Registral',102),('constitutional_remedy','public_administrative','Amparo y Hábeas Corpus',103),
  ('adr',null,'Resolución Alterna de Conflictos',110),('arbitration','adr','Arbitraje',111),
  ('mediation','adr','Mediación',112),('conciliation','adr','Conciliación',113),
  ('tourism','private_commercial','Turismo y Hospitalidad',120),('estate_planning','private_commercial','Planificación Patrimonial',121)
) as v(code,parent_code,name,sort_order);

with inserted as (
  insert into public.market_taxonomy_versions(vertical,version,jurisdiction,source_url,source_checked_at,published_at,content_hash)
  values('REAL_ESTATE',1,'CR','https://www.rnp.go.cr/registro_inmobiliario/','2026-08-26T00:00:00Z',now(),
    encode(extensions.digest('CR-PROPERTY-V1-2026-08-26','sha256'),'hex')) returning taxonomy_version_id
)
insert into public.market_service_categories(taxonomy_version_id,code,display_name_es,sort_order)
select taxonomy_version_id,v.code,v.name,v.sort_order from inserted cross join (values
  ('independent_house','Casa independiente',10),('condominium_house','Casa en condominio',11),
  ('apartment','Apartamento',12),('condominium','Condominio',13),('residential_lot','Lote residencial',20),
  ('commercial_lot','Lote comercial',21),('industrial_lot','Lote industrial',22),('mixed_lot','Lote mixto',23),
  ('building','Edificio',30),('office','Oficina',31),('retail_premises','Local comercial',32),
  ('warehouse','Bodega',33),('industrial_facility','Nave industrial',34),('agricultural_farm','Finca agrícola',40),
  ('livestock_farm','Finca ganadera',41),('recreational_farm','Finca de recreo',42),('quinta','Quinta',43),
  ('hotel_tourism','Hotel / alojamiento',50),('development','Proyecto inmobiliario',51),
  ('presale_development','Proyecto en preventa',52),('development_land','Terreno para desarrollo',53),
  ('investment_property','Propiedad de inversión',54),('parking','Parqueo',60),('storage','Bodega pequeña / storage',61),
  ('room','Habitación',62),('mixed_use','Propiedad de uso mixto',63)
) as v(code,name,sort_order);

insert into public.market_service_templates(category_id,code,display_name_es,allowed_fee_models,requires_notary)
select c.category_id,v.code,v.name,v.models,v.requires_notary
from public.market_service_categories c
join public.market_taxonomy_versions t using(taxonomy_version_id)
cross join (values
 ('consultation','Consulta jurídica',array['FIXED','HOURLY','QUOTE_AFTER_CONSULTATION']::text[],false),
 ('document_review','Revisión de documentos',array['FIXED','HOURLY']::text[],false),
 ('representation','Representación judicial',array['FIXED','HOURLY','PER_STAGE','TARIFF_BASED']::text[],false),
 ('retainer','Asesoría mensual',array['RETAINER']::text[],false)
) as v(code,name,models,requires_notary)
where t.vertical='LEGAL' and c.code in ('civil','labor_private','administrative','criminal');

insert into public.market_service_templates(category_id,code,display_name_es,allowed_fee_models,requires_notary)
select c.category_id,'notarial_act','Actuación notarial',array['TARIFF_BASED']::text[],true
from public.market_service_categories c join public.market_taxonomy_versions t using(taxonomy_version_id)
where t.vertical='LEGAL' and c.code='notarial';
