package com.elysium369.meet.ui.screens.services

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ui.ObdViewModel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import com.elysium369.meet.core.agent.ui.*

@Serializable private data class ServiceDefinitionWire(val id:String,val domain:String,@SerialName("display_name") val name:String,@SerialName("supported_modalities") val modalities:List<String>)
@Serializable private data class ServiceRequestWire(val id:String,@SerialName("client_id") val client:String,@SerialName("assigned_provider_id") val provider:String?=null,@SerialName("service_definition_id") val definition:String,val modality:String,val title:String,val description:String,@SerialName("location_label") val location:String?=null,@SerialName("offered_price_minor") val price:Long,@SerialName("final_price_minor") val finalPrice:Long?=null,val currency:String,val state:String,val version:Long)
@Serializable private data class ServiceOfferWire(val id:String,@SerialName("request_id") val request:String,@SerialName("provider_id") val provider:String,@SerialName("price_minor") val price:Long,val currency:String,val state:String)
@Serializable private data class ProviderSummary(@SerialName("provider_id") val providerId:String?=null,val name:String?=null,val completed:Long=0,val reviews:Long=0,val rating:Double?=null,@SerialName("balance_minor") val balance:Long?=null,val eligible:Boolean=false)

/** Both historical entry points share this server-authoritative client/provider experience. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedServicesScreen(viewModel:ObdViewModel,onBack:()->Unit,onOpenMessages:()->Unit,onProviderConfig:()->Unit,onAdvanced:()->Unit,onServiceMessages:(String)->Unit) {
 val principal by viewModel.activePrincipal.collectAsState()
 val actor=principal?.id
 val scope=rememberCoroutineScope()
 var providerMode by remember(actor) { mutableStateOf(false) }
 var pages by remember(actor) { mutableStateOf(1) }
 var history by remember(actor) { mutableStateOf(false) }
 var definitions by remember(actor) { mutableStateOf(emptyList<ServiceDefinitionWire>()) }
 var requests by remember(actor) { mutableStateOf(emptyList<ServiceRequestWire>()) }
 var offers by remember(actor) { mutableStateOf(emptyList<ServiceOfferWire>()) }
 var query by remember(actor) { mutableStateOf("") }
 var providerSummaries by remember(actor) { mutableStateOf(emptyMap<String,ProviderSummary>()) }
 var summary by remember(actor) { mutableStateOf(ProviderSummary()) }
 var error by remember(actor) { mutableStateOf<String?>(null) }
 var busy by remember(actor) { mutableStateOf(false) }
 var selectedDefinition by remember(actor) { mutableStateOf<ServiceDefinitionWire?>(null) }
 var bidTarget by remember(actor) { mutableStateOf<ServiceRequestWire?>(null) }
 var title by remember(actor) { mutableStateOf("") }
 var description by remember(actor) { mutableStateOf("") }
 var location by remember(actor) { mutableStateOf("") }
 var price by remember(actor) { mutableStateOf("") }
 var modality by remember(actor) { mutableStateOf("") }
 var transferAmount by remember(actor) { mutableStateOf("") }
 var transferId by remember(actor) { mutableStateOf(UUID.randomUUID().toString()) }
 var draftId by remember(actor) { mutableStateOf(UUID.randomUUID().toString()) }
 val client=SupabaseModule.client
 suspend fun refresh() {
  val owner=actor ?: error("AUTH_REQUIRED")
  check(client.auth.currentUserOrNull()?.id==owner)
  val catalog=client.postgrest["service_definitions"].select { filter { eq("active",true) } }.decodeList<ServiceDefinitionWire>()
  val mine=mutableListOf<ServiceRequestWire>()
  val assigned=mutableListOf<ServiceRequestWire>()
  val market=mutableListOf<ServiceRequestWire>()
  for(page in 0 until pages) {
  mine+=client.postgrest["universal_service_requests"].select { filter { eq("client_id",owner) };order("updated_at",io.github.jan.supabase.postgrest.query.Order.DESCENDING);order("id",io.github.jan.supabase.postgrest.query.Order.DESCENDING);range((page*100).toLong(),(page*100+99).toLong()) }.decodeList<ServiceRequestWire>()
  assigned+=client.postgrest["universal_service_requests"].select { filter { eq("assigned_provider_id",owner) };order("updated_at",io.github.jan.supabase.postgrest.query.Order.DESCENDING);order("id",io.github.jan.supabase.postgrest.query.Order.DESCENDING);range((page*100).toLong(),(page*100+99).toLong()) }.decodeList<ServiceRequestWire>()
  market+=client.postgrest["universal_service_requests"].select { filter { eq("state","OPEN");neq("client_id",owner) };order("created_at",io.github.jan.supabase.postgrest.query.Order.DESCENDING);order("id",io.github.jan.supabase.postgrest.query.Order.DESCENDING);range((page*100).toLong(),(page*100+99).toLong()) }.decodeList<ServiceRequestWire>()
  }
  val rows=(mine+assigned+market).distinctBy { it.id }
  val proposals=mutableListOf<ServiceOfferWire>()
  // Request-scoped queries prevent unrelated proposals from hiding a user's offers.
  for(row in mine.filter { it.state=="OPEN" }) proposals+=client.postgrest["universal_service_offers"].select { filter { eq("request_id",row.id) } }.decodeList<ServiceOfferWire>()
  val stats=client.postgrest.rpc("universal_service_provider_summary_v1",buildJsonObject { put("p_provider_id",owner) }).decodeSingle<ProviderSummary>()
  val summaries=mutableListOf<ProviderSummary>()
  for(ids in proposals.map { it.provider }.distinct().chunked(100)) {
   summaries+=client.postgrest.rpc("universal_service_provider_summaries_v1",buildJsonObject {put("p_provider_ids",JsonArray(ids.map(::JsonPrimitive))) }).decodeList<ProviderSummary>()
  }
  check(client.auth.currentUserOrNull()?.id==owner)
  providerSummaries=summaries.mapNotNull { entry -> entry.providerId?.let { it to entry } }.toMap()
  definitions=catalog;requests=rows;offers=proposals;summary=stats;error=null
 }
 fun action(block:suspend ()->Unit) {
  if(busy) return
  busy=true
  scope.launch {
   try { check(client.auth.currentUserOrNull()?.id==actor);block();refresh() }
   catch(c:CancellationException) { throw c }
   catch(_:Exception) { error="No se pudo confirmar con el servidor. Revisa tu sesión, conexión y saldo; puedes reintentar." }
   finally { busy=false }
  }
 }
 fun transition(request:ServiceRequestWire,command:String,offer:String?=null)=action {
  client.postgrest.rpc("universal_service_transition_v1",buildJsonObject { put("p_request_id",request.id);put("p_action",command);if(offer!=null)put("p_offer_id",offer) })
 }
 LaunchedEffect(actor,pages) {
  while(true) { try { refresh() } catch(c:CancellationException) { throw c } catch(_:Exception) { error="Servicios en línea no disponibles. Inicia sesión y vuelve a intentar." };delay(10000) }
 }
 Scaffold(topBar={ TopAppBar(title={Text("Servicios Elysium")},navigationIcon={TextButton(onClick=onBack){Text("Volver")}},actions={TextButton(onClick=onOpenMessages){Text("Mensajes")}}) }) { padding ->
  LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     FilterChip(modifier=Modifier.weight(1f),selected=!providerMode,onClick={providerMode=false},label={Text("Necesito un servicio")})
     FilterChip(modifier=Modifier.weight(1f),selected=providerMode,onClick={providerMode=true},label={Text("Ofrezco servicios")})
    }
    if(providerMode) {
     Text(if(summary.eligible) "Proveedor verificado" else "Registra y verifica tu perfil para ofrecer servicios.")
     Text("${summary.completed} trabajos completados · ${summary.reviews} calificaciones")
     Text(summary.rating?.let { "Calificación: %.1f / 5".format(it) } ?: "Aún sin calificaciones")
     Text(summary.balance?.let { "Saldo: ₡$it CRC" } ?: "Saldo aún no habilitado")
     OutlinedTextField(transferAmount,{transferAmount=it},label={Text("Pasar saldo aprobado a servicios (CRC)")})
     Button(enabled=!busy && summary.eligible && (transferAmount.toLongOrNull() ?: 0)>0,onClick={action {
      client.postgrest.rpc("universal_service_wallet_transfer_v1",buildJsonObject {put("p_transfer_id",transferId);put("p_amount_minor",requireNotNull(transferAmount.toLongOrNull()))})
      transferId=UUID.randomUUID().toString();transferAmount=""
     }}){Text("Transferir mi saldo aprobado")}
     Text("Primero solicita la recarga SINPE y espera su aprobación. La transferencia mueve saldo; no lo duplica.")
     Text("Comisión constitucional: 5% del precio acordado, cobrada una vez al aceptar la oferta.")
     OutlinedButton(onClick=onProviderConfig){Text("Mi perfil y configuración")}
    } else Text("Compara propuestas. El proveedor inicia el trabajo y tú confirmas su finalización.")
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Row { TextButton(onClick={history=false}){Text("Activos")};TextButton(onClick={history=true}){Text("Historial")};TextButton(onClick={action { }},enabled=!busy){Text("Actualizar")} }
   }
   if(!providerMode && !history) item { OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().agentTextInput(AgentUiControlId("services.search"),"Buscar servicio u oficio",AgentTextFieldRole.SEARCH,readValue={query},writeValue={query=it}),label={Text("Buscar servicio u oficio")}) }
   if(!providerMode && !history) items(definitions.filter { query.isBlank() || (it.name+" "+it.domain).contains(query.trim(),ignoreCase=true) },key={"definition:${it.id}"}) { definition ->
    OutlinedButton(onClick={selectedDefinition=definition;modality=definition.modalities.firstOrNull().orEmpty();draftId=UUID.randomUUID().toString()},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("${definition.domain} · ${definition.name}")}
   }
   val visible=requests.filter { r -> (if(providerMode) r.provider==actor || r.state=="OPEN" && r.client!=actor else r.client==actor) && (if(history) r.state in setOf("COMPLETED","CANCELLED","DISPUTED") else r.state !in setOf("COMPLETED","CANCELLED","DISPUTED")) }
   if(visible.isEmpty()) item { Text(if(history) "Sin servicios en el historial." else "Sin solicitudes en esta vista.") }
   items(visible,key={it.id}) { r ->
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text(r.title,style=MaterialTheme.typography.titleMedium);Text(r.description);r.location?.let { Text(it) }
     Text("${r.finalPrice ?: r.price} ${r.currency} · ${r.state}")
     if(r.provider!=null && actor in listOf(r.client,r.provider)) TextButton(onClick={onServiceMessages(r.id)}){Text("Mensajes de este servicio")}
     if(providerMode && r.state=="OPEN" && summary.eligible) Button(onClick={bidTarget=r;price=""},enabled=!busy){Text("Enviar propuesta")}
     if(r.client==actor && r.state=="OPEN") {
      offers.filter { it.request==r.id && it.state=="PENDING" }.forEach { offer ->
       val publicProfile=providerSummaries[offer.provider]
       Text(publicProfile?.name ?: "Proveedor sin perfil público disponible")
       Text(publicProfile?.let { "${it.completed} trabajos · ${it.reviews} calificaciones" } ?: "Métricas no disponibles")
       Text(publicProfile?.rating?.let { "★ %.1f / 5".format(it) } ?: "Aún sin calificaciones")
       Text("Propuesta: ${offer.price} ${offer.currency}")
       Button(modifier=Modifier.serviceAction("accept.${offer.id}","Aceptar propuesta",!busy,AgentUiSensitivity.FINANCIAL){transition(r,"ACCEPT",offer.id)},onClick={transition(r,"ACCEPT",offer.id)},enabled=!busy){Text("Aceptar propuesta")}
      }
      TextButton(modifier=Modifier.serviceAction("cancel.${r.id}","Cancelar solicitud",!busy){transition(r,"CANCEL")},onClick={transition(r,"CANCEL")},enabled=!busy){Text("Cancelar solicitud")}
     }
     if(r.provider==actor && r.state=="ASSIGNED") Button(modifier=Modifier.serviceAction("start.${r.id}","Iniciar trabajo",!busy){transition(r,"START")},onClick={transition(r,"START")},enabled=!busy){Text("Iniciar trabajo")}
     if(r.client==actor && r.state=="IN_PROGRESS") Button(modifier=Modifier.serviceAction("complete.${r.id}","Confirmar trabajo terminado",!busy){transition(r,"COMPLETE")},onClick={transition(r,"COMPLETE")},enabled=!busy){Text("Confirmar trabajo terminado")}
     if(r.client==actor && r.state=="COMPLETED") {
      Text("Califica tu servicio (una vez)")
      Row { (1..5).forEach { stars -> TextButton(enabled=!busy,onClick={action { client.postgrest.rpc("universal_service_rate_v1",buildJsonObject { put("p_request_id",r.id);put("p_stars",stars) }) }}){Text("$stars ★")} } }
     }
    } }
   }
   item { TextButton(onClick={pages+=1},enabled=!busy){Text("Cargar más servicios")} }
   item { OutlinedButton(onClick=onAdvanced){Text("Herramientas avanzadas de cotización")};Text("Las herramientas anteriores conservan su historial local. Una operación local no confirma un servicio en línea.") }
  }
 }
 selectedDefinition?.let { d ->
  AlertDialog(onDismissRequest={if(!busy)selectedDefinition=null},title={Text(d.name)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(title,{title=it},modifier=Modifier.agentTextInput(AgentUiControlId("services.title"),"Título",readValue={title},writeValue={title=it},route="elysium_services"),label={Text("Título")})
   OutlinedTextField(description,{description=it},modifier=Modifier.agentTextInput(AgentUiControlId("services.description"),"Describe lo que necesitas",readValue={description},writeValue={description=it},route="elysium_services"),label={Text("Describe lo que necesitas")})
   OutlinedTextField(location,{location=it},modifier=Modifier.agentTextInput(AgentUiControlId("services.location"),"Lugar o instrucciones para servicio remoto",readValue={location},writeValue={location=it},route="elysium_services"),label={Text("Lugar o instrucciones para servicio remoto")})
   OutlinedTextField(price,{price=it},modifier=Modifier.agentTextInput(AgentUiControlId("services.price"),"Presupuesto",readValue={price},writeValue={price=it},route="elysium_services"),label={Text("Presupuesto en CRC (colones enteros)")})
   d.modalities.forEach { option -> FilterChip(selected=modality==option,onClick={modality=option},label={Text(option)}) }
  }},dismissButton={TextButton(onClick={selectedDefinition=null},enabled=!busy){Text("Volver")}},confirmButton={Button(enabled=!busy && title.trim().length in 3..160 && description.trim().length in 10..5000 && (price.toLongOrNull() ?: 0)>0 && location.isNotBlank(),onClick={action {
   try { client.postgrest["universal_service_requests"].insert(buildJsonObject { put("id",draftId);put("client_id",requireNotNull(actor));put("service_definition_id",d.id);put("modality",modality);put("title",title.trim());put("description",description.trim());put("location_label",location.trim());put("offered_price_minor",requireNotNull(price.toLongOrNull()));put("currency","CRC") }) } catch(c:CancellationException) { throw c } catch(e:Exception) {
    val existing=client.postgrest["universal_service_requests"].select { filter {eq("id",draftId);eq("client_id",requireNotNull(actor))} }.decodeList<ServiceRequestWire>().singleOrNull()
    if(existing==null || existing.definition!=d.id || existing.modality!=modality || existing.title!=title.trim() || existing.description!=description.trim() || existing.location!=location.trim() || existing.price!=price.toLongOrNull() || existing.currency!="CRC") throw e
   }
   selectedDefinition=null;title="";description="";location="";price=""
  }}){Text("Publicar solicitud")}})
 }
 bidTarget?.let { r -> AlertDialog(onDismissRequest={if(!busy)bidTarget=null},title={Text("Propuesta para ${r.title}")},text={OutlinedTextField(price,{price=it},modifier=Modifier.agentTextInput(AgentUiControlId("services.price"),"Presupuesto",readValue={price},writeValue={price=it},route="elysium_services"),label={Text("Precio en ${r.currency}")})},dismissButton={TextButton(onClick={bidTarget=null},enabled=!busy){Text("Volver")}},confirmButton={Button(enabled=!busy && (price.toLongOrNull() ?: 0)>0,onClick={action {
  try { client.postgrest["universal_service_offers"].insert(buildJsonObject {put("request_id",r.id);put("provider_id",requireNotNull(actor));put("price_minor",requireNotNull(price.toLongOrNull()));put("currency",r.currency) }) }
  catch(c:CancellationException) { throw c } catch(e:Exception) {
   val existing=client.postgrest["universal_service_offers"].select {filter {eq("request_id",r.id);eq("provider_id",requireNotNull(actor))}}.decodeList<ServiceOfferWire>().singleOrNull()
   if(existing==null || existing.price!=price.toLongOrNull() || existing.currency!=r.currency) throw e
  };bidTarget=null
 }}){Text("Enviar propuesta")}}) }
}

private fun Modifier.serviceAction(id:String,label:String,enabled:Boolean=true,sensitivity:AgentUiSensitivity=AgentUiSensitivity.NORMAL,action:()->Unit):Modifier =
 agentAction(AgentUiControlId("services.$id"),label,route="elysium_services",enabled=enabled,sensitivity=sensitivity,onActivate=action)
