package com.nextbench.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryContractTest {

    @Test
    fun loginPayloadMatchesCallableContract() {
        val payload = otpVerificationPayload(" ADA@Example.COM ", "12 34 56", null)

        assertEquals("ada@example.com", payload["email"])
        assertEquals("123456", payload["otp"])
        assertFalse(payload.containsKey("isSignup"))
    }

    @Test
    fun signupPayloadMatchesCallableContract() {
        val payload = otpVerificationPayload(
            email = "ada@example.com",
            otp = "123456",
            signupData = StudentSignupData(
                name = " Ada Lovelace ",
                school = " Loreto College ",
                city = " ",
                referralCode = " NB123 ",
            ),
        )

        assertTrue(payload["isSignup"] as Boolean)
        val signup = payload["signupData"] as Map<*, *>
        assertEquals("Ada Lovelace", signup["name"])
        assertEquals("Loreto College", signup["school"])
        assertEquals("Lucknow", signup["city"])
        assertEquals("NB123", signup["referralCode"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun otpMustContainExactlySixDigits() {
        otpVerificationPayload("ada@example.com", "12345", null)
    }
}
