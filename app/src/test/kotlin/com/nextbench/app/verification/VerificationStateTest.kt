package com.nextbench.app.verification

import com.nextbench.data.model.UserData
import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationStateTest {

    @Test
    fun newStudentStartsCaptureUntilPhotosWereSubmitted() {
        assertEquals(
            AccountVerificationState.Capture,
            accountVerificationState(UserData(accountType = "student", verificationStatus = "pending")),
        )
        assertEquals(
            AccountVerificationState.Pending,
            accountVerificationState(
                UserData(
                    accountType = "student",
                    verificationStatus = "pending",
                    idCardUrl = "https://example.com/id.jpg",
                ),
            ),
        )
    }

    @Test
    fun manualRejectedAndApprovedStatesResumeCorrectly() {
        assertEquals(
            AccountVerificationState.ManualReview,
            accountVerificationState(
                UserData(
                    verificationStatus = "flagged_manual",
                    idCardUrl = "https://example.com/id.jpg",
                ),
            ),
        )
        assertEquals(
            AccountVerificationState.Rejected,
            accountVerificationState(UserData(verificationStatus = "rejected")),
        )
        assertEquals(
            AccountVerificationState.Approved,
            accountVerificationState(UserData(verified = true)),
        )
    }

    @Test
    fun organizationDocumentsAlwaysResumeAtAdminReview() {
        assertEquals(
            AccountVerificationState.OrganizationReview,
            accountVerificationState(UserData(accountType = "organization", verificationStatus = "pending")),
        )
    }
}
