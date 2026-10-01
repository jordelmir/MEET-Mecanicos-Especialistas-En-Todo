package com.elysium369.meet.ui.screens.services

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ServicesOfferPayloadTest {
 @Test fun preservesCounterofferTermsWithoutSynthesizingAssignmentOrPayment() {
  val payload=servicesOfferPayload("request-real","provider-real",12500,"CRC",1.5,30," Materiales + instalación ")
  assertEquals(90,payload.getValue("eta_minutes").jsonPrimitive.int)
  assertEquals(30,payload.getValue("warranty_days").jsonPrimitive.int)
  assertEquals("Materiales + instalación",payload.getValue("scope").jsonObject.getValue("note").jsonPrimitive.content)
  assertFalse(payload.containsKey("state"));assertFalse(payload.containsKey("payment_state"));assertFalse(payload.containsKey("assigned_provider_id"))
 }
 @Test fun rejectsInvalidTermsBeforeSending() {
  listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,721.0).forEach { hours ->
   assertTrue(runCatching {servicesOfferPayload("r","p",1,"CRC",hours,0,"")}.isFailure)
  }
  assertTrue(runCatching {servicesOfferPayload("r","p",0,"CRC",1.0,0,"")}.isFailure)
  assertTrue(runCatching {servicesOfferPayload("r","p",1,"CRC",1.0,3651,"")}.isFailure)
 }
}
