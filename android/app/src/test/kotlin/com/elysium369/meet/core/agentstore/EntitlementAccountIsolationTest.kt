package com.elysium369.meet.core.agentstore
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class EntitlementAccountIsolationTest {
 @Test fun switchingAccountInvalidatesCachedPremium()=runBlocking {
  var actor="a"
  val gateway=object:AgentEntitlementGateway {
   override fun currentPrincipalId()=actor
   override suspend fun fetchAuthoritativeEntitlements()=Result.success(EntitlementSnapshot(actor,setOf("premium"),1,1))
  }
  val repo=AgentEntitlementRepository(gateway);repo.refresh();assertTrue(repo.hasEntitlement("premium"))
  actor="b";assertFalse(repo.hasEntitlement("premium"));assertTrue(repo.userEntitlements.value.isEmpty())
 }
 @Test fun responseFromPreviousAccountCannotRestorePremium()=runBlocking {
  var actor="a";val response=CompletableDeferred<Result<EntitlementSnapshot>>()
  val gateway=object:AgentEntitlementGateway {
   override fun currentPrincipalId()=actor
   override suspend fun fetchAuthoritativeEntitlements()=response.await()
  }
  val repo=AgentEntitlementRepository(gateway);val job=launch(start=CoroutineStart.UNDISPATCHED){repo.refresh()}
  actor="b";response.complete(Result.success(EntitlementSnapshot("a",setOf("premium"),1,1)));job.join()
  assertFalse(repo.hasEntitlement("premium"));assertTrue(repo.userEntitlements.value.isEmpty())
 }
}
